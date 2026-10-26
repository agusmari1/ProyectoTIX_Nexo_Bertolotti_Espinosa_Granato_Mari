package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Cada minuto pasa a EXPIRED las reservas pendientes cuya retención venció.
// Los cupos ya dejan de contarse apenas vence heldUntil (ver BookingRepository.countTakenSpots);
// esta tarea deja el estado de la reserva al día para el historial del turista
@Component
public class BookingExpirationTask {

    private static final Logger log = LoggerFactory.getLogger(BookingExpirationTask.class);

    private final BookingService bookingService;

    public BookingExpirationTask(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedRate = 60_000)
    public void expireHeldBookings() {
        int expired = bookingService.expireHeldBookings();
        if (expired > 0) {
            log.info("Vencieron {} reservas pendientes y se liberaron sus cupos", expired);
        }
    }
}
