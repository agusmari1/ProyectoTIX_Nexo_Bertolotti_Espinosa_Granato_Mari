package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.dto.ProfileDTO;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.Role;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private UserRepository userRepository;
    private BookingService bookingService;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        bookingService = mock(BookingService.class);
        service = new ProfileService(userRepository, bookingService);
    }

    @Test
    void missingUserHasNoProfile() {
        when(userRepository.findByEmail("nadie@ejemplo.test")).thenReturn(Optional.empty());

        assertThat(service.findProfile("nadie@ejemplo.test")).isEmpty();
    }

    @Test
    void touristSeesItsDataAndBookings() {
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        tourist.setName("Ana");
        tourist.setEmail("ana@ejemplo.test");
        tourist.setExceptionalCondition("Muletas");
        TouristBookingDTO booking = new TouristBookingDTO(1L, "Cabalgata", LocalDateTime.of(2030, 1, 1, 10, 0), 2,
                new BigDecimal("1000.00"), BookingStatus.CONFIRMED, null, "MN-ABC", false);
        when(userRepository.findByEmail("ana@ejemplo.test")).thenReturn(Optional.of(tourist));
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(booking));

        ProfileDTO profile = service.findProfile("ana@ejemplo.test").orElseThrow();

        assertThat(profile.role()).isEqualTo(Role.TOURIST);
        assertThat(profile.name()).isEqualTo("Ana");
        assertThat(profile.exceptionalCondition()).isEqualTo("Muletas");
        assertThat(profile.bookings()).containsExactly(booking);
        assertThat(profile.businesses()).isEmpty();
        assertThat(profile.approvalStatus()).isNull();
    }

    @Test
    void providerSeesItsBusinessAndApprovalStatus() {
        Business business = new Business();
        business.setName("Emprendimiento");
        business.setLegalType(LegalType.COMPANY);
        Provider provider = new Provider();
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        provider.getBusinesses().add(business);
        when(userRepository.findByEmail("prestador@ejemplo.test")).thenReturn(Optional.of(provider));

        ProfileDTO profile = service.findProfile("prestador@ejemplo.test").orElseThrow();

        assertThat(profile.role()).isEqualTo(Role.PROVIDER);
        assertThat(profile.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(profile.businesses()).extracting("name").containsExactly("Emprendimiento");
        assertThat(profile.bookings()).isEmpty();
        verifyNoInteractions(bookingService);
    }

    @Test
    void adminSeesOnlyItsData() {
        Admin admin = new Admin();
        admin.setName("Admin");
        when(userRepository.findByEmail("admin@ejemplo.test")).thenReturn(Optional.of(admin));

        ProfileDTO profile = service.findProfile("admin@ejemplo.test").orElseThrow();

        assertThat(profile.role()).isEqualTo(Role.ADMIN);
        assertThat(profile.businesses()).isEmpty();
        assertThat(profile.bookings()).isEmpty();
        assertThat(profile.approvalStatus()).isNull();
    }

    @Test
    void theProfileHasNoFieldForThePassword() {
        assertThat(Arrays.stream(ProfileDTO.class.getRecordComponents()).map(component -> component.getName().toLowerCase()))
                .noneMatch(name -> name.contains("password") || name.contains("hash"));
    }
}
