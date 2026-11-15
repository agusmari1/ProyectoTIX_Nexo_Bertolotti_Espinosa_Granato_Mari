package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uy.edu.um.nexo.mnatural.entities.Provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Servicio de correo para todo el proyecto. Para mandar otro tipo de mail (por ejemplo la
// confirmación de una reserva con el QR) alcanza con agregar un método que arme el texto y
// llame a send(...).
//
// Con app.mail.enabled=false (el valor por defecto) NO se envía nada: el mensaje completo se
// imprime en el log, así el proyecto funciona en cualquier máquina sin servidor de correo.
// Un error al enviar se registra en el log pero nunca se lanza, para que un correo caído no
// deshaga lo que se estaba haciendo (por ejemplo aprobar a un prestador).
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:${spring.mail.username:}}") String from,
                        @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        // Sin la barra final, para poder escribir baseUrl + "/login"
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // Si el envío está activado (app.mail.enabled=true). Sirve para avisarle al usuario
    // si el mensaje realmente salió o solo se imprimió en el log
    public boolean isEnabled() {
        return enabled;
    }

    // Mensaje de texto simple. Nunca lanza una excepción.
    // Con el correo deshabilitado el mensaje se imprime COMPLETO en el log, y eso incluye una
    // contraseña si el mensaje la lleva (ver sendAdminCreated): en desarrollo es la única forma de
    // conocerla. Con el correo activado el log solo registra a quién se mandó y con qué asunto
    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Correo deshabilitado (app.mail.enabled=false), no se envía. Mensaje:\n"
                    + "  Para: {}\n  Asunto: {}\n  Cuerpo:\n{}", to, subject, body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!from.isBlank()) {
                message.setFrom(from);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Correo enviado a {} (asunto: {})", to, subject);
        } catch (RuntimeException e) {
            log.error("No se pudo enviar el correo a {} (asunto: {}): {}", to, subject, e.getMessage(), e);
        }
    }

    public void sendProviderApproved(Provider provider) {
        String body = "Hola " + provider.getName() + ",\n\n"
                + "Tu solicitud para ser prestador en Uruguay Mnatural fue aprobada. "
                + "Ya podés ingresar a la plataforma con tu correo electrónico y tu contraseña:\n\n"
                + baseUrl + "/login\n\n"
                + "¡Bienvenido/a!\n"
                + "Equipo de Uruguay Mnatural";
        send(provider.getEmail(), "Tu solicitud fue aprobada - Uruguay Mnatural", body);
    }

    // Datos de acceso de un administrador nuevo: su usuario es el correo y la contraseña la generó
    // el sistema al azar, así que este correo es el único lugar donde se la manda
    public void sendAdminCreated(String name, String email, String password) {
        send(email, "Tu cuenta de administrador - Uruguay Mnatural", accessBody(name, email, password,
                "Se creó tu cuenta de administrador en Uruguay Mnatural."));
    }

    // Contraseña nueva para un admin que ya existía (se generó otra al azar y la anterior dejó de servir)
    public void sendAdminPasswordReset(String name, String email, String password) {
        send(email, "Tu nueva contraseña - Uruguay Mnatural", accessBody(name, email, password,
                "Se generó una contraseña nueva para tu cuenta de administrador en Uruguay Mnatural. "
                        + "La anterior ya no sirve."));
    }

    private String accessBody(String name, String email, String password, String intro) {
        return "Hola " + name + ",\n\n"
                + intro + " Para ingresar usá estos datos:\n\n"
                + "Usuario: " + email + "\n"
                + "Contraseña: " + password + "\n\n"
                + "Ingresá desde " + baseUrl + "/login\n\n"
                + "La contraseña se generó automáticamente y solo se mandó a este correo. "
                + "No la compartas con nadie.\n\n"
                + "Equipo de Uruguay Mnatural";
    }

    // ===== Correos al turista =====

    public void sendTouristWelcome(String name, String email) {
        send(email, "Tu cuenta fue creada - Uruguay Mnatural", "Hola " + name + ",\n\n"
                + "Tu cuenta de turista en Uruguay Mnatural se creó con éxito. "
                + "Ya podés ingresar con tu correo electrónico y la contraseña que elegiste, "
                + "explorar las actividades y reservar:\n\n"
                + baseUrl + "/actividades\n\n"
                + "¡Bienvenido/a!\n"
                + "Equipo de Uruguay Mnatural");
    }

    public void sendBookingPending(EmailEvents.BookingPending booking) {
        send(booking.touristEmail(), "Reserva creada, pendiente de pago - " + booking.activityName(),
                "Hola " + booking.touristName() + ",\n\n"
                        + "Creamos tu reserva y guardamos tus lugares, pero todavía falta pagarla.\n\n"
                        + "Actividad: " + booking.activityName() + "\n"
                        + "Fecha y hora: " + date(booking.startDateTime()) + "\n"
                        + "Participantes: " + booking.participantCount() + "\n"
                        + "Total a pagar: " + money(booking.totalAmount()) + "\n"
                        + "Código de reserva: " + booking.bookingCode() + "\n\n"
                        + "Tenés tiempo para pagar hasta las " + date(booking.payBefore()) + ". Pasado ese momento la "
                        + "reserva vence y los lugares se liberan.\n\n"
                        + "Pagá desde tu reserva (hace falta iniciar sesión):\n"
                        + baseUrl + "/payments/bookings/" + booking.bookingId() + "\n\n"
                        + "Equipo de Uruguay Mnatural");
    }

    public void sendPaymentFailed(EmailEvents.PaymentFailed payment) {
        String why = payment.error()
                ? "Hubo un problema al procesar tu pago y no pudimos confirmarlo. Si ves un cobro en tu tarjeta, se va a "
                + "reflejar en tu reserva en unos minutos; si no se acredita, podés intentar de nuevo."
                : "Tu pago fue rechazado y no se te cobró.";
        String detail = payment.reason() == null || payment.reason().isBlank() ? "" : "Detalle: " + payment.reason() + "\n\n";
        String retry = payment.payBefore() == null
                ? "Entrá a tu reserva para ver cómo seguir:\n"
                : "Tu reserva sigue pendiente y podés intentar pagar de nuevo hasta las " + date(payment.payBefore())
                + " (con la misma u otra tarjeta):\n";
        send(payment.touristEmail(), "No pudimos procesar tu pago - " + payment.activityName(),
                "Hola " + payment.touristName() + ",\n\n"
                        + why + "\n\n" + detail + retry
                        + baseUrl + "/payments/bookings/" + payment.bookingId() + "\n\n"
                        + "Equipo de Uruguay Mnatural");
    }

    public void sendBookingExpired(EmailEvents.BookingExpired booking) {
        send(booking.touristEmail(), "Tu reserva venció - " + booking.activityName(),
                "Hola " + booking.touristName() + ",\n\n"
                        + "Tu reserva " + booking.bookingCode() + " de \"" + booking.activityName() + "\" del "
                        + date(booking.startDateTime()) + " venció porque no se pagó a tiempo, y los lugares que "
                        + "teníamos guardados se liberaron. No se te cobró nada.\n\n"
                        + "Si todavía querés ir, podés volver a reservar (si quedan lugares):\n"
                        + baseUrl + "/actividades\n\n"
                        + "Equipo de Uruguay Mnatural");
    }

    public void sendBookingCancelled(EmailEvents.BookingCancelled booking) {
        boolean suspended = booking.cause() == EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED;
        String reason = suspended
                ? "El prestador suspendió la actividad en esa fecha y horario, así que tu reserva " + booking.bookingCode()
                + " de \"" + booking.activityName() + "\" (" + date(booking.startDateTime()) + ") se canceló."
                : "Cancelaste tu reserva " + booking.bookingCode() + " de \"" + booking.activityName() + "\" ("
                + date(booking.startDateTime()) + ").";
        String refund = switch (booking.refundState()) {
            case COMPLETED -> "Reembolso: se te devolvieron " + pesos(booking.refundAmount())
                    + (suspended ? " (el 100 % de lo pagado)" : "") + " por el mismo medio de pago.";
            case IN_PROGRESS -> "Reembolso: estamos procesando la devolución de " + pesos(booking.refundAmount())
                    + (suspended ? " (el 100 % de lo pagado)" : "") + " por el mismo medio de pago. "
                    + "Puede demorar y, si el servicio de pagos no responde, lo reintentamos solos. Te avisamos cuando se complete.";
            case NONE -> suspended
                    ? "Como la reserva todavía no estaba pagada, no se te cobró nada."
                    : "Según la política de cancelación de la actividad, no corresponde reembolso.";
        };
        send(booking.touristEmail(), (suspended ? "Se canceló tu reserva por una suspensión - " : "Cancelaste tu reserva - ")
                + booking.activityName(), "Hola " + booking.touristName() + ",\n\n"
                + reason + "\n\n" + refund + "\n\n"
                + (suspended ? "Lamentamos el inconveniente. Podés buscar otra fecha u otra actividad:\n" + baseUrl + "/actividades\n\n" : "")
                + "Equipo de Uruguay Mnatural");
    }

    public void sendRefundCompleted(EmailEvents.RefundCompleted refund) {
        String why = refund.reason() == null || refund.reason().isBlank() ? "" : refund.reason() + "\n\n";
        send(refund.touristEmail(), "Te devolvimos el dinero de tu reserva - " + refund.activityName(),
                "Hola " + refund.touristName() + ",\n\n"
                        + "Se completó el reembolso de " + money(refund.amount()) + " de tu reserva " + refund.bookingCode()
                        + " (\"" + refund.activityName() + "\") por el mismo medio de pago. "
                        + "Según tu banco o tarjeta, puede tardar unos días en verse reflejado.\n\n"
                        + why + "Equipo de Uruguay Mnatural");
    }

    // ===== Correos al prestador y a los administradores =====

    // Cuando cancela un turista. Sin datos personales del turista: solo qué reserva y qué lugares se liberaron
    public void sendProviderBookingCancelled(String providerName, String providerEmail, EmailEvents.BookingCancelled booking) {
        send(providerEmail, "Un turista canceló una reserva - " + booking.activityName(), "Hola " + providerName + ",\n\n"
                + "Un turista canceló su reserva " + booking.bookingCode() + " en una de tus actividades. "
                + "Los lugares quedaron libres otra vez.\n\n"
                + "Actividad: " + booking.activityName() + "\n"
                + "Fecha y hora: " + date(booking.startDateTime()) + "\n"
                + "Participantes: " + booking.participantCount() + "\n\n"
                + "Podés ver el estado de tus reservas en tu panel (hace falta iniciar sesión):\n"
                + baseUrl + "/provider/reservations\n\n"
                + "Equipo de Uruguay Mnatural");
    }

    public void sendProviderRegistered(String name, String email, String businessName) {
        send(email, "Recibimos tu solicitud - Uruguay Mnatural", "Hola " + name + ",\n\n"
                + "Recibimos tu solicitud para ser prestador de Uruguay Mnatural con tu emprendimiento \""
                + businessName + "\". Un administrador la va a revisar y te vamos a avisar por correo electrónico "
                + "si fue aprobada o rechazada. Hasta entonces no vas a poder ingresar a la plataforma.\n\n"
                + "Gracias por sumarte.\n"
                + "Equipo de Uruguay Mnatural");
    }

    public void sendProviderNewBooking(String providerName, String providerEmail, BookingConfirmedEvent booking) {
        send(providerEmail, "Nueva reserva confirmada - " + booking.activityName(), "Hola " + providerName + ",\n\n"
                + "Se confirmó una nueva reserva en una de tus actividades.\n\n"
                + "Actividad: " + booking.activityName() + "\n"
                + "Fecha y hora: " + date(booking.startDateTime()) + "\n"
                + "Participantes: " + booking.participantCount() + "\n"
                + "Código de reserva: " + booking.bookingCode() + "\n\n"
                + "Los datos de los participantes los ves en tu panel (hace falta iniciar sesión):\n"
                + baseUrl + "/provider/reservations\n\n"
                + "Equipo de Uruguay Mnatural");
    }

    public void sendAdminProviderPending(String adminName, String adminEmail, String providerName, String businessName) {
        send(adminEmail, "Nuevo prestador pendiente de aprobación - Uruguay Mnatural", "Hola " + adminName + ",\n\n"
                + "Hay una solicitud nueva de prestador esperando tu revisión: " + providerName
                + " (emprendimiento \"" + businessName + "\").\n\n"
                + "Revisala desde el panel (hace falta iniciar sesión como administrador):\n"
                + baseUrl + "/admin/prestadores\n\n"
                + "Equipo de Uruguay Mnatural");
    }

    // Fecha y hora como se muestran en toda la aplicación
    private static String date(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    // Solo el monto ("$ 750.00"), para cuando la frase ya aclara de qué se trata
    private static String pesos(BigDecimal amount) {
        return amount == null ? "-" : "$ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "-" : "$ " + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " (pesos uruguayos)";
    }

    public void sendProviderRejected(Provider provider, String reason) {
        String body = "Hola " + provider.getName() + ",\n\n"
                + "Lamentablemente tu solicitud para ser prestador en Uruguay Mnatural fue rechazada.\n\n"
                + "Motivo: " + reason + "\n\n"
                + "Saludos,\n"
                + "Equipo de Uruguay Mnatural";
        send(provider.getEmail(), "Tu solicitud fue rechazada - Uruguay Mnatural", body);
    }
}
