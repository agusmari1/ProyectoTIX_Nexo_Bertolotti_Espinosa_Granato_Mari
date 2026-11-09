package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

// Pasa reservas a CANCELLED y pide el reembolso que corresponde (ESPECIFICACION.md, sección 3):
//   - el turista cancela: según la política de la actividad (si admite cancelación, plazo y porcentaje);
//   - el prestador suspende el horario: 100 % de lo pagado, y las pendientes se cancelan sin cobro.
// Está separado de BookingService (de Agustina) para no modificarlo: solo usa los setters públicos de Booking,
// igual que BookingConfirmationService. Una reserva cancelada deja de ocupar cupos
// (BookingRepository.countTakenSpots solo cuenta confirmadas y pendientes)
@Service
public class BookingCancellationService {

    private static final Logger log = LoggerFactory.getLogger(BookingCancellationService.class);
    // Formato uruguayo: 1.500,00
    private static final Locale PESOS = Locale.forLanguageTag("es-UY");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingCancellationService(PaymentTransactionRepository paymentTransactionRepository,
                                      PaymentService paymentService,
                                      ApplicationEventPublisher eventPublisher) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
    }

    // El turista cancela su reserva confirmada.
    // Si la actividad no admite cancelación o ya pasó el plazo, tira PaymentException con el motivo y no cambia nada.
    // Si el reembolso falla, la reserva NO se cancela: sigue confirmada y el turista puede volver a intentar
    @Transactional
    public CancellationResult cancelByTourist(Long bookingId, Long touristId) {
        // Se bloquea la reserva: dos pedidos de cancelar al mismo tiempo no reembolsan dos veces
        Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        if (touristId == null || !Objects.equals(booking.getTourist().getId(), touristId)) {
            throw new PaymentException("La reserva no existe.");
        }
        switch (booking.getStatus()) {
            case CANCELLED -> throw new PaymentException("Esta reserva ya fue cancelada.");
            case EXPIRED -> throw new PaymentException("Esta reserva venció sin pagarse: no hay nada que cancelar.");
            case PENDING -> throw new PaymentException("Esta reserva todavía no está pagada. Si no la pagás, "
                    + "se cancela sola al vencer el plazo y los lugares se liberan.");
            case CONFIRMED -> {
            }
        }
        if (paymentService.isRefunded(bookingId)) {
            throw new PaymentException("Esta reserva ya fue reembolsada.");
        }

        BigDecimal percentage = paymentService.touristRefundPercentage(booking, LocalDateTime.now());
        PaymentTransaction refund = null;
        if (percentage.signum() > 0) {
            refund = paymentService.refund(booking, percentage);
            if (!PaymentService.isDone(refund)) {
                return new CancellationResult(false, refund, "No pudimos procesar el reembolso con el servicio "
                        + "de pagos, así que tu reserva sigue confirmada. Probá cancelar de nuevo en unos minutos.");
            }
        }

        cancel(booking, BookingCancelledEvent.Reason.TOURIST_CANCELLED, refund);
        log.info("Reserva {} cancelada por el turista ({} % de reembolso)", bookingId, percentage);
        String message = refund == null
                ? "Cancelaste tu reserva. Según la política de la actividad, no corresponde reembolso."
                : "Cancelaste tu reserva. Se te devuelven $ " + String.format(PESOS, "%,.2f", refund.getAmount())
                + " (" + percentage.stripTrailingZeros().toPlainString() + " % de lo pagado) por el mismo medio de pago.";
        return new CancellationResult(true, refund, message);
    }

    // El prestador suspendió el horario: todas sus reservas se cancelan.
    // Las pagadas se reembolsan al 100 %; si el servicio de pagos falla, la reserva se cancela igual
    // (el horario no se va a hacer) y RefundRetryTask reintenta el reembolso hasta que se complete
    @Transactional
    public SuspensionResult cancelForSuspension(Long scheduleId) {
        List<Booking> bookings = paymentTransactionRepository.findBookingsOfScheduleForUpdate(
                scheduleId, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        int refunded = 0;
        int failed = 0;
        for (Booking booking : bookings) {
            PaymentTransaction refund = null;
            // Las pendientes normalmente no tienen cobro; si alguna ya se cobró, también se devuelve
            if (paymentService.hasApprovedCharge(booking.getId()) && !paymentService.isRefunded(booking.getId())) {
                refund = paymentService.refund(booking, PaymentService.FULL_REFUND_PERCENTAGE);
                if (PaymentService.isDone(refund)) {
                    refunded++;
                } else {
                    failed++;
                }
            }
            cancel(booking, BookingCancelledEvent.Reason.SCHEDULE_SUSPENDED, refund);
        }
        log.info("Horario {} suspendido: {} reservas canceladas, {} reembolsadas, {} reembolsos para reintentar",
                scheduleId, bookings.size(), refunded, failed);
        return new SuspensionResult(bookings.size(), refunded, failed);
    }

    // refund: el reembolso pedido (null si no correspondía). El mail al turista lo manda
    // BookingCancellationNotifier recién cuando esta transacción se guarda
    private void cancel(Booking booking, BookingCancelledEvent.Reason reason, PaymentTransaction refund) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setHeldUntil(null);
        ActivitySchedule schedule = booking.getActivitySchedule();
        eventPublisher.publishEvent(new BookingCancelledEvent(booking.getId(), booking.getTourist().getEmail(),
                booking.getTourist().getName(), booking.getBookingCode(), schedule.getActivity().getName(),
                schedule.getStartDateTime(), reason, refund == null ? null : refund.getAmount(),
                refund != null && refund.getStatus() == PaymentStatus.APPROVED));
    }
}
