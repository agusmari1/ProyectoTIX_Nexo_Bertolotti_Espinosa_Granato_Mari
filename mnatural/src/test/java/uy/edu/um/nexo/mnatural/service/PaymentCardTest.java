package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import uy.edu.um.nexo.mnatural.payment.CardPaymentRequest;
import uy.edu.um.nexo.mnatural.payment.CheckoutMode;
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Cobro con el formulario de tarjeta. Repositorio y servicio de pagos son mocks: no se toca la base ni internet
class PaymentCardTest {

    private static final Long BOOKING_ID = 30L;
    private static final Long TOURIST_ID = 7L;

    private final PaymentTransactionRepository repository = mock(PaymentTransactionRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private PaymentService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, gateway);
        when(gateway.checkoutMode()).thenReturn(CheckoutMode.CARD_FORM);
        when(repository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of());

        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        tourist.setEmail("ana@ejemplo.com");

        Activity activity = new Activity();
        activity.setBookingCutoffMinutes(0);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));

        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(1);
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setCreatedAt(createdAt);
        booking.setHeldUntil(createdAt.plusMinutes(10));
        when(repository.findBookingForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
    }

    private static CardPaymentRequest request() {
        return new CardPaymentRequest("tok", "master", "24", 1, null, "CI", "12345678", null, null, null, null);
    }

    private static GatewayPaymentStatus result(PaymentStatus status, String reason) {
        return new GatewayPaymentStatus(status, "master", "0604", reason, new BigDecimal("3000.00"), "UYU");
    }

    @Test
    void anApprovedCardPaymentIsStoredWithTheBrandAndTheLastFourDigits() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        PaymentTransaction transaction = service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(transaction.getCardBrand()).isEqualTo("master");
        assertThat(transaction.getCardLastFour()).isEqualTo("0604");
        assertThat(transaction.getAttemptNumber()).isEqualTo(1);
        assertThat(transaction.getType()).isEqualTo(PaymentType.CHARGE);
        assertThat(transaction.getAmount()).isEqualByComparingTo("3000");
        // La reserva la confirma BookingConfirmationService, no este servicio
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void theAttemptIsSavedBeforeChargingSoAnEarlyNotificationFindsIt() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        InOrder order = inOrder(repository, gateway);
        order.verify(repository).save(any(PaymentTransaction.class));
        order.verify(gateway).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void theReferenceIsOursAndIsTheSameOneSentToTheService() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        PaymentTransaction transaction = service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        assertThat(transaction.getExternalReference()).startsWith("MN-30-");
        verify(gateway).payWithCard(any(), eq(transaction.getExternalReference()), eq(new BigDecimal("3000.00")), any());
    }

    @Test
    void ifTheFormDidNotAskForAnEmailTheTouristsIsUsed() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        ArgumentCaptor<CardPaymentRequest> sent = ArgumentCaptor.forClass(CardPaymentRequest.class);
        verify(gateway).payWithCard(any(), anyString(), any(), sent.capture());
        assertThat(sent.getValue().payerEmail()).isEqualTo("ana@ejemplo.com");
        assertThat(sent.getValue().token()).isEqualTo("tok");
    }

    @Test
    void anEmailFromTheFormIsKept() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        service.payWithCard(BOOKING_ID, TOURIST_ID,
                new CardPaymentRequest("tok", "master", null, 1, "otro@ejemplo.com", null, null, null, null, null, null));

        ArgumentCaptor<CardPaymentRequest> sent = ArgumentCaptor.forClass(CardPaymentRequest.class);
        verify(gateway).payWithCard(any(), anyString(), any(), sent.capture());
        assertThat(sent.getValue().payerEmail()).isEqualTo("otro@ejemplo.com");
    }

    @Test
    void aRejectedPaymentKeepsTheReasonAndLeavesTheBookingPendingForARetry() {
        when(gateway.payWithCard(any(), anyString(), any(), any()))
                .thenReturn(result(PaymentStatus.REJECTED, "La tarjeta no tiene fondos suficientes."));

        PaymentTransaction transaction = service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(transaction.getFailureReason()).contains("fondos");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void anInProcessPaymentStaysPending() {
        when(gateway.payWithCard(any(), anyString(), any(), any()))
                .thenReturn(result(PaymentStatus.PENDING, "El pago está en proceso."));

        assertThat(service.payWithCard(BOOKING_ID, TOURIST_ID, request()).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ifTheServiceDoesNotAnswerWeDoNotKnowIfItChargedSoTheAttemptStaysPendingWithItsReference() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenThrow(new PaymentGatewayException("timeout"));

        PaymentTransaction transaction = service.payWithCard(BOOKING_ID, TOURIST_ID, request());

        // Ni aprobado ni fallado: con la referencia se vuelve a consultar después
        assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transaction.getExternalReference()).startsWith("MN-30-");
        assertThat(transaction.getFailureReason()).contains("No pudimos confirmar el pago");
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void anAmountThatDoesNotMatchIsNotAccepted() {
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(
                new GatewayPaymentStatus(PaymentStatus.APPROVED, "master", "0604", null, new BigDecimal("1.00"), "UYU"));

        assertThat(service.payWithCard(BOOKING_ID, TOURIST_ID, request()).getStatus()).isEqualTo(PaymentStatus.ERROR);
    }

    @Test
    void theSecondAttemptIsNumberedTwo() {
        when(repository.countByBookingIdAndType(BOOKING_ID, PaymentType.CHARGE)).thenReturn(1L);
        when(gateway.payWithCard(any(), anyString(), any(), any())).thenReturn(result(PaymentStatus.APPROVED, null));

        assertThat(service.payWithCard(BOOKING_ID, TOURIST_ID, request()).getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void aServiceThatUsesTheServicePageCannotChargeWithTheCardForm() {
        when(gateway.checkoutMode()).thenReturn(CheckoutMode.REDIRECT);

        assertThatThrownBy(() -> service.payWithCard(BOOKING_ID, TOURIST_ID, request()))
                .isInstanceOf(PaymentException.class);
        verify(gateway, never()).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void anotherTouristCannotPayTheBooking() {
        assertThatThrownBy(() -> service.payWithCard(BOOKING_ID, 99L, request())).isInstanceOf(PaymentException.class);
        verify(gateway, never()).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void aBookingThatIsNotPendingCannotBePaid() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                BookingStatus.EXPIRED}) {
            booking.setStatus(status);

            assertThatThrownBy(() -> service.payWithCard(BOOKING_ID, TOURIST_ID, request()))
                    .as(status.name()).isInstanceOf(PaymentException.class);
        }
        verify(gateway, never()).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void aBookingThatAlreadyHasAnApprovedPaymentIsNotChargedTwice() {
        when(repository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.payWithCard(BOOKING_ID, TOURIST_ID, request()))
                .isInstanceOf(PaymentException.class).hasMessageContaining("pago aprobado");
        verify(gateway, never()).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void thePreviousPendingAttemptsAreCheckedBeforeChargingAgain() {
        PaymentTransaction previous = new PaymentTransaction();
        previous.setBooking(booking);
        previous.setAmount(booking.getTotalAmount());
        previous.setStatus(PaymentStatus.PENDING);
        previous.setExternalReference("MN-30-previo");
        when(repository.findByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.PENDING))
                .thenReturn(List.of(previous));
        when(gateway.getPaymentStatus("MN-30-previo")).thenReturn(result(PaymentStatus.APPROVED, null));
        // El anterior resultó estar cobrado: ahora la reserva ya tiene un pago aprobado
        when(repository.existsByBookingIdAndTypeAndStatus(BOOKING_ID, PaymentType.CHARGE, PaymentStatus.APPROVED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.payWithCard(BOOKING_ID, TOURIST_ID, request()))
                .isInstanceOf(PaymentException.class);

        assertThat(previous.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        verify(gateway, never()).payWithCard(any(), anyString(), any(), any());
    }

    @Test
    void theCheckoutModeAndPublicKeyComeFromTheService() {
        when(gateway.publicKey()).thenReturn("TEST-public-key-inventada");

        assertThat(service.usesCardForm()).isTrue();
        assertThat(service.checkoutPublicKey()).isEqualTo("TEST-public-key-inventada");

        when(gateway.checkoutMode()).thenReturn(CheckoutMode.REDIRECT);
        assertThat(service.usesCardForm()).isFalse();
    }
}
