package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.dto.TouristRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TouristServiceTest {

    private UserRepository userRepository;
    private TouristRepository touristRepository;
    private TouristService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        touristRepository = mock(TouristRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("clave-de-prueba")).thenReturn("hash");
        when(touristRepository.save(any(Tourist.class))).thenAnswer(call -> call.getArgument(0));
        service = new TouristService(userRepository, touristRepository, passwordEncoder);
    }

    @Test
    void registerNormalizesDataAndHashesThePassword() {
        TouristRegistrationDTO dto = validRegistration();
        dto.setEmail("  Ana@Ejemplo.TEST ");
        dto.setPhoneNumber("   ");

        Tourist tourist = service.register(dto);

        assertThat(tourist.getEmail()).isEqualTo("ana@ejemplo.test");
        assertThat(tourist.getPasswordHash()).isEqualTo("hash");
        assertThat(tourist.getPhoneNumber()).isNull();
    }

    @Test
    void registerRejectsAnEmailAlreadyUsedByAnyKindOfUser() {
        when(userRepository.existsByEmail("ana@ejemplo.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRegistration()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(touristRepository, never()).save(any());
    }

    @Test
    void registerSavesTheExceptionalConditionWhenThereIsOne() {
        TouristRegistrationDTO dto = validRegistration();
        dto.setExceptionalCondition("  Silla de ruedas ");
        assertThat(service.register(dto).getExceptionalCondition()).isEqualTo("Silla de ruedas");

        dto.setExceptionalCondition("   ");
        assertThat(service.register(dto).getExceptionalCondition()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "Uruguay, es", "ESPAÑA, es", "méxico, es", "Spain, es", "  República Dominicana , es",
            // El portugués no está soportado: esos países usan el inglés
            "Brasil, en", "Brazil, en", "PORTUGAL, en",
            "Francia, en", "United States, en", "Germany, en"})
    void registerAssignsTheLanguageAccordingToTheCountry(String country, String language) {
        TouristRegistrationDTO dto = validRegistration();
        dto.setCountry(country);

        assertThat(service.register(dto).getLanguage()).isEqualTo(language);
    }

    @Test
    void getByEmailReturnsOnlyTourists() {
        Tourist tourist = new Tourist();
        when(userRepository.findByEmail("turista@ejemplo.test")).thenReturn(Optional.of(tourist));
        when(userRepository.findByEmail("prestador@ejemplo.test")).thenReturn(Optional.of(new Provider()));

        assertThat(service.getByEmail("turista@ejemplo.test")).isSameAs(tourist);
        assertThatThrownBy(() -> service.getByEmail("prestador@ejemplo.test")).isInstanceOf(BookingException.class);
    }

    private TouristRegistrationDTO validRegistration() {
        TouristRegistrationDTO dto = new TouristRegistrationDTO();
        dto.setName("Ana");
        dto.setLastName("Prueba");
        dto.setDateOfBirth(LocalDate.of(1995, 5, 10));
        dto.setDocumentType(DocumentType.CI);
        dto.setDocumentNumber("12345678");
        dto.setCountry("Uruguay");
        dto.setEmail("ana@ejemplo.test");
        dto.setPassword("clave-de-prueba");
        dto.setConfirmPassword("clave-de-prueba");
        return dto;
    }
}
