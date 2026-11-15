package uy.edu.um.nexo.mnatural.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.repository.BookingNotificationRepository;

import java.time.LocalDateTime;
import java.util.List;

// Avisa a cada turista cuya reserva venció por falta de pago. BookingService vence las reservas con un solo UPDATE
// que no dice cuáles fueron, así que BookingExpirationTask usa esta clase en dos pasos:
//   1. antes de vencer: findExpiring() anota las reservas pendientes que están por vencer (con un margen de unos
//      segundos, por el tiempo entre una consulta y la otra);
//   2. después: publishExpired() publica el evento SOLO de las que quedaron realmente vencidas. Una reserva que se
//      pagó y confirmó en el medio no vence, así que no recibe el aviso; y como una vez vencida deja de estar
//      pendiente, no se vuelve a anotar y no se avisa dos veces
@Component
public class BookingExpiryNotifier {

    // Margen para no perder una reserva que vence entre el paso 1 y el UPDATE
    static final long MARGIN_SECONDS = 30;

    private final BookingNotificationRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BookingExpiryNotifier(BookingNotificationRepository bookingRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiring() {
        return bookingRepository.findIdsHeldUntilBefore(LocalDateTime.now().plusSeconds(MARGIN_SECONDS));
    }

    // Publica un evento por cada una (el correo sale cuando esta transacción se confirma)
    @Transactional
    public int publishExpired(List<Long> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return 0;
        }
        List<Booking> expired = bookingRepository.findExpiredByIds(candidateIds);
        for (Booking booking : expired) {
            var schedule = booking.getActivitySchedule();
            eventPublisher.publishEvent(new EmailEvents.BookingExpired(booking.getId(),
                    booking.getTourist().getEmail(), booking.getTourist().getName(), booking.getBookingCode(),
                    schedule.getActivity().getName(), schedule.getStartDateTime()));
        }
        return expired.size();
    }
}
