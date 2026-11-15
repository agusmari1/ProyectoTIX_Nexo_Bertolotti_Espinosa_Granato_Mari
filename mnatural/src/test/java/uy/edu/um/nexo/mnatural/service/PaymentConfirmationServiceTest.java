package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Los servicios de pagos y de confirmación son mocks: no se toca la base de datos
class PaymentConfirmationServiceTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final BookingConfirmationService confirmationService = mock(BookingConfirmationService.class);

    private PaymentConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentConfirmationService(paymentService, confirmationService);
    }

    @Test
    void withoutAnApprovedPaymentNothingHappens() {
        when(paymentService.hasApprovedCharge(30L)).thenReturn(false);

        assertThat(service.confirmOrRefund(30L)).isEqualTo(PaymentConfirmationService.Outcome.NOT_PAID);

        verify(confirmationService, never()).confirmIfPaid(anyLong());
        verify(paymentService, never()).refundIfPaidButNotConfirmed(anyLong());
    }

    @Test
    void aPaidBookingThatCanBeConfirmedIsConfirmedAndNotRefunded() {
        when(paymentService.hasApprovedCharge(30L)).thenReturn(true);
        when(confirmationService.confirmIfPaid(30L)).thenReturn(true);

        assertThat(service.confirmOrRefund(30L)).isEqualTo(PaymentConfirmationService.Outcome.CONFIRMED);

        verify(paymentService, never()).refundIfPaidButNotConfirmed(anyLong());
    }

    @Test
    void aPaidBookingThatCannotBeConfirmedIsRefunded() {
        when(paymentService.hasApprovedCharge(30L)).thenReturn(true);
        when(confirmationService.confirmIfPaid(30L)).thenReturn(false);

        assertThat(service.confirmOrRefund(30L)).isEqualTo(PaymentConfirmationService.Outcome.REFUND);

        verify(paymentService).refundIfPaidButNotConfirmed(30L);
    }
}
