package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los tres colaboradores son mocks: no se toca la base de datos ni internet
class PaymentWebhookServiceTest {

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentConfirmationService confirmationService = mock(PaymentConfirmationService.class);

    private PaymentWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(gateway, paymentService, confirmationService);
    }

    @Test
    void aNotificationForOurPaymentSyncsItAndConfirmsOrRefundsTheBooking() {
        when(gateway.findExternalReference("123")).thenReturn(Optional.of("MN-30-abc"));
        when(paymentService.syncByNotification("MN-30-abc")).thenReturn(Optional.of(30L));
        when(confirmationService.confirmOrRefund(30L)).thenReturn(PaymentConfirmationService.Outcome.CONFIRMED);

        assertThat(service.handlePaymentNotification("123")).isTrue();

        verify(paymentService).syncByNotification("MN-30-abc");
        verify(confirmationService).confirmOrRefund(30L);
    }

    @Test
    void aPaymentThatIsNotFromThisApplicationIsIgnored() {
        when(gateway.findExternalReference("999")).thenReturn(Optional.empty());

        assertThat(service.handlePaymentNotification("999")).isFalse();

        verify(paymentService, never()).syncByNotification(anyString());
        verify(confirmationService, never()).confirmOrRefund(anyLong());
    }

    @Test
    void aReferenceThatMatchesNoAttemptIsIgnored() {
        when(gateway.findExternalReference("123")).thenReturn(Optional.of("MN-1-zzz"));
        when(paymentService.syncByNotification("MN-1-zzz")).thenReturn(Optional.empty());

        assertThat(service.handlePaymentNotification("123")).isFalse();
        verify(confirmationService, never()).confirmOrRefund(anyLong());
    }

    @Test
    void ifThePaymentServiceDoesNotAnswerTheErrorGoesUpSoTheNotificationIsRetried() {
        when(gateway.findExternalReference("123")).thenThrow(new PaymentGatewayException("timeout"));

        assertThatThrownBy(() -> service.handlePaymentNotification("123")).isInstanceOf(PaymentGatewayException.class);
        verify(confirmationService, never()).confirmOrRefund(anyLong());
    }
}
