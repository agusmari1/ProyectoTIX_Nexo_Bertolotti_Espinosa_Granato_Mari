package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;

// Pasa una reserva a CONFIRMED cuando tiene un cobro aprobado.
// Está separado de BookingService (de Agustina) para no modificarlo: solo usa los setters públicos de Booking.
// Se puede llamar más de una vez sobre la misma reserva: si ya está confirmada, no cambia nada
@Service
public class BookingConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationService.class);

    // Mismo formato que BookingService (MN-XXXXXXXX): sin 0/O ni 1/I para que no se confundan
    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    public BookingConfirmationService(PaymentTransactionRepository paymentTransactionRepository,
                                      BookingRepository bookingRepository,
                                      ApplicationEventPublisher eventPublisher) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
    }

    // Devuelve true si la reserva queda confirmada
    @Transactional
    public boolean confirmIfPaid(Long bookingId) {
        // Se bloquea la reserva para que BookingExpirationTask no la venza mientras se confirma
        Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return true;
        }
        // Nunca se confirma una reserva sin un cobro APPROVED registrado
        if (!paymentTransactionRepository.existsByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.APPROVED)) {
            return false;
        }
        // Si la retención ya venció, los cupos pudieron quedar para otro turista: no se confirma
        LocalDateTime now = LocalDateTime.now();
        if (booking.getStatus() != BookingStatus.PENDING
                || booking.getHeldUntil() == null || !now.isBefore(booking.getHeldUntil())) {
            log.warn("La reserva {} tiene un pago aprobado pero está {} o venció su retención: requiere reembolso",
                    bookingId, booking.getStatus());
            return false;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        // Una reserva confirmada ocupa sus cupos sin vencimiento (ver BookingRepository.countTakenSpots)
        booking.setHeldUntil(null);
        // B. BookingService (Agustina) ya genera el código de reserva al crear la reserva, así que se usa ese
        // para el QR y el mail. Solo se genera uno nuevo, con el mismo formato, si por algún motivo viniera vacío
        if (booking.getBookingCode() == null || booking.getBookingCode().isBlank()) {
            booking.setBookingCode(generateUniqueCode());
        }
        log.info("Reserva {} confirmada con el código {}", bookingId, booking.getBookingCode());
        // BookingConfirmationNotifier genera el QR y manda el mail recién cuando esta transacción se guarda
        eventPublisher.publishEvent(new BookingConfirmedEvent(booking.getId(), booking.getTourist().getEmail(),
                booking.getTourist().getName(), booking.getBookingCode(),
                booking.getActivitySchedule().getActivity().getName(), booking.getActivitySchedule().getStartDateTime(),
                booking.getParticipantCount(), booking.getTotalAmount()));
        return true;
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder("MN-");
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
            }
            code = builder.toString();
        } while (bookingRepository.existsByBookingCode(code));
        return code;
    }
}
