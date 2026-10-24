package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import uy.edu.um.nexo.mnatural.entities.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Test unitario: JavaMailSender es un mock, así que no hace falta ningún servidor de correo.
// Los datos de las cuentas son de mentira.
@ExtendWith(OutputCaptureExtension.class)
class EmailServiceTest {

    private static final String BASE_URL = "http://localhost:8080";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private EmailService service(boolean enabled) {
        return new EmailService(mailSender, enabled, "avisos@ejemplo.test", BASE_URL);
    }

    private Provider provider() {
        Provider provider = new Provider();
        provider.setName("Ana");
        provider.setEmail("ana@ejemplo.test");
        return provider;
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void disabledDoesNotSendAndPrintsTheFullMessageInTheLog(CapturedOutput output) {
        service(false).send("ana@ejemplo.test", "Asunto de prueba", "Cuerpo de prueba");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertThat(output.getAll())
                .contains("ana@ejemplo.test")
                .contains("Asunto de prueba")
                .contains("Cuerpo de prueba");
    }

    @Test
    void enabledSendsTheMessage() {
        service(true).send("ana@ejemplo.test", "Asunto de prueba", "Cuerpo de prueba");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getFrom()).isEqualTo("avisos@ejemplo.test");
        assertThat(message.getTo()).containsExactly("ana@ejemplo.test");
        assertThat(message.getSubject()).isEqualTo("Asunto de prueba");
        assertThat(message.getText()).isEqualTo("Cuerpo de prueba");
    }

    @Test
    void sendFailureIsLoggedAndNotThrown(CapturedOutput output) {
        doThrow(new MailSendException("servidor caído")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service(true).send("ana@ejemplo.test", "Asunto", "Cuerpo"))
                .doesNotThrowAnyException();
        assertThat(output.getAll()).contains("No se pudo enviar el correo a ana@ejemplo.test");
    }

    @Test
    void approvedMessageGreetsByNameAndLinksToLogin() {
        service(true).sendProviderApproved(provider());

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("ana@ejemplo.test");
        assertThat(message.getSubject()).contains("aprobada");
        assertThat(message.getText())
                .contains("Hola Ana")
                .contains("Ya podés ingresar")
                .contains(BASE_URL + "/login");
    }

    @Test
    void baseUrlWithTrailingSlashDoesNotProduceADoubleSlash() {
        new EmailService(mailSender, true, "", BASE_URL + "/").sendProviderApproved(provider());

        assertThat(sentMessage().getText()).contains(BASE_URL + "/login").doesNotContain("//login");
    }

    @Test
    void rejectedMessageIncludesTheReason() {
        service(true).sendProviderRejected(provider(), "Documentación incompleta");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getSubject()).contains("rechazada");
        assertThat(message.getText())
                .contains("Hola Ana")
                .contains("rechazada")
                .contains("Motivo: Documentación incompleta");
    }

    @Test
    void withoutAnAccountTheFromAddressIsNotSet() {
        new EmailService(mailSender, true, "", BASE_URL).send("ana@ejemplo.test", "Asunto", "Cuerpo");

        assertThat(sentMessage().getFrom()).isNull();
    }
}
