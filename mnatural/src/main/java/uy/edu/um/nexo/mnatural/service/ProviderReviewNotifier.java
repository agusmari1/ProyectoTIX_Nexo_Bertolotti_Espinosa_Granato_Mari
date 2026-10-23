package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;

// Le avisa por correo al prestador cuando se resolvió su solicitud.
// AFTER_COMMIT: solo corre si la transacción de AdminService se confirmó. Si falla y se
// deshace, este método no se ejecuta y no sale ningún correo.
@Component
public class ProviderReviewNotifier {

    private final EmailService emailService;

    public ProviderReviewNotifier(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProviderReviewed(ProviderReviewedEvent event) {
        Provider provider = event.provider();
        if (provider.getApprovalStatus() == ApprovalStatus.APPROVED) {
            emailService.sendProviderApproved(provider);
        } else if (provider.getApprovalStatus() == ApprovalStatus.REJECTED) {
            emailService.sendProviderRejected(provider, provider.getRejectionReason());
        }
    }
}
