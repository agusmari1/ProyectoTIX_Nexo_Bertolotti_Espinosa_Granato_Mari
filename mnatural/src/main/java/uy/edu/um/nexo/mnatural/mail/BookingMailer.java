package uy.edu.um.nexo.mnatural.mail;

import uy.edu.um.nexo.mnatural.service.BookingCancelledEvent;
import uy.edu.um.nexo.mnatural.service.BookingConfirmedEvent;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
    // Formato uruguayo para los montos: 1.500,00
    Locale PESOS = Locale.forLanguageTag("es-UY");

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

    // B. Aviso de reserva cancelada (por el turista o porque el prestador suspendió el horario),
    // con el motivo y el monto reembolsado. Va sin adjuntos
    void sendBookingCancellation(BookingCancelledEvent booking);

    default String subject(BookingCancelledEvent booking) {
        String code = booking.bookingCode() == null ? "" : " " + booking.bookingCode();
        return "Reserva cancelada" + code + " - " + booking.activityName();
    }

    default String body(BookingCancelledEvent booking) {
        String reason = booking.reason() == BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED
                ? "El prestador suspendió esta fecha y horario, así que tu reserva quedó cancelada. "
                + "Lamentamos el inconveniente."
                : "Cancelaste tu reserva.";
        String refund;
        if (booking.refundAmount() == null) {
            refund = booking.reason() == BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED
                    ? "No se había hecho ningún cobro, así que no hay nada para reembolsar."
                    : "Según la política de cancelación de la actividad, no corresponde reembolso.";
        } else {
            String amount = "$ " + String.format(PESOS, "%,.2f", booking.refundAmount()) + " (pesos uruguayos)";
            refund = booking.refundCompleted()
                    ? "Te devolvimos " + amount + " por el mismo medio de pago. Puede tardar unos días en verse "
                    + "en tu cuenta, según tu banco o tarjeta."
                    : "El reembolso de " + amount + " está en proceso: lo vamos a completar por el mismo medio "
                    + "de pago, sin que tengas que hacer nada.";
        }
        String code = booking.bookingCode() == null ? "" : "Código de reserva: " + booking.bookingCode() + "\n";
        return "Hola " + booking.touristName() + ":\n\n"
                + reason + "\n\n"
                + "Actividad: " + booking.activityName() + "\n"
                + "Fecha y hora: " + booking.startDateTime().format(DATE_TIME_FORMAT) + "\n"
                + code + "\n"
                + refund + "\n\n"
                + "Uruguay Mnatural";
    }
}
