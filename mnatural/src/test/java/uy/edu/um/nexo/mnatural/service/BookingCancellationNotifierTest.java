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

class BookingCancellationNotifierTest {

    private final BookingMailer mailer = mock(BookingMailer.class);
    private final BookingCancellationNotifier notifier = new BookingCancellationNotifier(mailer);

    private final BookingCancelledEvent event = new BookingCancelledEvent(46L, "ana@ejemplo.test", "Ana",
            "MN-7KQ2XH4P", "Cabalgata", LocalDateTime.now().plusDays(10),
            BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED, new BigDecimal("3000.00"), true);

    @Test
    void sendsTheCancellationMail() {
        notifier.onBookingCancelled(event);

        verify(mailer).sendBookingCancellation(event);
    }

    @Test
    void ifTheMailFailsNothingIsThrown() {
        doThrow(new RuntimeException("SMTP caído")).when(mailer).sendBookingCancellation(any());

        assertThatCode(() -> notifier.onBookingCancelled(event)).doesNotThrowAnyException();
    }
}
