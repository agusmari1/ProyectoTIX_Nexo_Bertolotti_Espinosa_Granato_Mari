package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
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
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.GatewayRefundResult;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.payment.PaymentStart;
import uy.edu.um.nexo.mnatural.payment.SimulatedPaymentGateway;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio es un mock (no hace falta la base) y el servicio de pagos es el simulado,
// con el resultado forzado en cada test
class PaymentServiceTest {

    private static final Long TOURIST_ID = 7L;
    private static final Long BOOKING_ID = 30L;
    private static final String BASE_URL = "http://localhost:8080";

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);

    private Booking booking;
    private LocalDateTime createdAt;

    @BeforeEach
    void setUp() {
        // Lo que se guarda se devuelve tal cual, como haría JPA
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of());

        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);

        Activity activity = new Activity();
        activity.setBookingCutoffMinutes(0);

        // Una actividad dentro de 10 días: el límite de reserva no influye
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));

        // Reserva recién creada, con la retención de 10 minutos de BookingService
        createdAt = LocalDateTime.now().minusMinutes(1);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(createdAt);
        booking.setHeldUntil(createdAt.plusMinutes(BookingService.HOLD_MINUTES));
        when(repository.findBookingForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    private PaymentService service(PaymentStatus simulatedResult) {
        return new PaymentService(repository, new SimulatedPaymentGateway(simulatedResult, BASE_URL));
    }

    // Un intento ya iniciado, guardado en la base con su referencia
    private PaymentTransaction pendingTransaction() {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setId(100L);
        transaction.setBooking(booking);
        transaction.setAmount(booking.getTotalAmount());
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setAttemptNumber(1);
        transaction.setExternalReference("SIM-123");
        when(repository.findById(100L)).thenReturn(Optional.of(transaction));
        return transaction;
    }

    // ===== startPayment =====

    @Test
    void startPaymentRegistersAPendingChargeAndReturnsThePaymentUrl() {
        PaymentStart start = service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID);

        PaymentTransaction transaction = start.transaction();
        assertThat(start.started()).isTrue();
        assertThat(start.paymentUrl()).startsWith(BASE_URL + "/payments/return?external_reference=SIM-");
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getType()).isEqualTo(PaymentType.CHARGE);
        assertThat(transaction.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(transaction.getAttemptNumber()).isEqualTo(1);
        assertThat(transaction.getExternalReference()).startsWith("SIM-");
        verify(repository).save(transaction);
        // Iniciar el pago no confirma la reserva
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void startPaymentExtendsTheHoldToTwoHoursFromTheBookingCreation() {
        service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID);

        assertThat(booking.getHeldUntil()).isEqualTo(createdAt.plusHours(PaymentService.PENDING_PAYMENT_HOURS));
    }

    @Test
    void theHoldNeverGoesPastTheActivityBookingDeadline() {
        // Empieza en 1 hora y cierra reservas 30 minutos antes: el límite llega antes que las 2 horas
        LocalDateTime start = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.SECONDS);
        booking.getActivitySchedule().setStartDateTime(start);
        booking.getActivitySchedule().getActivity().setBookingCutoffMinutes(30);

        service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID);

        assertThat(booking.getHeldUntil()).isEqualTo(start.minusMinutes(30));
    }

    @Test
    void aRetryIsANewTransactionWithTheNextAttemptNumber() {
        when(repository.countByBookingIdAndType(BOOKING_ID, PaymentType.CHARGE)).thenReturn(2L);

        PaymentStart start = service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID);

        assertThat(start.transaction().getAttemptNumber()).isEqualTo(3);
    }

    @Test
    void whenTheGatewayIsDownTheAttemptIsRecordedAsErrorAndTheBookingStaysPending() {
        PaymentStart start = service(PaymentStatus.ERROR).startPayment(BOOKING_ID, TOURIST_ID);

        assertThat(start.started()).isFalse();
        assertThat(start.paymentUrl()).isNull();
        assertThat(start.transaction().getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(start.transaction().getFailureReason()).isNotBlank();
        assertThat(start.transaction().getExternalReference()).isNull();
        verify(repository).save(start.transaction());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        // Igual intentó pagar: tiene las 2 horas para reintentar
        assertThat(booking.getHeldUntil()).isEqualTo(createdAt.plusHours(PaymentService.PENDING_PAYMENT_HOURS));
    }

    @Test
    void anotherTouristCannotPayTheBooking() {
        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, 99L))
                .isInstanceOf(PaymentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aBookingThatIsNotPendingCannotBePaid() {
        for (BookingStatus status : List.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
            booking.setStatus(status);
            assertThatThrownBy(() -> service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID))
                    .isInstanceOf(PaymentException.class);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void aBookingWhoseHoldAlreadyExpiredCannotBePaidAndIsNotRevived() {
        LocalDateTime expiredHold = LocalDateTime.now().minusSeconds(1);
        booking.setHeldUntil(expiredHold);

        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Venció");
        assertThat(booking.getHeldUntil()).isEqualTo(expiredHold);
        verify(repository, never()).save(any());
    }

    @Test
    void aPreviousAttemptThatWasPaidPreventsChargingAgain() {
        PaymentTransaction previous = pendingTransaction();
        when(repository.findByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of(previous));
        when(repository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(true);

        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).startPayment(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("ya tiene un pago aprobado");
        // El intento anterior se consultó y quedó aprobado
        assertThat(previous.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(repository, never()).save(any());
    }

    // ===== syncPayment =====

    @Test
    void syncApprovedSavesTheCardBrandAndLastFourDigitsButDoesNotConfirmTheBooking() {
        pendingTransaction();

        PaymentTransaction transaction = service(PaymentStatus.APPROVED).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(transaction.getCardBrand()).isEqualTo("visa");
        assertThat(transaction.getCardLastFour()).isEqualTo("4242");
        assertThat(transaction.getFailureReason()).isNull();
        // Confirmar es tarea de BookingConfirmationService
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void syncRejectedKeepsTheBookingPendingForTwoHours() {
        pendingTransaction();

        PaymentTransaction transaction = service(PaymentStatus.REJECTED).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(transaction.getFailureReason()).isNotBlank();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getHeldUntil()).isEqualTo(createdAt.plusHours(PaymentService.PENDING_PAYMENT_HOURS));
    }

    @Test
    void syncPendingKeepsTheTransactionPending() {
        pendingTransaction();

        PaymentTransaction transaction = service(PaymentStatus.PENDING).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void ifTheStatusQueryFailsTheTransactionStaysPendingToAskAgainLater() {
        pendingTransaction();
        PaymentGateway failing = mock(PaymentGateway.class);
        when(failing.getPaymentStatus(anyString())).thenThrow(new PaymentGatewayException("timeout"));

        PaymentTransaction transaction = new PaymentService(repository, failing).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getFailureReason()).isNotBlank();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void anApprovedPaymentForTheRightAmountInPesosIsApproved() {
        pendingTransaction();
        PaymentGateway gateway = gatewayReturning(new GatewayPaymentStatus(PaymentStatus.APPROVED, "master", "0604",
                null, new BigDecimal("3000.0"), "UYU"));

        PaymentTransaction transaction = new PaymentService(repository, gateway).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void anApprovedPaymentForAnotherAmountIsAnErrorAndIsNotTakenAsPaid() {
        pendingTransaction();
        PaymentGateway gateway = gatewayReturning(new GatewayPaymentStatus(PaymentStatus.APPROVED, "master", "0604",
                null, new BigDecimal("30.00"), "UYU"));

        PaymentTransaction transaction = new PaymentService(repository, gateway).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(transaction.getFailureReason()).contains("monto");
    }

    @Test
    void anApprovedPaymentInAnotherCurrencyIsAnError() {
        pendingTransaction();
        PaymentGateway gateway = gatewayReturning(new GatewayPaymentStatus(PaymentStatus.APPROVED, "master", "0604",
                null, new BigDecimal("3000.00"), "USD"));

        PaymentTransaction transaction = new PaymentService(repository, gateway).syncPayment(100L, TOURIST_ID);

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.ERROR);
    }

    private PaymentGateway gatewayReturning(GatewayPaymentStatus status) {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.getPaymentStatus(anyString())).thenReturn(status);
        return gateway;
    }

    @Test
    void aTransactionWithAFinalResultIsNotQueriedAgain() {
        PaymentTransaction transaction = pendingTransaction();
        transaction.setStatus(PaymentStatus.REJECTED);
        PaymentGateway gateway = mock(PaymentGateway.class);

        PaymentTransaction result = new PaymentService(repository, gateway).syncPayment(100L, TOURIST_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        verify(gateway, never()).getPaymentStatus(anyString());
    }

    @Test
    void anotherTouristCannotSyncThePayment() {
        PaymentTransaction transaction = pendingTransaction();

        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).syncPayment(100L, 99L))
                .isInstanceOf(PaymentException.class);
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void syncByReferenceFindsTheTransactionWithWhichTheTouristReturns() {
        PaymentTransaction transaction = pendingTransaction();
        when(repository.findByExternalReference("SIM-123")).thenReturn(Optional.of(transaction));

        PaymentTransaction result = service(PaymentStatus.APPROVED).syncPaymentByReference("SIM-123", TOURIST_ID);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void syncByAnUnknownReferenceThrows() {
        when(repository.findByExternalReference(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).syncPaymentByReference("SIM-otro", TOURIST_ID))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).syncPaymentByReference(null, TOURIST_ID))
                .isInstanceOf(PaymentException.class);
    }

    // ===== syncPendingPayments =====

    @Test
    void enteringTheBookingPageFindsOutAPaymentThatWasApprovedWithoutReturning() {
        PaymentTransaction pending = pendingTransaction();
        when(repository.findByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of(pending));

        service(PaymentStatus.APPROVED).syncPendingPayments(BOOKING_ID, TOURIST_ID);

        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void aBookingThatIsNoLongerPendingIsNotQueried() {
        booking.setStatus(BookingStatus.CONFIRMED);
        PaymentGateway gateway = mock(PaymentGateway.class);

        new PaymentService(repository, gateway).syncPendingPayments(BOOKING_ID, TOURIST_ID);

        verify(gateway, never()).getPaymentStatus(anyString());
    }

    @Test
    void anotherTouristCannotSyncTheBookingPayments() {
        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).syncPendingPayments(BOOKING_ID, 99L))
                .isInstanceOf(PaymentException.class);
    }

    // ===== getBookingPayment =====

    @Test
    void aPendingBookingCanBePaidAndDoesNotShowItsCodeYet() {
        booking.getActivitySchedule().getActivity().setName("Cabalgata");
        booking.setBookingCode("MN-AGUSTINA");
        when(repository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(repository.findByBookingIdAndTypeOrderByAttemptNumberAsc(BOOKING_ID, PaymentType.CHARGE))
                .thenReturn(List.of());

        BookingPaymentDTO payment = service(PaymentStatus.APPROVED).getBookingPayment(BOOKING_ID, TOURIST_ID);

        assertThat(payment.isPayable()).isTrue();
        assertThat(payment.getActivityName()).isEqualTo("Cabalgata");
        // El código solo vale una vez confirmada la reserva
        assertThat(payment.getBookingCode()).isNull();
    }

    @Test
    void aBookingWithAnApprovedPaymentCannotBePaidAgain() {
        PaymentTransaction approved = pendingTransaction();
        approved.setStatus(PaymentStatus.APPROVED);
        when(repository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(repository.findByBookingIdAndTypeOrderByAttemptNumberAsc(BOOKING_ID, PaymentType.CHARGE))
                .thenReturn(List.of(approved));

        BookingPaymentDTO payment = service(PaymentStatus.APPROVED).getBookingPayment(BOOKING_ID, TOURIST_ID);

        assertThat(payment.isPayable()).isFalse();
    }

    @Test
    void anotherTouristCannotSeeTheBookingPayment() {
        when(repository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service(PaymentStatus.APPROVED).getBookingPayment(BOOKING_ID, 99L))
                .isInstanceOf(PaymentException.class);
    }

    // ===== Reembolsos =====

    // Para los reembolsos el servicio de pagos es un mock: así se controla qué responde y se verifica qué se le pidió
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private PaymentService refundService() {
        return new PaymentService(repository, gateway);
    }

    // Reserva confirmada con su cobro de $ 3000 aprobado
    private PaymentTransaction approvedCharge() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setHeldUntil(null);
        PaymentTransaction charge = new PaymentTransaction();
        charge.setId(200L);
        charge.setBooking(booking);
        charge.setType(PaymentType.CHARGE);
        charge.setStatus(PaymentStatus.APPROVED);
        charge.setAmount(new BigDecimal("3000.00"));
        charge.setAttemptNumber(1);
        charge.setExternalReference("MN-30-abc");
        when(repository.findFirstByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(Optional.of(charge));
        return charge;
    }

    private void cancellationPolicy(boolean allowed, Integer cutoffMinutes, String refundPercentage) {
        Activity activity = booking.getActivitySchedule().getActivity();
        activity.setCancellationAllowed(allowed);
        activity.setCancellationCutoffMinutes(cutoffMinutes);
        activity.setRefundPercentage(refundPercentage == null ? null : new BigDecimal(refundPercentage));
    }

    private static BigDecimal amountEq(String expected) {
        return argThat(amount -> amount != null && amount.compareTo(new BigDecimal(expected)) == 0);
    }

    @Test
    void touristCancellingWithinTheDeadlineGetsAPartialRefundLinkedToTheCharge() {
        PaymentTransaction charge = approvedCharge();
        // La actividad es en 10 días y admite cancelar hasta 2880 minutos (48 horas) antes, con 50 %
        cancellationPolicy(true, 2880, "50");
        when(gateway.refund(anyString(), any(), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "REF-1", null));
        PaymentService service = refundService();

        BigDecimal percentage = service.touristRefundPercentage(booking, LocalDateTime.now());
        PaymentTransaction refund = service.refund(booking, percentage);

        assertThat(percentage).isEqualByComparingTo("50");
        // Parcial: se manda el monto al servicio de pagos
        verify(gateway).refund(eq("MN-30-abc"), amountEq("1500.00"), eq(false));
        assertThat(refund.getType()).isEqualTo(PaymentType.REFUND);
        assertThat(refund.getOriginalPayment()).isSameAs(charge);
        assertThat(refund.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(refund.getExternalReference()).isEqualTo("REF-1");
        assertThat(refund.getAttemptNumber()).isEqualTo(1);
        assertThat(PaymentService.isDone(refund)).isTrue();
        verify(repository).save(refund);
    }

    @Test
    void whenTheProviderSuspendsTheRefundIsOneHundredPercent() {
        approvedCharge();
        // Aunque la actividad no admita cancelaciones, la suspensión del prestador devuelve todo
        cancellationPolicy(false, null, null);
        when(gateway.refund(anyString(), any(), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "REF-2", null));

        PaymentTransaction refund = refundService().refund(booking, PaymentService.FULL_REFUND_PERCENTAGE);

        verify(gateway).refund(eq("MN-30-abc"), amountEq("3000.00"), eq(true));
        assertThat(refund.getAmount()).isEqualByComparingTo("3000.00");
        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void afterTheCancellationDeadlineThereIsNoRefundAndTheReasonIsExplained() {
        approvedCharge();
        cancellationPolicy(true, 2880, "50");
        // Empieza en 24 horas: ya pasó el límite de 2880 minutos antes
        booking.getActivitySchedule().setStartDateTime(LocalDateTime.now().plusHours(24));

        assertThatThrownBy(() -> refundService().touristRefundPercentage(booking, LocalDateTime.now()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("Ya pasó el plazo para cancelar")
                .hasMessageContaining("2880 minutos");
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void ifTheActivityDoesNotAllowCancellationThereIsNoRefundAndTheReasonIsExplained() {
        approvedCharge();
        cancellationPolicy(false, null, null);

        assertThatThrownBy(() -> refundService().touristRefundPercentage(booking, LocalDateTime.now()))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("no admite cancelaciones");
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void aBookingCannotBeRefundedTwice() {
        approvedCharge();
        when(repository.existsByBookingIdAndTypeAndStatusIn(eq(BOOKING_ID), eq(PaymentType.REFUND), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> refundService().refund(booking, PaymentService.FULL_REFUND_PERCENTAGE))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("ya fue reembolsada");
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void ifTheGatewayFailsTheRefundIsRecordedAsErrorAndNotAsDone() {
        approvedCharge();
        when(gateway.refund(anyString(), any(), anyBoolean())).thenThrow(new PaymentGatewayException("timeout"));

        PaymentTransaction refund = refundService().refund(booking, PaymentService.FULL_REFUND_PERCENTAGE);

        assertThat(refund.getStatus()).isEqualTo(PaymentStatus.ERROR);
        assertThat(refund.getExternalReference()).isNull();
        assertThat(refund.getFailureReason()).isNotBlank();
        assertThat(PaymentService.isDone(refund)).isFalse();
        // Queda registrado el intento, para reintentarlo
        verify(repository).save(refund);
    }

    @Test
    void aBookingWithoutAnApprovedChargeHasNothingToRefund() {
        booking.setStatus(BookingStatus.CONFIRMED);

        assertThatThrownBy(() -> refundService().refund(booking, PaymentService.FULL_REFUND_PERCENTAGE))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("no tiene un pago aprobado");
        verify(gateway, never()).refund(anyString(), any(), anyBoolean());
    }

    @Test
    void theRefundAmountIsRoundedToCents() {
        assertThat(PaymentService.refundAmount(new BigDecimal("1000.00"), new BigDecimal("33.33")))
                .isEqualByComparingTo("333.30");
        assertThat(PaymentService.refundAmount(new BigDecimal("999.99"), new BigDecimal("50")))
                .isEqualByComparingTo("500.00");
    }

    @Test
    void theSimulatedGatewayRefundsUnlessItIsDown() {
        approvedCharge();

        PaymentTransaction approved = service(PaymentStatus.APPROVED).refund(booking, new BigDecimal("50"));
        PaymentTransaction failed = service(PaymentStatus.ERROR).refund(booking, new BigDecimal("50"));

        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.getExternalReference()).startsWith("SIM-REF-");
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.ERROR);
    }

    @Test
    void aFailedRefundOfACancelledBookingIsRetriedForTheSameAmount() {
        approvedCharge();
        booking.setStatus(BookingStatus.CANCELLED);
        PaymentTransaction failed = new PaymentTransaction();
        failed.setType(PaymentType.REFUND);
        failed.setStatus(PaymentStatus.ERROR);
        failed.setAmount(new BigDecimal("3000.00"));
        failed.setAttemptNumber(1);
        when(repository.findBookingIdsWithFailedRefunds()).thenReturn(List.of(BOOKING_ID));
        when(repository.findFirstByBookingIdAndTypeOrderByAttemptNumberDesc(BOOKING_ID, PaymentType.REFUND))
                .thenReturn(Optional.of(failed));
        when(repository.countByBookingIdAndType(BOOKING_ID, PaymentType.REFUND)).thenReturn(1L);
        when(gateway.refund(anyString(), any(), anyBoolean()))
                .thenReturn(new GatewayRefundResult(PaymentStatus.APPROVED, "REF-3", null));

        int done = refundService().retryFailedRefunds();

        assertThat(done).isEqualTo(1);
        verify(gateway).refund(eq("MN-30-abc"), amountEq("3000.00"), eq(true));
    }

    @Test
    void theBookingScreenExplainsWhenTheBookingCannotBeCancelled() {
        PaymentTransaction charge = approvedCharge();
        cancellationPolicy(false, null, null);
        when(repository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(repository.findByBookingIdAndTypeOrderByAttemptNumberAsc(BOOKING_ID, PaymentType.CHARGE))
                .thenReturn(List.of(charge));

        BookingPaymentDTO payment = refundService().getBookingPayment(BOOKING_ID, TOURIST_ID);

        assertThat(payment.isCancellable()).isTrue();
        assertThat(payment.getCancellationBlockedReason()).contains("no admite cancelaciones");
        assertThat(payment.getRefundIfCancelled()).isNull();
    }

    @Test
    void theBookingScreenShowsHowMuchWouldBeRefunded() {
        PaymentTransaction charge = approvedCharge();
        cancellationPolicy(true, 2880, "50");
        when(repository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(repository.findByBookingIdAndTypeOrderByAttemptNumberAsc(BOOKING_ID, PaymentType.CHARGE))
                .thenReturn(List.of(charge));

        BookingPaymentDTO payment = refundService().getBookingPayment(BOOKING_ID, TOURIST_ID);

        assertThat(payment.getCancellationBlockedReason()).isNull();
        assertThat(payment.getRefundIfCancelled()).isEqualByComparingTo("1500.00");
    }
}
