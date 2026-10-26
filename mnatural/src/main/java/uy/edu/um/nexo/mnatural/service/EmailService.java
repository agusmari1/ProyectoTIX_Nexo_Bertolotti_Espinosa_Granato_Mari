package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import uy.edu.um.nexo.mnatural.entities.Provider;

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
                        @Value("${spring.mail.username:}") String from,
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

    public void sendProviderRejected(Provider provider, String reason) {
        String body = "Hola " + provider.getName() + ",\n\n"
                + "Lamentablemente tu solicitud para ser prestador en Uruguay Mnatural fue rechazada.\n\n"
                + "Motivo: " + reason + "\n\n"
                + "Saludos,\n"
                + "Equipo de Uruguay Mnatural";
        send(provider.getEmail(), "Tu solicitud fue rechazada - Uruguay Mnatural", body);
    }
}
