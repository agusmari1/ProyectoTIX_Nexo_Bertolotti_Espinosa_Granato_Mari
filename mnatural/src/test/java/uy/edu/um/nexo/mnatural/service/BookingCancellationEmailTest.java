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
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Avisos por correo de una cancelación. Repositorio y servicio de pagos son mocks: no se toca la base de datos
class BookingCancellationEmailTest {

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    private BookingCancellationService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new BookingCancellationService(repository, paymentService, publisher);
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        tourist.setName("Ana");
        tourist.setEmail("ana@ejemplo.test");
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(1L);
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));
        booking = new Booking();
        booking.setId(30L);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setBookingCode("MN-ABCD2345");
        booking.setParticipantCount(2);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(repository.findBookingForUpdate(30L)).thenReturn(Optional.of(booking));
    }

    private EmailEvents.BookingCancelled published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(EmailEvents.BookingCancelled.class);
        return (EmailEvents.BookingCancelled) captor.getValue();
    }

    private static PaymentTransaction refund(PaymentStatus status, String amount) {
        PaymentTransaction refund = new PaymentTransaction();
        refund.setStatus(status);
        refund.setAmount(new BigDecimal(amount));
        return refund;
    }

    // ===== Cancela el turista =====

    @Test
    void whenTheTouristCancelsWithARefundTheEventCarriesTheAmount() {
        when(paymentService.touristRefundPercentage(any(), any())).thenReturn(new BigDecimal("50"));
        when(paymentService.refund(any(), any())).thenReturn(refund(PaymentStatus.APPROVED, "750.00"));

        service.cancelByTourist(30L, 7L);

        EmailEvents.BookingCancelled event = published();
        assertThat(event.cause()).isEqualTo(EmailEvents.BookingCancelled.Cause.BY_TOURIST);
        assertThat(event.refundState()).isEqualTo(EmailEvents.BookingCancelled.RefundState.COMPLETED);
        assertThat(event.refundAmount()).isEqualByComparingTo("750.00");
        assertThat(event.touristEmail()).isEqualTo("ana@ejemplo.test");
        assertThat(event.bookingCode()).isEqualTo("MN-ABCD2345");
        assertThat(event.participantCount()).isEqualTo(2);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void whenThePolicyGivesNoRefundTheEventSaysNone() {
        when(paymentService.touristRefundPercentage(any(), any())).thenReturn(BigDecimal.ZERO);

        service.cancelByTourist(30L, 7L);

        EmailEvents.BookingCancelled event = published();
        assertThat(event.refundState()).isEqualTo(EmailEvents.BookingCancelled.RefundState.NONE);
        assertThat(event.refundAmount()).isNull();
        verify(paymentService, never()).refund(any(), any());
    }

    @Test
    void ifTheRefundFailsTheBookingIsNotCancelledAndNoEmailIsSent() {
        when(paymentService.touristRefundPercentage(any(), any())).thenReturn(new BigDecimal("100"));
        when(paymentService.refund(any(), any())).thenReturn(refund(PaymentStatus.ERROR, "1500.00"));

        CancellationResult result = service.cancelByTourist(30L, 7L);

        assertThat(result.cancelled()).isFalse();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aCancellationThatIsNotAllowedSendsNoEmail() {
        booking.setStatus(BookingStatus.CANCELLED);

        assertThatThrownBy(() -> service.cancelByTourist(30L, 7L)).isInstanceOf(PaymentException.class);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    // ===== Suspende el prestador =====

    private void scheduleWith(Booking... bookings) {
        when(repository.findBookingsOfScheduleForUpdate(anyLong(), any())).thenReturn(List.of(bookings));
    }

    @Test
    void aPaidBookingCancelledBySuspensionIsRefundedInFullAndAnnounced() {
        scheduleWith(booking);
        when(paymentService.hasApprovedCharge(30L)).thenReturn(true);
        when(paymentService.refund(any(), any())).thenReturn(refund(PaymentStatus.APPROVED, "3000.00"));

        service.cancelForSuspension(1L);

        EmailEvents.BookingCancelled event = published();
        assertThat(event.cause()).isEqualTo(EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED);
        assertThat(event.refundState()).isEqualTo(EmailEvents.BookingCancelled.RefundState.COMPLETED);
        assertThat(event.refundAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void aPendingUnpaidBookingCancelledBySuspensionIsAnnouncedWithNoRefund() {
        booking.setStatus(BookingStatus.PENDING);
        scheduleWith(booking);
        when(paymentService.hasApprovedCharge(30L)).thenReturn(false);

        service.cancelForSuspension(1L);

        EmailEvents.BookingCancelled event = published();
        assertThat(event.refundState()).isEqualTo(EmailEvents.BookingCancelled.RefundState.NONE);
        assertThat(event.refundAmount()).isNull();
        verify(paymentService, never()).refund(any(), any());
    }

    @Test
    void ifTheRefundFailsTheBookingIsStillCancelledAndTheEmailSaysItIsInProgress() {
        scheduleWith(booking);
        when(paymentService.hasApprovedCharge(30L)).thenReturn(true);
        when(paymentService.refund(any(), any())).thenReturn(refund(PaymentStatus.ERROR, "3000.00"));

        service.cancelForSuspension(1L);

        EmailEvents.BookingCancelled event = published();
        assertThat(event.refundState()).isEqualTo(EmailEvents.BookingCancelled.RefundState.IN_PROGRESS);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void everyAffectedTouristGetsTheirOwnEvent() {
        Booking other = new Booking();
        other.setId(31L);
        Tourist tourist = new Tourist();
        tourist.setName("Luis");
        tourist.setEmail("luis@ejemplo.test");
        other.setTourist(tourist);
        other.setActivitySchedule(booking.getActivitySchedule());
        other.setStatus(BookingStatus.PENDING);
        scheduleWith(booking, other);
        when(paymentService.hasApprovedCharge(30L)).thenReturn(true);
        when(paymentService.refund(any(), any())).thenReturn(refund(PaymentStatus.APPROVED, "3000.00"));

        service.cancelForSuspension(1L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(e -> ((EmailEvents.BookingCancelled) e).touristEmail())
                .containsExactly("ana@ejemplo.test", "luis@ejemplo.test");
    }

    // ===== Un error al armar el aviso no deshace la cancelación =====

    @Test
    void aBookingWithoutTouristDataStillGetsCancelled() {
        booking.setTourist(null);
        scheduleWith(booking);
        when(paymentService.hasApprovedCharge(30L)).thenReturn(false);

        assertThatCode(() -> service.cancelForSuspension(1L)).doesNotThrowAnyException();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(publisher, never()).publishEvent(any(Object.class));
    }
}
