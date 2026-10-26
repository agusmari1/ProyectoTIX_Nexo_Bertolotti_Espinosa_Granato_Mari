package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;
import uy.edu.um.nexo.mnatural.mail.BookingMailer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingConfirmationNotifierTest {

    private final BookingQrService qrService = mock(BookingQrService.class);
    private final BookingMailer mailer = mock(BookingMailer.class);
    private final BookingConfirmationNotifier notifier = new BookingConfirmationNotifier(qrService, mailer);

    private final BookingConfirmedEvent event = new BookingConfirmedEvent(46L, "ana@ejemplo.test", "Ana",
            "MN-7KQ2XH4P", "Cabalgata", LocalDateTime.now().plusDays(10), 2, new BigDecimal("3000.00"));

    @Test
    void sendsTheMailWithTheQrOfTheBookingCode() {
        byte[] qr = {1, 2, 3};
        when(qrService.generatePng("MN-7KQ2XH4P")).thenReturn(qr);

        notifier.onBookingConfirmed(event);

        verify(mailer).sendBookingConfirmation(event, qr);
    }

    @Test
    void ifTheMailFailsNothingIsThrown() {
        when(qrService.generatePng(any())).thenReturn(new byte[]{1});
        doThrow(new RuntimeException("SMTP caído")).when(mailer).sendBookingConfirmation(any(), any());

        assertThatCode(() -> notifier.onBookingConfirmed(event)).doesNotThrowAnyException();
    }
}
