package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uy.edu.um.nexo.mnatural.dto.BusinessDTO;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.BusinessNotFoundException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    private static final String EMAIL = "prestador@example.com";

    @Mock
    private BusinessRepository businessRepository;
    @Mock
    private ApprovedProviderResolver providerResolver;

    private BusinessService service;
    private Provider provider;

    @BeforeEach
    void setUp() {
        service = new BusinessService(businessRepository, providerResolver);
        provider = new Provider();
        provider.setId(7L);
    }

    private BusinessDTO validDto() {
        BusinessDTO dto = new BusinessDTO();
        dto.setName("  Cabalgatas del Sur  ");
        dto.setLegalType(LegalType.INDIVIDUAL);
        dto.setAddress(" Ruta 9 km 10 ");
        dto.setPhoneNumber(" 099123456 ");
        dto.setEmail(" Contacto@Sur.COM ");
        dto.setServicesDescription("   ");
        return dto;
    }

    @Test
    void findOwnListsOnlyTheBusinessesOfTheProvider() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        Business business = new Business();
        when(businessRepository.findByProviderId(7L)).thenReturn(List.of(business));

        assertThat(service.findOwn(EMAIL)).containsExactly(business);
    }

    @Test
    void findOwnByIdFailsWhenBusinessBelongsToSomeoneElse() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        when(businessRepository.findByIdAndProviderId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwn(EMAIL, 99L)).isInstanceOf(BusinessNotFoundException.class);
    }

    @Test
    void createTrimsFieldsAndLinksTheBusinessToTheProvider() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(EMAIL, validDto());

        ArgumentCaptor<Business> captor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(captor.capture());
        Business saved = captor.getValue();
        assertThat(saved.getProvider()).isSameAs(provider);
        assertThat(saved.getName()).isEqualTo("Cabalgatas del Sur");
        assertThat(saved.getAddress()).isEqualTo("Ruta 9 km 10");
        assertThat(saved.getPhoneNumber()).isEqualTo("099123456");
        assertThat(saved.getEmail()).isEqualTo("contacto@sur.com");
        assertThat(saved.getServicesDescription()).isNull();
    }

    @Test
    void createRequiresRutForCompanies() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        BusinessDTO dto = validDto();
        dto.setLegalType(LegalType.COMPANY);
        dto.setRut(null);

        assertThatThrownBy(() -> service.create(EMAIL, dto)).isInstanceOf(RutRequiredException.class);
        verify(businessRepository, never()).save(any());
    }

    @Test
    void createAcceptsCompanyWithRut() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        when(businessRepository.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));
        BusinessDTO dto = validDto();
        dto.setLegalType(LegalType.COMPANY);
        dto.setRut(123456789012L);

        Business saved = service.create(EMAIL, dto);

        assertThat(saved.getRut()).isEqualTo(123456789012L);
    }

    @Test
    void updateChangesTheExistingBusiness() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        Business existing = new Business();
        existing.setName("Viejo");
        when(businessRepository.findByIdAndProviderId(3L, 7L)).thenReturn(Optional.of(existing));
        when(businessRepository.save(existing)).thenReturn(existing);
        BusinessDTO dto = validDto();
        dto.setServicesDescription("  Cabalgatas y paseos  ");

        Business updated = service.update(EMAIL, 3L, dto);

        assertThat(updated.getName()).isEqualTo("Cabalgatas del Sur");
        assertThat(updated.getServicesDescription()).isEqualTo("Cabalgatas y paseos");
    }

    @Test
    void updateFailsWhenBusinessIsNotOwned() {
        when(providerResolver.require(EMAIL)).thenReturn(provider);
        when(businessRepository.findByIdAndProviderId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EMAIL, 3L, validDto())).isInstanceOf(BusinessNotFoundException.class);
        verify(businessRepository, never()).save(any());
    }

    @Test
    void nothingIsDoneWhenProviderIsNotApproved() {
        when(providerResolver.require(EMAIL)).thenThrow(new AccessDeniedException("no aprobado"));

        assertThatThrownBy(() -> service.create(EMAIL, validDto())).isInstanceOf(AccessDeniedException.class);
        verify(businessRepository, never()).save(any());
    }
}
