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

    // Mensaje de texto simple. Nunca lanza una excepción
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

    public void sendProviderRejected(Provider provider, String reason) {
        String body = "Hola " + provider.getName() + ",\n\n"
                + "Lamentablemente tu solicitud para ser prestador en Uruguay Mnatural fue rechazada.\n\n"
                + "Motivo: " + reason + "\n\n"
                + "Saludos,\n"
                + "Equipo de Uruguay Mnatural";
        send(provider.getEmail(), "Tu solicitud fue rechazada - Uruguay Mnatural", body);
    }
}
