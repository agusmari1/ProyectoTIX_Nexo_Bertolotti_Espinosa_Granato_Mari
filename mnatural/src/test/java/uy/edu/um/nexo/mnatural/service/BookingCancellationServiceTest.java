package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;
import uy.edu.um.nexo.mnatural.payment.GatewayRefundResult;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio y el servicio de pagos son mocks; PaymentService es el real,
// así se prueba de punta a punta qué pasa con la reserva y con el reembolso
class BookingCancellationServiceTest {

    private static final Long TOURIST_ID = 7L;
    private static final Long BOOKING_ID = 30L;
    private static final Long SCHEDULE_ID = 5L;

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final BookingCancellationService service =
            new BookingCancellationService(repository, new PaymentService(repository, gateway));

    private Activity activity;
    private ActivitySchedule schedule;
    private Booking booking;

    @BeforeEach
    void setUp() {
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        activity = new Activity();
        activity.setCancellationAllowed(true);
        activity.setCancellationCutoffMinutes(2880);
        activity.setRefundPercentage(new BigDecimal("50"));

        schedule = new ActivitySchedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));

        booking = confirmedBooking(BOOKING_ID, "MN-30-abc");
        when(repository.findBookingForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    // Reserva confirmada del turista con un cobro de $ 3000 aprobado
    private Booking confirmedBooking(Long id, String chargeReference) {
        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        Booking confirmed = new Booking();
        confirmed.setId(id);
        confirmed.setTourist(tourist);
        confirmed.setActivitySchedule(schedule);
        confirmed.setTotalAmount(new BigDecimal("3000.00"));
        confirmed.setStatus(BookingStatus.CONFIRMED);

        PaymentTransaction charge = new PaymentTransaction();
        charge.setBooking(confirmed);
        charge.setType(PaymentType.CHARGE);
        charge.setStatus(PaymentStatus.APPROVED);
        charge.setAmount(new BigDecimal("3000.00"));
        charge.setExternalReference(chargeReference);
        when(repository.findFirstByBookingIdAndTypeAndStatus(id, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
        when(repository.existsByBookingIdAndTypeAndStatus(id, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(true);
        return confirmed;
    }

    private void gatewayApproves() {
        when(gateway.refund(anyString(), any(), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "REF-1", null));
    }

    // ===== El turista cancela =====

    @Test
    void withinTheDeadlineTheBookingIsCancelledAndThePercentageIsRefunded() {
        gatewayApproves();

        CancellationResult result = service.cancelByTourist(BOOKING_ID, TOURIST_ID);

        assertThat(result.cancelled()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.refund().getAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.message()).contains("1.500,00").contains("50 %");
        verify(gateway).refund(eq("MN-30-abc"), argThat(a -> a.compareTo(new BigDecimal("1500")) == 0), eq(false));
    }

    @Test
    void ifTheRefundFailsTheBookingStaysConfirmed() {
        when(gateway.refund(anyString(), any(), anyBoolean())).thenThrow(new PaymentGatewayException("timeout"));

        CancellationResult result = service.cancelByTourist(BOOKING_ID, TOURIST_ID);

        assertThat(result.cancelled()).isFalse();
        assertThat(result.refund().getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(result.message()).contains("sigue confirmada");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void ifTheActivityDoesNotAllowCancellationNothingChanges() {
        activity.setCancellationAllowed(false);

        assertThatThrownBy(() -> service.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("no admite cancelaciones");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void afterTheDeadlineNothingChanges() {
        schedule.setStartDateTime(LocalDateTime.now().plusHours(24));

        assertThatThrownBy(() -> service.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Ya pasó el plazo");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void withAZeroPercentPolicyTheBookingIsCancelledWithoutCallingTheGateway() {
        activity.setRefundPercentage(BigDecimal.ZERO);

        CancellationResult result = service.cancelByTourist(BOOKING_ID, TOURIST_ID);

        assertThat(result.cancelled()).isTrue();
        assertThat(result.refund()).isNull();
        assertThat(result.message()).contains("no corresponde reembolso");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void anAlreadyRefundedBookingCannotBeCancelledAgain() {
        when(repository.existsByBookingIdAndTypeAndStatusIn(eq(BOOKING_ID), eq(PaymentType.REFUND), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("ya fue reembolsada");
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void onlyAConfirmedBookingOfTheSameTouristCanBeCancelled() {
        assertThatThrownBy(() -> service.cancelByTourist(BOOKING_ID, 99L)).isInstanceOf(PaymentException.class);
        for (BookingStatus status : List.of(BookingStatus.PENDING, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
            booking.setStatus(status);
            assertThatThrownBy(() -> service.cancelByTourist(BOOKING_ID, TOURIST_ID)).isInstanceOf(PaymentException.class);
        }
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    // ===== El prestador suspende el horario =====

    @Test
    void suspendingCancelsEveryBookingAndRefundsOneHundredPercentOfThePaidOnes() {
        // Aunque la política no permita cancelar, la suspensión devuelve todo
        activity.setCancellationAllowed(false);
        Booking unpaid = new Booking();
        unpaid.setId(31L);
        unpaid.setStatus(BookingStatus.PENDING);
        unpaid.setHeldUntil(LocalDateTime.now().plusMinutes(5));
        when(repository.findBookingsOfScheduleForUpdate(eq(SCHEDULE_ID), any())).thenReturn(List.of(booking, unpaid));
        gatewayApproves();

        SuspensionResult result = service.cancelForSuspension(SCHEDULE_ID);

        assertThat(result.cancelledBookings()).isEqualTo(2);
        assertThat(result.refunded()).isEqualTo(1);
        assertThat(result.failedRefunds()).isZero();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // La pendiente se cancela sin cobro: no se reembolsa nada y libera sus cupos
        assertThat(unpaid.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(unpaid.getHeldUntil()).isNull();
        verify(gateway, times(1)).refund(eq("MN-30-abc"),
                argThat(a -> a.compareTo(new BigDecimal("3000")) == 0), eq(true));
    }

    @Test
    void ifARefundFailsWhileSuspendingTheBookingIsCancelledAnywayAndCountedForRetry() {
        when(repository.findBookingsOfScheduleForUpdate(eq(SCHEDULE_ID), any())).thenReturn(List.of(booking));
        when(gateway.refund(anyString(), any(), anyBoolean())).thenThrow(new PaymentGatewayException("timeout"));

        SuspensionResult result = service.cancelForSuspension(SCHEDULE_ID);

        assertThat(result.refunded()).isZero();
        assertThat(result.failedRefunds()).isEqualTo(1);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
