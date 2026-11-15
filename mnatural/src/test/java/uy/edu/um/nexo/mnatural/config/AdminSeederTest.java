package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: los repositorios son mocks, así que no hace falta la base de datos
@ExtendWith(OutputCaptureExtension.class)
class AdminSeederTest {

    private UserRepository userRepository;
    private AdminRepository adminRepository;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adminRepository = mock(AdminRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
    }

    private AdminSeeder seeder(String configuredPassword) {
        return new AdminSeeder(userRepository, adminRepository, passwordEncoder, "admin@ejemplo.test", configuredPassword);
    }

    private Admin savedAdmin() {
        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void doesNothingWhenThereIsAlreadyAnAdmin() {
        when(adminRepository.count()).thenReturn(1L);

        seeder("clave-de-prueba-2").run();

        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    void createsTheFirstAdminWithThePasswordFromTheConfiguration(CapturedOutput output) {
        when(adminRepository.count()).thenReturn(0L);

        seeder("clave-de-prueba-2").run();

        Admin admin = savedAdmin();
        assertThat(admin.getEmail()).isEqualTo("admin@ejemplo.test");
        assertThat(passwordEncoder.matches("clave-de-prueba-2", admin.getPasswordHash())).isTrue();
        // Si la contraseña la eligió quien configuró la app, no se imprime en el log
        assertThat(output.getAll()).doesNotContain("clave-de-prueba-2");
    }

    @Test
    void generatesAPasswordAndShowsItOnceWhenNoneIsConfigured(CapturedOutput output) {
        when(adminRepository.count()).thenReturn(0L);

        seeder("").run();

        Admin admin = savedAdmin();
        Matcher matcher = Pattern.compile("contraseña generada: (\\S+)").matcher(output.getAll());
        assertThat(matcher.find()).isTrue();
        String generated = matcher.group(1);
        assertThat(generated).hasSize(16);
        assertThat(passwordEncoder.matches(generated, admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotCreateTheAdminWhenTheEmailBelongsToAnotherUser() {
        when(adminRepository.count()).thenReturn(0L);
        when(userRepository.existsByEmail("admin@ejemplo.test")).thenReturn(true);

        seeder("clave-de-prueba-2").run();

        verify(adminRepository, never()).save(any(Admin.class));
    }
}
