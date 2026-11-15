package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.mail.BookingMailer;

// Cuando se confirma una reserva, genera el QR y se lo manda al turista por mail.
// AFTER_COMMIT (igual que ProviderReviewNotifier): solo corre si la confirmación ya quedó guardada,
// y si el QR o el mail fallan, la reserva sigue confirmada
@Component
public class BookingConfirmationNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationNotifier.class);

    private final BookingQrService qrService;
    private final BookingMailer mailer;

    public BookingConfirmationNotifier(BookingQrService qrService, BookingMailer mailer) {
        this.qrService = qrService;
        this.mailer = mailer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        try {
            byte[] qr = qrService.generatePng(event.bookingCode());
            mailer.sendBookingConfirmation(event, qr);
        } catch (RuntimeException e) {
            log.error("No se pudo mandar el QR de la reserva {}: {}", event.bookingId(), e.getMessage(), e);
        }
    }
}
