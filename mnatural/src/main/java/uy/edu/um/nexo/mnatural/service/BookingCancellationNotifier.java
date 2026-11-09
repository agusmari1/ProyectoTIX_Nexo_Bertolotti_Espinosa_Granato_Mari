package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.mail.BookingMailer;

// Cuando se cancela una reserva, le avisa al turista por mail con el motivo y el reembolso.
// AFTER_COMMIT (igual que BookingConfirmationNotifier): solo corre si la cancelación ya quedó guardada,
// y si el mail falla, la cancelación sigue siendo válida
@Component
public class BookingCancellationNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingCancellationNotifier.class);

    private final BookingMailer mailer;

    public BookingCancellationNotifier(BookingMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        try {
            mailer.sendBookingCancellation(event);
        } catch (RuntimeException e) {
            log.error("No se pudo avisar la cancelación de la reserva {}: {}", event.bookingId(), e.getMessage(), e);
        }
    }
}
