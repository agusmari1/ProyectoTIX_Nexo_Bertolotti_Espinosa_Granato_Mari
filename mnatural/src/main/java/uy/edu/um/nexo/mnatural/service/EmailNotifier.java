package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.BookingNotificationRepository;

// Manda los correos automáticos del sistema (turista, prestador y administradores), igual que ProviderReviewNotifier:
// cada uno es un evento publicado dentro de la transacción que hizo el cambio y se manda recién cuando esa
// transacción se confirma (AFTER_COMMIT). Si algo falla al armar o mandar el correo solo se registra en el log:
// nunca se deshace ni se rompe lo que ya se hizo (registrar, reservar, pagar...).
// Los mails de prestador aprobado o rechazado (ProviderReviewNotifier), el del administrador nuevo
// (AdminCreatedNotifier) y el de reserva confirmada con el QR (BookingConfirmationNotifier) están en esas clases
@Component
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);

    private final EmailService emailService;
    private final AdminRepository adminRepository;
    private final BookingNotificationRepository bookingRepository;

    public EmailNotifier(EmailService emailService,
                         AdminRepository adminRepository,
                         BookingNotificationRepository bookingRepository) {
        this.emailService = emailService;
        this.adminRepository = adminRepository;
        this.bookingRepository = bookingRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTouristRegistered(EmailEvents.TouristRegistered event) {
        safely("cuenta de turista creada", () -> emailService.sendTouristWelcome(event.name(), event.email()));
    }

    // Al prestador: solicitud recibida y pendiente. A los administradores: hay una solicitud para revisar
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProviderRegistered(EmailEvents.ProviderRegistered event) {
        safely("solicitud de prestador recibida",
                () -> emailService.sendProviderRegistered(event.name(), event.email(), event.businessName()));
        safely("aviso a los administradores", () -> {
            for (Admin admin : adminRepository.findAll()) {
                emailService.sendAdminProviderPending(admin.getName(), admin.getEmail(), event.name(),
                        event.businessName());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingPending(EmailEvents.BookingPending event) {
        safely("reserva pendiente de pago", () -> emailService.sendBookingPending(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(EmailEvents.PaymentFailed event) {
        safely("pago rechazado", () -> emailService.sendPaymentFailed(event));
    }

    // Al turista siempre. Al prestador solo si cancela el turista (si el prestador suspendió, ya lo sabe)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(EmailEvents.BookingCancelled event) {
        safely("reserva cancelada (turista)", () -> emailService.sendBookingCancelled(event));
        if (event.cause() == EmailEvents.BookingCancelled.Cause.BY_TOURIST) {
            safely("reserva cancelada (prestador)", () -> {
                Booking booking = bookingRepository.findWithProviderById(event.bookingId()).orElse(null);
                if (booking != null) {
                    Provider provider = booking.getActivitySchedule().getActivity().getBusiness().getProvider();
                    emailService.sendProviderBookingCancelled(provider.getName(), provider.getEmail(), event);
                }
            });
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundCompleted(EmailEvents.RefundCompleted event) {
        safely("reembolso realizado", () -> emailService.sendRefundCompleted(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingExpired(EmailEvents.BookingExpired event) {
        safely("reserva vencida", () -> emailService.sendBookingExpired(event));
    }

    // Al prestador dueño de la actividad cuando se confirma una reserva (el mail al turista con el QR lo manda
    // BookingConfirmationNotifier con el mismo evento)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        safely("nueva reserva para el prestador", () -> {
            Booking booking = bookingRepository.findWithProviderById(event.bookingId()).orElse(null);
            if (booking == null) {
                return;
            }
            Provider provider = booking.getActivitySchedule().getActivity().getBusiness().getProvider();
            emailService.sendProviderNewBooking(provider.getName(), provider.getEmail(), event);
        });
    }

    // Un error acá solo se registra: el correo es un aviso y no puede romper la operación que ya se confirmó
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("No se pudo mandar el correo ({}): {}", what, e.getMessage(), e);
        }
    }
}
