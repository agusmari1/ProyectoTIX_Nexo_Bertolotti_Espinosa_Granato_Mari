package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Le manda al admin sus datos de acceso: al crearlo, o cuando se le genera una contraseña nueva.
// AFTER_COMMIT: solo corre si la transacción de AdminAccountService se confirmó; si se deshace,
// no sale ningún correo
@Component
public class AdminCreatedNotifier {

    private final EmailService emailService;

    public AdminCreatedNotifier(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminCreated(AdminCreatedEvent event) {
        emailService.sendAdminCreated(event.name(), event.email(), event.password());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAdminPasswordReset(AdminPasswordResetEvent event) {
        emailService.sendAdminPasswordReset(event.name(), event.email(), event.password());
    }
}
