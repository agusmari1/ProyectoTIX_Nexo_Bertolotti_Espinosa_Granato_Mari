package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.dto.ProviderRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderServiceTest {

    private UserRepository userRepository;
    private ProviderRepository providerRepository;
    private BusinessRepository businessRepository;
    private ProviderService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        providerRepository = mock(ProviderRepository.class);
        businessRepository = mock(BusinessRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("clave-de-prueba")).thenReturn("hash");
        service = new ProviderService(userRepository, providerRepository, businessRepository, passwordEncoder);
    }

    @Test
    void registerLeavesTheProviderPendingWithItsBusiness() {
        ProviderRegistrationDTO dto = validRegistration();
        dto.setEmail("  Prestador@Ejemplo.TEST ");

        Provider provider = service.register(dto);

        assertThat(provider.getEmail()).isEqualTo("prestador@ejemplo.test");
        assertThat(provider.getPasswordHash()).isEqualTo("hash");
        assertThat(provider.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);

        ArgumentCaptor<Business> business = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(business.capture());
        assertThat(business.getValue().getName()).isEqualTo("Emprendimiento");
        assertThat(business.getValue().getProvider()).isSameAs(provider);
        assertThat(provider.getBusinesses()).containsExactly(business.getValue());
    }

    @Test
    void registerRejectsAnEmailAlreadyUsedByAnyKindOfUser() {
        when(userRepository.existsByEmail("prestador@ejemplo.test")).thenReturn(true);

        assertThatThrownBy(() -> service.register(validRegistration()))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void aCompanyMustHaveARut() {
        ProviderRegistrationDTO dto = validRegistration();
        dto.setLegalType(LegalType.COMPANY);
        dto.setRut(null);

        assertThatThrownBy(() -> service.register(dto)).isInstanceOf(RutRequiredException.class);
        verify(providerRepository, never()).save(any());
    }

    @Test
    void aCompanyWithRutCanRegister() {
        ProviderRegistrationDTO dto = validRegistration();
        dto.setLegalType(LegalType.COMPANY);
        dto.setRut(123456789012L);

        service.register(dto);

        ArgumentCaptor<Business> business = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(business.capture());
        assertThat(business.getValue().getRut()).isEqualTo(123456789012L);
    }

    @Test
    void aPersonDoesNotNeedARut() {
        ProviderRegistrationDTO dto = validRegistration();
        dto.setLegalType(LegalType.INDIVIDUAL);
        dto.setRut(null);

        service.register(dto);

        verify(providerRepository).save(any(Provider.class));
    }

    private ProviderRegistrationDTO validRegistration() {
        ProviderRegistrationDTO dto = new ProviderRegistrationDTO();
        dto.setName("Pablo");
        dto.setLastName("Prueba");
        dto.setDateOfBirth(LocalDate.of(1985, 3, 2));
        dto.setDocumentType(DocumentType.CI);
        dto.setDocumentNumber("12345678");
        dto.setCountry("Uruguay");
        dto.setEmail("prestador@ejemplo.test");
        dto.setPassword("clave-de-prueba");
        dto.setConfirmPassword("clave-de-prueba");
        dto.setBusinessName("Emprendimiento");
        dto.setLegalType(LegalType.INDIVIDUAL);
        dto.setAddress("Calle de Prueba 123");
        dto.setBusinessPhone("099000000");
        dto.setBusinessEmail("emprendimiento@ejemplo.test");
        return dto;
    }
}
