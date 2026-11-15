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
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.GatewayRefundResult;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Avisos (webhook) del servicio de pagos y reembolso de reservas vencidas. Repositorio y servicio de pagos
// son mocks: no se toca la base de datos ni internet
class PaymentNotificationTest {

    private static final Long BOOKING_ID = 30L;
    private static final String REFERENCE = "MN-30-abc";

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private PaymentService service;
    private Booking booking;
    private PaymentTransaction charge;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, gateway);
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Activity activity = new Activity();
        activity.setBookingCutoffMinutes(0);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));

        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(new Tourist());
        booking.setActivitySchedule(schedule);
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(createdAt);
        booking.setHeldUntil(createdAt.plusMinutes(10));
        when(repository.findBookingForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));

        charge = new PaymentTransaction();
        charge.setId(100L);
        charge.setBooking(booking);
        charge.setType(PaymentType.CHARGE);
        charge.setAmount(booking.getTotalAmount());
        charge.setStatus(PaymentStatus.PENDING);
        charge.setAttemptNumber(1);
        charge.setExternalReference(REFERENCE);
        when(repository.findByExternalReference(REFERENCE)).thenReturn(Optional.of(charge));
    }

    private static GatewayPaymentStatus gatewayStatus(PaymentStatus status) {
        return new GatewayPaymentStatus(status, "visa", "4242", null, new BigDecimal("3000.00"), "UYU");
    }

    // ===== syncByNotification =====

    @Test
    void anApprovedPaymentNotificationMarksTheChargeApprovedAndReturnsTheBooking() {
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(gatewayStatus(PaymentStatus.APPROVED));

        Optional<Long> result = service.syncByNotification(REFERENCE);

        assertThat(result).contains(BOOKING_ID);
        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(charge.getCardLastFour()).isEqualTo("4242");
        // Este servicio no confirma la reserva: eso lo hace BookingConfirmationService
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void theStatusComesFromQueryingThePaymentNotFromTheNotification() {
        // Aunque el aviso dijera "aprobado", si el servicio informa pendiente no se da por pagado
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(gatewayStatus(PaymentStatus.PENDING));

        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(gateway).getPaymentStatus(REFERENCE);
    }

    @Test
    void theSameNotificationTwiceIsHarmless() {
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(gatewayStatus(PaymentStatus.APPROVED));

        service.syncByNotification(REFERENCE);
        service.syncByNotification(REFERENCE);
        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        // Una vez aprobado es final: no se vuelve a consultar
        verify(gateway, times(1)).getPaymentStatus(REFERENCE);
    }

    @Test
    void aRejectedAttemptThatIsLaterApprovedIsPickedUp() {
        // Dentro de la misma página de pago el turista pudo reintentar con otra tarjeta: la referencia es la misma
        charge.setStatus(PaymentStatus.REJECTED);
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(gatewayStatus(PaymentStatus.APPROVED));

        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(charge.getFailureReason()).isNull();
    }

    @Test
    void aRejectedAttemptStaysRejectedIfTheServiceStillSaysSo() {
        charge.setStatus(PaymentStatus.REJECTED);
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(
                new GatewayPaymentStatus(PaymentStatus.REJECTED, null, null, "Tarjeta rechazada", null, null));

        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.REJECTED);
    }

    @Test
    void aNotificationForAnUnknownReferenceIsIgnored() {
        when(repository.findByExternalReference("MN-99-zzz")).thenReturn(Optional.empty());

        assertThat(service.syncByNotification("MN-99-zzz")).isEmpty();
        verify(gateway, never()).getPaymentStatus(anyString());
    }

    @Test
    void ifThePaymentServiceFailsTheAttemptStaysPendingAndNothingIsConfirmed() {
        when(gateway.getPaymentStatus(REFERENCE)).thenThrow(new PaymentGatewayException("timeout"));

        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void aPaymentApprovedForADifferentAmountIsNotAccepted() {
        when(gateway.getPaymentStatus(REFERENCE)).thenReturn(
                new GatewayPaymentStatus(PaymentStatus.APPROVED, null, null, null, new BigDecimal("1.00"), "UYU"));

        service.syncByNotification(REFERENCE);

        assertThat(charge.getStatus()).isEqualTo(PaymentStatus.ERROR);
    }

    // ===== refundIfPaidButNotConfirmed =====

    private void approvedCharge() {
        charge.setStatus(PaymentStatus.APPROVED);
        when(repository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(true);
        when(repository.findFirstByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
    }

    @Test
    void anExpiredBookingThatWasPaidIsRefundedInFull() {
        approvedCharge();
        booking.setStatus(BookingStatus.EXPIRED);
        when(gateway.refund(eq(REFERENCE), any(BigDecimal.class), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "ref-1", null));

        assertThat(service.refundIfPaidButNotConfirmed(BOOKING_ID)).isTrue();

        verify(gateway).refund(REFERENCE, new BigDecimal("3000.00"), true);
        var saved = forClass(PaymentTransaction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(PaymentType.REFUND);
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(saved.getValue().getOriginalPayment()).isSameAs(charge);
    }

    @Test
    void ifTheRefundFailsItIsSavedAsErrorSoItCanBeRetried() {
        approvedCharge();
        booking.setStatus(BookingStatus.EXPIRED);
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean()))
                .thenThrow(new PaymentGatewayException("sin respuesta"));

        assertThat(service.refundIfPaidButNotConfirmed(BOOKING_ID)).isTrue();

        var saved = forClass(PaymentTransaction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.ERROR);
    }

    @Test
    void aBookingThatAlreadyHasARefundIsNotRefundedTwice() {
        approvedCharge();
        booking.setStatus(BookingStatus.EXPIRED);
        when(repository.countByBookingIdAndType(BOOKING_ID, PaymentType.REFUND)).thenReturn(1L);

        assertThat(service.refundIfPaidButNotConfirmed(BOOKING_ID)).isFalse();
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void onlyExpiredBookingsAreRefundedAutomatically() {
        approvedCharge();
        // Confirmada: se queda con su pago. Cancelada: pudo ser una cancelación del turista con política sin
        // reembolso, donde no se devuelve nada. Pendiente: todavía puede confirmarse
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                BookingStatus.PENDING}) {
            booking.setStatus(status);

            assertThat(service.refundIfPaidButNotConfirmed(BOOKING_ID)).as(status.name()).isFalse();
        }
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void anExpiredBookingWithoutAnApprovedPaymentIsNotRefunded() {
        booking.setStatus(BookingStatus.EXPIRED);
        when(repository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(false);

        assertThat(service.refundIfPaidButNotConfirmed(BOOKING_ID)).isFalse();
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void theSafetyNetRefundsEveryExpiredPaidBookingFound() {
        approvedCharge();
        booking.setStatus(BookingStatus.EXPIRED);
        when(repository.findExpiredPaidBookingIdsWithoutRefund()).thenReturn(List.of(BOOKING_ID));
        when(gateway.refund(anyString(), any(BigDecimal.class), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "ref-1", null));

        assertThat(service.refundPaidExpiredBookings()).isEqualTo(1);
    }
}
