package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import uy.edu.um.nexo.mnatural.dto.BookingVerificationDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ProviderBookingRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Verificación de códigos de reserva: repositorio y resolver son mocks, no se toca la base de datos
class BookingVerificationServiceTest {

    private static final String EMAIL = "prestador@ejemplo.test";
    private static final Long PROVIDER_ID = 3L;
    private static final String CODE = "MN-ABCD2345";

    private final ProviderBookingRepository bookingRepository = mock(ProviderBookingRepository.class);
    private final ApprovedProviderResolver resolver = mock(ApprovedProviderResolver.class);

    private BookingVerificationService service;

    @BeforeEach
    void setUp() {
        service = new BookingVerificationService(bookingRepository, resolver);
        Provider provider = new Provider();
        provider.setId(PROVIDER_ID);
        when(resolver.require(EMAIL)).thenReturn(provider);
    }

    private void bookingExists(BookingStatus status) {
        Booking booking = ProviderReservationServiceTest.booking(status);
        when(bookingRepository.findByCodeOfProvider(PROVIDER_ID, CODE)).thenReturn(Optional.of(booking));
    }

    @Test
    void aConfirmedReservationOfTheProviderIsValidAndShowsItsData() {
        bookingExists(BookingStatus.CONFIRMED);

        BookingVerificationDTO result = service.verify(EMAIL, CODE);

        assertThat(result.valid()).isTrue();
        assertThat(result.code()).isEqualTo(CODE);
        assertThat(result.activityName()).isEqualTo("Trekking");
        assertThat(result.startDateTime()).isEqualTo(LocalDateTime.of(2027, 1, 15, 9, 30));
        assertThat(result.holderName()).isEqualTo("Ana Pérez");
        assertThat(result.participantCount()).isEqualTo(1);
        assertThat(result.statusLabel()).isEqualTo("Confirmada");
    }

    @Test
    void theCodeIsAcceptedWithSpacesAndLowercase() {
        bookingExists(BookingStatus.CONFIRMED);

        assertThat(service.verify(EMAIL, "  mn-abcd2345 ").valid()).isTrue();
    }

    @Test
    void aCodeOfAnotherProviderOrThatDoesNotExistGivesTheSameAnswer() {
        when(bookingRepository.findByCodeOfProvider(PROVIDER_ID, "MN-ZZZZ9999")).thenReturn(Optional.empty());

        BookingVerificationDTO result = service.verify(EMAIL, "MN-ZZZZ9999");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo(BookingVerificationService.NOT_VALID);
        assertThat(result.activityName()).isNull();
        assertThat(result.holderName()).isNull();
    }

    @Test
    void aCancelledOrPendingReservationIsNotValidAndDoesNotShowTheTourist() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CANCELLED, BookingStatus.PENDING, BookingStatus.EXPIRED}) {
            bookingExists(status);

            BookingVerificationDTO result = service.verify(EMAIL, CODE);

            assertThat(result.valid()).as(status.name()).isFalse();
            assertThat(result.holderName()).as(status.name()).isNull();
            assertThat(result.message()).as(status.name()).contains("no es válida");
            assertThat(result.statusLabel()).as(status.name()).isNotNull();
        }
    }

    @Test
    void aBadFormatIsRejectedWithoutQueryingTheDatabase() {
        BookingVerificationDTO result = service.verify(EMAIL, "hola");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("formato");
        verify(bookingRepository, never()).findByCodeOfProvider(PROVIDER_ID, "HOLA");
        verify(bookingRepository, never()).findByCodeOfProvider(org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void anEmptyCodeAsksForTheCode() {
        assertThat(service.verify(EMAIL, "   ").message()).contains("Ingresá");
        assertThat(service.verify(EMAIL, null).valid()).isFalse();
    }

    @Test
    void anUnapprovedProviderCannotVerify() {
        when(resolver.require("otro@ejemplo.test")).thenThrow(new AccessDeniedException("no aprobado"));

        assertThatThrownBy(() -> service.verify("otro@ejemplo.test", CODE)).isInstanceOf(AccessDeniedException.class);
    }
}
