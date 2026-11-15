package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.dto.AdminRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.exception.AdminNotFoundException;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: los repositorios y el publicador de eventos son mocks, así que no hace falta
// la base de datos. El PasswordEncoder es el real (BCrypt) para comprobar el hash de verdad
class AdminAccountServiceTest {

    private UserRepository userRepository;
    private AdminRepository adminRepository;
    private ApplicationEventPublisher eventPublisher;
    private PasswordEncoder passwordEncoder;
    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminRepository = mock(AdminRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AdminAccountService(userRepository, adminRepository, passwordEncoder, eventPublisher);
        when(adminRepository.save(any(Admin.class))).then(returnsFirstArg());
    }

    private AdminRegistrationDTO dto() {
        AdminRegistrationDTO dto = new AdminRegistrationDTO();
        dto.setName("Lucía");
        dto.setLastName("Pérez");
        dto.setDateOfBirth(LocalDate.of(1990, 5, 10));
        dto.setEmail("  Lucia@Ejemplo.test ");
        return dto;
    }

    private List<Object> publishedEvents(int expectedCount) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(expectedCount)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void createsTheAdminWithAGeneratedPasswordAndSendsItByEmail() {
        service.create(dto());

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(saved.capture());
        Admin admin = saved.getValue();
        assertThat(admin.getName()).isEqualTo("Lucía");
        assertThat(admin.getLastName()).isEqualTo("Pérez");
        assertThat(admin.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 10));
        // El correo se guarda sin espacios y en minúsculas, porque así lo busca el login
        assertThat(admin.getEmail()).isEqualTo("lucia@ejemplo.test");

        // La contraseña no la eligió nadie: la generó el sistema y solo viaja en el evento del correo
        AdminCreatedEvent event = (AdminCreatedEvent) publishedEvents(1).getFirst();
        assertThat(event.email()).isEqualTo("lucia@ejemplo.test");
        assertThat(event.password()).hasSize(PasswordGenerator.LENGTH);
        // En la base queda un hash que coincide con esa contraseña, no la contraseña
        assertThat(admin.getPasswordHash()).isNotEqualTo(event.password());
        assertThat(passwordEncoder.matches(event.password(), admin.getPasswordHash())).isTrue();
    }

    @Test
    void eachNewAdminGetsADifferentPassword() {
        service.create(dto());
        service.create(dto());

        List<Object> events = publishedEvents(2);
        assertThat(((AdminCreatedEvent) events.get(0)).password())
                .isNotEqualTo(((AdminCreatedEvent) events.get(1)).password());
    }

    @Test
    void repeatedEmailIsRejectedEvenWhenAnotherKindOfUserHasIt() {
        // existsByEmail mira a todos los usuarios (turistas, prestadores y admins), no solo a los admins
        when(userRepository.existsByEmail("lucia@ejemplo.test")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto()))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("lucia@ejemplo.test");
        verify(adminRepository, never()).save(any(Admin.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void resetPasswordGeneratesANewOneAndSendsItByEmail() {
        Admin existing = new Admin();
        existing.setId(5L);
        existing.setName("Lucía");
        existing.setEmail("lucia@ejemplo.test");
        existing.setPasswordHash(passwordEncoder.encode("contraseña-vieja-1"));
        when(adminRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.resetPassword(5L);

        AdminPasswordResetEvent event = (AdminPasswordResetEvent) publishedEvents(1).getFirst();
        assertThat(event.email()).isEqualTo("lucia@ejemplo.test");
        assertThat(event.password()).hasSize(PasswordGenerator.LENGTH);
        // Con la nueva contraseña se puede entrar y la vieja dejó de servir
        assertThat(passwordEncoder.matches(event.password(), existing.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("contraseña-vieja-1", existing.getPasswordHash())).isFalse();
    }

    @Test
    void resetPasswordOfAnUnknownAdminFails() {
        when(adminRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(99L)).isInstanceOf(AdminNotFoundException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
