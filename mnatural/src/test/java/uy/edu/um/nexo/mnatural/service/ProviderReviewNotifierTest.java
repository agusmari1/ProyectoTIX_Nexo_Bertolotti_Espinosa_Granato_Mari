package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// Test unitario: EmailService es un mock, no se manda ningún correo
class ProviderReviewNotifierTest {

    private final EmailService emailService = mock(EmailService.class);

    private ProviderReviewNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new ProviderReviewNotifier(emailService);
    }

    private Provider provider(ApprovalStatus status, String reason) {
        Provider provider = new Provider();
        provider.setApprovalStatus(status);
        provider.setRejectionReason(reason);
        return provider;
    }

    @Test
    void anApprovedProviderIsToldTheyWereApproved() {
        Provider provider = provider(ApprovalStatus.APPROVED, null);

        notifier.onProviderReviewed(new ProviderReviewedEvent(provider));

        verify(emailService).sendProviderApproved(provider);
        verify(emailService, never()).sendProviderRejected(any(), anyString());
    }

    @Test
    void aRejectedProviderIsToldTheyWereRejectedTogetherWithTheReason() {
        Provider provider = provider(ApprovalStatus.REJECTED, "Falta documentación");

        notifier.onProviderReviewed(new ProviderReviewedEvent(provider));

        verify(emailService).sendProviderRejected(provider, "Falta documentación");
        verify(emailService, never()).sendProviderApproved(any());
    }

    @Test
    void aProviderStillPendingGetsNoEmail() {
        notifier.onProviderReviewed(new ProviderReviewedEvent(provider(ApprovalStatus.PENDING, null)));

        verifyNoInteractions(emailService);
    }

    // El correo solo sale si la transacción de AdminService se confirmó: si se deshace, el listener no corre
    @Test
    void theListenerRunsOnlyAfterTheTransactionCommits() throws NoSuchMethodException {
        TransactionalEventListener listener = ProviderReviewNotifier.class
                .getMethod("onProviderReviewed", ProviderReviewedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
