package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Contenido de los correos automáticos. JavaMailSender es un mock: no se manda nada
class EmailNotificationsTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final LocalDateTime START = LocalDateTime.of(2027, 1, 15, 9, 30);
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 1, 10, 18, 45);

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, true, "no-reply@mnatural.uy", BASE_URL);
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static EmailEvents.BookingCancelled cancelled(EmailEvents.BookingCancelled.Cause cause,
                                                          EmailEvents.BookingCancelled.RefundState state,
                                                          BigDecimal amount) {
        return new EmailEvents.BookingCancelled(30L, "ana@ejemplo.com", "Ana", "MN-ABCD2345", "Cabalgata", START, 2,
                cause, amount, state);
    }

    // ===== Turista =====

    @Test
    void theWelcomeEmailGoesToTheTouristWithALinkToTheActivities() {
        service.sendTouristWelcome("Ana", "ana@ejemplo.com");

        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly("ana@ejemplo.com");
        assertThat(mail.getFrom()).isEqualTo("no-reply@mnatural.uy");
        assertThat(mail.getSubject()).contains("Tu cuenta fue creada");
        assertThat(mail.getText()).contains("Hola Ana").contains("se creó con éxito").contains(BASE_URL + "/actividades");
        // Nunca se manda la contraseña que eligió
        assertThat(mail.getText().toLowerCase()).doesNotContain("contraseña:");
    }

    @Test
    void thePendingBookingEmailTellsTheDeadlineTheTotalAndTheCode() {
        service.sendBookingPending(new EmailEvents.BookingPending(30L, "ana@ejemplo.com", "Ana", "MN-ABCD2345",
                "Cabalgata", START, 3, new BigDecimal("1500"), DEADLINE));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("pendiente de pago").contains("Cabalgata");
        assertThat(mail.getText()).contains("hasta las 10/01/2027 18:45").contains("$ 1500.00 (pesos uruguayos)")
                .contains("Participantes: 3").contains("MN-ABCD2345").contains("15/01/2027 09:30")
                .contains(BASE_URL + "/payments/bookings/30");
    }

    @Test
    void aRejectedPaymentEmailSaysYouWereNotChargedAndWhenYouCanRetry() {
        service.sendPaymentFailed(new EmailEvents.PaymentFailed(30L, "ana@ejemplo.com", "Ana", "Cabalgata", false,
                "La tarjeta no tiene fondos suficientes.", DEADLINE));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("No pudimos procesar tu pago");
        assertThat(mail.getText()).contains("rechazado y no se te cobró").contains("fondos suficientes")
                .contains("intentar pagar de nuevo hasta las 10/01/2027 18:45").contains(BASE_URL + "/payments/bookings/30");
    }

    @Test
    void anErrorEmailDoesNotPromiseThatYouWereNotCharged() {
        service.sendPaymentFailed(new EmailEvents.PaymentFailed(30L, "ana@ejemplo.com", "Ana", "Cabalgata", true,
                null, null));

        SimpleMailMessage mail = sent();
        assertThat(mail.getText()).contains("problema al procesar").doesNotContain("no se te cobró")
                .doesNotContain("Detalle:");
        // Sin plazo para reintentar (la reserva ya no está pendiente): solo manda a ver la reserva
        assertThat(mail.getText()).contains("Entrá a tu reserva").doesNotContain("hasta las");
    }

    @Test
    void theExpiredBookingEmailSaysNothingWasCharged() {
        service.sendBookingExpired(new EmailEvents.BookingExpired(30L, "ana@ejemplo.com", "Ana", "MN-ABCD2345",
                "Cabalgata", START));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("Tu reserva venció");
        assertThat(mail.getText()).contains("MN-ABCD2345").contains("no se pagó a tiempo").contains("No se te cobró nada");
    }

    @Test
    void whenTheTouristCancelsWithARefundTheAmountIsShown() {
        service.sendBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.BY_TOURIST,
                EmailEvents.BookingCancelled.RefundState.COMPLETED, new BigDecimal("750")));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("Cancelaste tu reserva");
        assertThat(mail.getText()).contains("Cancelaste tu reserva MN-ABCD2345")
                .contains("se te devolvieron $ 750.00 por el mismo medio de pago").doesNotContain("el 100 %");
    }

    @Test
    void whenThePolicyGivesNoRefundTheEmailSaysSo() {
        service.sendBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.BY_TOURIST,
                EmailEvents.BookingCancelled.RefundState.NONE, null));

        assertThat(sent().getText()).contains("no corresponde reembolso");
    }

    @Test
    void whenTheProviderSuspendsTheEmailPromisesTheFullRefund() {
        service.sendBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED,
                EmailEvents.BookingCancelled.RefundState.COMPLETED, new BigDecimal("1500")));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("Se canceló tu reserva por una suspensión");
        assertThat(mail.getText()).contains("El prestador suspendió").contains("se te devolvieron $ 1500.00 (el 100 % de lo pagado)")
                .doesNotContain("(pesos uruguayos) (").contains(BASE_URL + "/actividades");
    }

    @Test
    void aRefundThatIsStillInProgressSaysWeWillTellYouWhenItIsDone() {
        service.sendBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED,
                EmailEvents.BookingCancelled.RefundState.IN_PROGRESS, new BigDecimal("1500")));

        assertThat(sent().getText()).contains("estamos procesando").contains("Te avisamos cuando se complete");
    }

    @Test
    void anUnpaidBookingCancelledBySuspensionSaysNothingWasCharged() {
        service.sendBookingCancelled(cancelled(EmailEvents.BookingCancelled.Cause.SCHEDULE_SUSPENDED,
                EmailEvents.BookingCancelled.RefundState.NONE, null));

        assertThat(sent().getText()).contains("no se te cobró nada").doesNotContain("devolvieron");
    }

    @Test
    void theRefundCompletedEmailShowsTheAmountAndTheReasonWhenThereIsOne() {
        service.sendRefundCompleted(new EmailEvents.RefundCompleted(30L, "ana@ejemplo.com", "Ana", "MN-ABCD2345",
                "Cabalgata", new BigDecimal("1500"), "Tu pago llegó cuando la reserva ya había vencido."));

        SimpleMailMessage mail = sent();
        assertThat(mail.getSubject()).contains("Te devolvimos el dinero");
        assertThat(mail.getText()).contains("reembolso de $ 1500.00").contains("MN-ABCD2345")
                .contains("ya había vencido");
    }

    // ===== Prestador y administradores =====

    @Test
    void theProviderIsToldTheRequestWasReceivedAndIsPending() {
        service.sendProviderRegistered("Luis", "luis@ejemplo.com", "Cabalgatas del Sur");

        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly("luis@ejemplo.com");
        assertThat(mail.getText()).contains("Cabalgatas del Sur").contains("fue aprobada o rechazada")
                .contains("no vas a poder ingresar");
    }

    @Test
    void theNewBookingEmailForTheProviderHasTheScheduleAndTheCountButNoTouristContact() {
        service.sendProviderNewBooking("Luis", "luis@ejemplo.com", new BookingConfirmedEvent(30L, "ana@ejemplo.com",
                "Ana", "MN-ABCD2345", "Cabalgata", START, 4, new BigDecimal("2000")));

        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly("luis@ejemplo.com");
        assertThat(mail.getText()).contains("Participantes: 4").contains("15/01/2027 09:30").contains("MN-ABCD2345")
                .contains(BASE_URL + "/provider/reservations");
        // Los datos del turista se ven en el panel, no por correo
        assertThat(mail.getText()).doesNotContain("ana@ejemplo.com").doesNotContain("Ana");
    }

    @Test
    void theProviderIsToldWhenATouristCancelsWithoutThePersonalData() {
        service.sendProviderBookingCancelled("Luis", "luis@ejemplo.com", cancelled(
                EmailEvents.BookingCancelled.Cause.BY_TOURIST, EmailEvents.BookingCancelled.RefundState.COMPLETED,
                new BigDecimal("750")));

        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly("luis@ejemplo.com");
        assertThat(mail.getText()).contains("MN-ABCD2345").contains("Participantes: 2").contains("libres otra vez");
        assertThat(mail.getText()).doesNotContain("ana@ejemplo.com").doesNotContain("750").doesNotContain("Ana");
    }

    @Test
    void theAdminIsToldThereIsAProviderToReviewWithALinkToThePanelOnly() {
        service.sendAdminProviderPending("Marta", "marta@mnatural.uy", "Luis Pérez", "Cabalgatas del Sur");

        SimpleMailMessage mail = sent();
        assertThat(mail.getTo()).containsExactly("marta@mnatural.uy");
        assertThat(mail.getText()).contains("Luis Pérez").contains("Cabalgatas del Sur")
                .contains(BASE_URL + "/admin/prestadores");
        assertThat(mail.getText()).doesNotContain("luis@ejemplo.com");
    }

    // ===== Ningún correo nuevo manda contraseñas =====

    @Test
    void noNewEmailCarriesAPassword() {
        service.sendTouristWelcome("Ana", "ana@ejemplo.com");
        service.sendProviderRegistered("Luis", "luis@ejemplo.com", "Cabalgatas");
        service.sendBookingPending(new EmailEvents.BookingPending(1L, "a@b.com", "Ana", "MN-A", "Act", START, 1,
                BigDecimal.TEN, DEADLINE));
        service.sendPaymentFailed(new EmailEvents.PaymentFailed(1L, "a@b.com", "Ana", "Act", false, "x", DEADLINE));
        service.sendBookingExpired(new EmailEvents.BookingExpired(1L, "a@b.com", "Ana", "MN-A", "Act", START));
        service.sendAdminProviderPending("Marta", "m@b.com", "Luis", "Cabalgatas");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(6)).send(captor.capture());
        for (SimpleMailMessage mail : captor.getAllValues()) {
            assertThat(mail.getText().toLowerCase()).as(mail.getSubject()).doesNotContain("contraseña:")
                    .doesNotContain("password");
        }
    }

    // ===== Si el envío falla, solo se registra =====

    @Test
    void aFailingMailServerNeverMakesAnyOfTheNewEmailsThrow() {
        doThrow(new MailSendException("servidor caído")).when(mailSender).send(any(SimpleMailMessage.class));
        EmailEvents.BookingCancelled cancel = cancelled(EmailEvents.BookingCancelled.Cause.BY_TOURIST,
                EmailEvents.BookingCancelled.RefundState.COMPLETED, BigDecimal.TEN);
        List<Runnable> all = List.of(
                () -> service.sendTouristWelcome("Ana", "a@b.com"),
                () -> service.sendProviderRegistered("Luis", "l@b.com", "Cabalgatas"),
                () -> service.sendBookingPending(new EmailEvents.BookingPending(1L, "a@b.com", "Ana", "MN-A", "Act",
                        START, 1, BigDecimal.TEN, DEADLINE)),
                () -> service.sendPaymentFailed(new EmailEvents.PaymentFailed(1L, "a@b.com", "Ana", "Act", true, null, null)),
                () -> service.sendBookingExpired(new EmailEvents.BookingExpired(1L, "a@b.com", "Ana", "MN-A", "Act", START)),
                () -> service.sendBookingCancelled(cancel),
                () -> service.sendRefundCompleted(new EmailEvents.RefundCompleted(1L, "a@b.com", "Ana", "MN-A", "Act",
                        BigDecimal.TEN, null)),
                () -> service.sendProviderNewBooking("Luis", "l@b.com", new BookingConfirmedEvent(1L, "a@b.com", "Ana",
                        "MN-A", "Act", START, 1, BigDecimal.TEN)),
                () -> service.sendProviderBookingCancelled("Luis", "l@b.com", cancel),
                () -> service.sendAdminProviderPending("Marta", "m@b.com", "Luis", "Cabalgatas"));

        for (Runnable send : all) {
            assertThatCode(send::run).doesNotThrowAnyException();
        }
    }

    @Test
    void withTheMailDisabledNothingIsSentAndTheMessageGoesToTheLog() {
        EmailService disabled = new EmailService(mailSender, false, "", BASE_URL);
        Consumer<EmailService> all = s -> {
            s.sendTouristWelcome("Ana", "a@b.com");
            s.sendBookingExpired(new EmailEvents.BookingExpired(1L, "a@b.com", "Ana", "MN-A", "Act", START));
        };

        all.accept(disabled);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
