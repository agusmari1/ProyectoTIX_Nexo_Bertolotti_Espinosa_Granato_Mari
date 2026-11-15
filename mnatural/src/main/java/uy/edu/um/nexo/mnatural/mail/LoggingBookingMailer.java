package uy.edu.um.nexo.mnatural.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uy.edu.um.nexo.mnatural.service.BookingConfirmedEvent;

// Implementación por defecto (sin SMTP configurado): no envía nada, escribe el mail en el log.
// Así el proyecto funciona en cualquier compu sin servidor de correo
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingBookingMailer implements BookingMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingBookingMailer.class);

    @Override
    public void sendBookingConfirmation(BookingConfirmedEvent booking, byte[] qrPng) {
        log.info("Correo deshabilitado (app.mail.enabled=false), no se envía. Mensaje:\n"
                        + "  Para: {}\n  Asunto: {}\n  Adjunto: {} ({} bytes)\n  Cuerpo:\n{}",
                booking.touristEmail(), subject(booking), attachmentName(booking),
                qrPng == null ? 0 : qrPng.length, body(booking));
    }
}
