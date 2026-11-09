package uy.edu.um.nexo.mnatural.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import uy.edu.um.nexo.mnatural.service.BookingCancelledEvent;
import uy.edu.um.nexo.mnatural.service.BookingConfirmedEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: JavaMailSender es un mock, no hace falta ningún servidor de correo. Los datos son de mentira
@ExtendWith(OutputCaptureExtension.class)
class BookingMailerTest {

    private final BookingConfirmedEvent booking = new BookingConfirmedEvent(46L, "ana@ejemplo.test", "Ana",
            "MN-7KQ2XH4P", "Cabalgata", LocalDateTime.of(2026, 12, 15, 10, 0), 2, new BigDecimal("3000.00"));
    private final byte[] qr = {1, 2, 3, 4};

    @Test
    void withoutSmtpTheMailIsWrittenToTheLog(CapturedOutput output) {
        new LoggingBookingMailer().sendBookingConfirmation(booking, qr);

        assertThat(output.getAll())
                .contains("ana@ejemplo.test")
                .contains("MN-7KQ2XH4P")
                .contains("15/12/2026 10:00")
                .contains("reserva-MN-7KQ2XH4P.png");
    }

    @Test
    void withSmtpTheMailIsSentWithTheQrAttached() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        new SmtpBookingMailer(sender, "avisos@ejemplo.test").sendBookingConfirmation(booking, qr);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getAllRecipients()).containsExactly(new InternetAddress("ana@ejemplo.test"));
        assertThat(message.getSubject()).contains("MN-7KQ2XH4P");
        assertThat(findAttachment((Multipart) message.getContent())).isEqualTo("reserva-MN-7KQ2XH4P.png");
    }

    @Test
    void ifSmtpFailsNothingIsThrown() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("servidor caído")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> new SmtpBookingMailer(sender, "").sendBookingConfirmation(booking, qr))
                .doesNotThrowAnyException();
    }

    // ===== Reserva cancelada =====

    private BookingCancelledEvent cancelled(BookingCancelledEvent.Reason reason, String refund, boolean completed) {
        return new BookingCancelledEvent(46L, "ana@ejemplo.test", "Ana", "MN-7KQ2XH4P", "Cabalgata",
                LocalDateTime.of(2026, 12, 15, 10, 0), reason, refund == null ? null : new BigDecimal(refund), completed);
    }

    @Test
    void cancellationByTheTouristShowsTheRefundedAmount() {
        String body = new LoggingBookingMailer().body(
                cancelled(BookingCancelledEvent.Reason.TOURIST_CANCELLED, "1500.00", true));

        assertThat(body).contains("Cancelaste tu reserva").contains("$ 1.500,00").contains("Te devolvimos")
                .contains("15/12/2026 10:00").contains("MN-7KQ2XH4P");
    }

    @Test
    void cancellationWithoutRefundExplainsThePolicy() {
        String body = new LoggingBookingMailer().body(
                cancelled(BookingCancelledEvent.Reason.TOURIST_CANCELLED, null, false));

        assertThat(body).contains("no corresponde reembolso");
    }

    @Test
    void suspensionExplainsTheReasonAndAPendingRefund() {
        String body = new LoggingBookingMailer().body(
                cancelled(BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED, "3000.00", false));

        assertThat(body).contains("El prestador suspendió").contains("$ 3.000,00").contains("en proceso");
    }

    @Test
    void withoutSmtpTheCancellationIsWrittenToTheLog(CapturedOutput output) {
        new LoggingBookingMailer().sendBookingCancellation(
                cancelled(BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED, null, false));

        assertThat(output.getAll()).contains("ana@ejemplo.test").contains("Reserva cancelada MN-7KQ2XH4P");
    }

    @Test
    void withSmtpTheCancellationIsSent() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        new SmtpBookingMailer(sender, "avisos@ejemplo.test").sendBookingCancellation(
                cancelled(BookingCancelledEvent.Reason.TOURIST_CANCELLED, "1500.00", true));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()).containsExactly(new InternetAddress("ana@ejemplo.test"));
        assertThat(captor.getValue().getSubject()).isEqualTo("Reserva cancelada MN-7KQ2XH4P - Cabalgata");
    }

    @Test
    void ifSmtpFailsTheCancellationMailThrowsNothing() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("servidor caído")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> new SmtpBookingMailer(sender, "").sendBookingCancellation(
                cancelled(BookingCancelledEvent.Reason.TOURIST_CANCELLED, null, false))).doesNotThrowAnyException();
    }

    // Busca el nombre del adjunto dentro de las partes del mail
    private String findAttachment(Multipart multipart) throws Exception {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.getFileName() != null) {
                return part.getFileName();
            }
            if (part.getContent() instanceof Multipart nested) {
                String found = findAttachment(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
