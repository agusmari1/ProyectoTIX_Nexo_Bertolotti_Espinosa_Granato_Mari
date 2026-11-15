package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: los repositorios son mocks, no hace falta la base
class BookingConfirmationServiceTest {

    private static final Long BOOKING_ID = 30L;

    private final PaymentTransactionRepository paymentRepository = mock(PaymentTransactionRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BookingConfirmationService service =
            new BookingConfirmationService(paymentRepository, bookingRepository, eventPublisher);

    private Booking booking;
    private LocalDateTime heldUntil;

    @BeforeEach
    void setUp() {
        // Reserva pendiente, con el código que ya le puso BookingService y la retención vigente
        heldUntil = LocalDateTime.now().plusHours(1);
        Tourist tourist = new Tourist();
        tourist.setName("Ana");
        tourist.setEmail("ana@ejemplo.test");
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));

        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setParticipantCount(2);
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingCode("MN-AGUSTINA");
        booking.setHeldUntil(heldUntil);
        when(paymentRepository.findBookingForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    private void hasApprovedCharge(boolean approved) {
        when(paymentRepository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(approved);
    }

    @Test
    void withAnApprovedChargeTheBookingIsConfirmedWithTheExistingCode() {
        hasApprovedCharge(true);

        assertThat(service.confirmIfPaid(BOOKING_ID)).isTrue();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getBookingCode()).isEqualTo("MN-AGUSTINA");
        // Confirmada, los cupos quedan ocupados sin vencimiento
        assertThat(booking.getHeldUntil()).isNull();
        // Se avisa para mandar el QR por mail, con el código de la reserva
        ArgumentCaptor<BookingConfirmedEvent> event = ArgumentCaptor.forClass(BookingConfirmedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().bookingCode()).isEqualTo("MN-AGUSTINA");
        assertThat(event.getValue().touristEmail()).isEqualTo("ana@ejemplo.test");
        assertThat(event.getValue().activityName()).isEqualTo("Cabalgata");
    }

    @Test
    void ifTheBookingHasNoCodeANewUniqueOneIsGenerated() {
        hasApprovedCharge(true);
        booking.setBookingCode(null);
        // El primer código generado ya existe: tiene que generar otro
        when(bookingRepository.existsByBookingCode(anyString())).thenReturn(true, false);

        assertThat(service.confirmIfPaid(BOOKING_ID)).isTrue();

        assertThat(booking.getBookingCode()).matches("MN-[A-HJ-NP-Z2-9]{8}");
    }

    @Test
    void neverConfirmsWithoutAnApprovedCharge() {
        hasApprovedCharge(false);

        assertThat(service.confirmIfPaid(BOOKING_ID)).isFalse();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getHeldUntil()).isEqualTo(heldUntil);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void doesNotConfirmIfTheHoldAlreadyExpired() {
        hasApprovedCharge(true);
        booking.setHeldUntil(LocalDateTime.now().minusSeconds(1));

        assertThat(service.confirmIfPaid(BOOKING_ID)).isFalse();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void doesNotConfirmAnExpiredOrCancelledBooking() {
        hasApprovedCharge(true);
        for (BookingStatus status : List.of(BookingStatus.EXPIRED, BookingStatus.CANCELLED)) {
            booking.setStatus(status);

            assertThat(service.confirmIfPaid(BOOKING_ID)).isFalse();

            assertThat(booking.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void confirmingAgainAnAlreadyConfirmedBookingChangesNothing() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setHeldUntil(null);

        assertThat(service.confirmIfPaid(BOOKING_ID)).isTrue();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getBookingCode()).isEqualTo("MN-AGUSTINA");
        // Ya se mandó el mail la primera vez: no se manda otro
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void aBookingThatDoesNotExistThrows() {
        when(paymentRepository.findBookingForUpdate(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmIfPaid(99L)).isInstanceOf(PaymentException.class);
    }
}
