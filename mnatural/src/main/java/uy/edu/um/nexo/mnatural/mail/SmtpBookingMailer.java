package uy.edu.um.nexo.mnatural.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import uy.edu.um.nexo.mnatural.service.BookingConfirmedEvent;

import java.nio.charset.StandardCharsets;

// Envía el mail por SMTP con el QR adjunto. Se activa con app.mail.enabled=true (MAIL_ENABLED=true).
// El servidor y la cuenta salen de spring.mail.* (MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD...), que se leen
// de variables de entorno: en el repo no hay ninguna cuenta ni contraseña
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpBookingMailer implements BookingMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpBookingMailer.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpBookingMailer(JavaMailSender mailSender, @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendBookingConfirmation(BookingConfirmedEvent booking, byte[] qrPng) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true: el mensaje lleva adjuntos
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (!from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(booking.touristEmail());
            helper.setSubject(subject(booking));
            helper.setText(body(booking));
            helper.addAttachment(attachmentName(booking), new ByteArrayResource(qrPng), "image/png");
            mailSender.send(message);
            log.info("Correo de reserva confirmada enviado a {} (reserva {})", booking.touristEmail(), booking.bookingId());
        } catch (MessagingException | RuntimeException e) {
            log.error("No se pudo enviar el correo de la reserva {} a {}: {}",
                    booking.bookingId(), booking.touristEmail(), e.getMessage(), e);
        }
    }
}
