package uy.edu.um.nexo.mnatural.mail;

import uy.edu.um.nexo.mnatural.service.BookingConfirmedEvent;

import java.time.format.DateTimeFormatter;

// A. Mail de reserva confirmada con el QR adjunto.
// Es una interfaz propia y no usa el EmailService de Inés porque ese solo manda texto, sin adjuntos,
// y no queríamos modificarlo. Tiene dos implementaciones, elegidas con app.mail.enabled
// (la misma propiedad que usa EmailService):
//   - LoggingBookingMailer (por defecto): no envía nada, escribe el mail en el log;
//   - SmtpBookingMailer (app.mail.enabled=true): lo envía por SMTP con las credenciales spring.mail.*,
//     que se leen de variables de entorno.
// Ninguna lanza excepciones: si el mail falla, la reserva ya está confirmada y eso no se deshace
public interface BookingMailer {

    DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    void sendBookingConfirmation(BookingConfirmedEvent booking, byte[] qrPng);

    default String subject(BookingConfirmedEvent booking) {
        return "Reserva confirmada " + booking.bookingCode() + " - " + booking.activityName();
    }

    default String body(BookingConfirmedEvent booking) {
        return "Hola " + booking.touristName() + ":\n\n"
                + "Tu pago se acreditó y tu reserva está confirmada.\n\n"
                + "Actividad: " + booking.activityName() + "\n"
                + "Fecha y hora: " + booking.startDateTime().format(DATE_TIME_FORMAT) + "\n"
                + "Participantes: " + booking.participantCount() + "\n"
                + "Total pagado: $ " + booking.totalAmount() + " (pesos uruguayos)\n"
                + "Código de reserva: " + booking.bookingCode() + "\n\n"
                + "Te adjuntamos el código QR de la reserva. Presentalo (o el código) el día de la actividad.\n\n"
                + "Uruguay Mnatural";
    }

    default String attachmentName(BookingConfirmedEvent booking) {
        return "reserva-" + booking.bookingCode() + ".png";
    }
}
