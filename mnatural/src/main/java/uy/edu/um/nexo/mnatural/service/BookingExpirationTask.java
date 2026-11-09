package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Cada minuto pasa a EXPIRED las reservas pendientes cuya retención venció.
// Los cupos ya dejan de contarse apenas vence heldUntil (ver BookingRepository.countTakenSpots);
// esta tarea deja el estado de la reserva al día para el historial del turista
@Component
public class BookingExpirationTask {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationTask.class);

    private final BookingService bookingService;
    private final BookingExpiryNotifier expiryNotifier;

    public BookingExpirationTask(BookingService bookingService, BookingExpiryNotifier expiryNotifier) {
        this.bookingService = bookingService;
        this.expiryNotifier = expiryNotifier;
    }

    @Scheduled(fixedRate = 60_000)
    public void expireHeldBookings() {
        // Se anotan las que están por vencer para poder avisarle a cada turista (ver BookingExpiryNotifier)
        List<Long> expiring = expiryNotifier.findExpiring();
        int expired = bookingService.expireHeldBookings();
        if (expired > 0) {
            log.info("Vencieron {} reservas pendientes y se liberaron sus cupos", expired);
            try {
                expiryNotifier.publishExpired(expiring);
            } catch (RuntimeException e) {
                // Las reservas ya vencieron: si no se puede avisar, solo se registra
                log.error("No se pudo avisar el vencimiento de las reservas: {}", e.getMessage(), e);
            }
        }
    }
}
