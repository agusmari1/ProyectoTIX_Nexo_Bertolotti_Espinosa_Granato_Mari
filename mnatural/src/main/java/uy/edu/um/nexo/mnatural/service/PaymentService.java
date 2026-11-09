package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.GatewayRefundResult;
import uy.edu.um.nexo.mnatural.payment.PaymentCreation;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.payment.PaymentStart;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// El pago es en dos pasos:
//   1. startPayment: registra el intento en PENDING y devuelve la URL donde paga el turista;
//   2. syncPayment: cuando el turista vuelve, consulta el resultado y actualiza el intento.
// También hace los reembolsos (refund): calcula cuánto se devuelve y lo pide al servicio de pagos.
// El resultado también puede llegar por un aviso del servicio de pagos (syncByNotification), sin que vuelva el turista.
// Este servicio nunca confirma ni cancela reservas: confirmar lo hace BookingConfirmationService
// (solo con un pago APPROVED), cancelar BookingCancellationService y vencer las que no se pagaron
// BookingExpirationTask
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Máximo que una reserva puede quedar pendiente de pago, contado desde que se creó (ESPECIFICACION.md, sección 3)
    public static final int PENDING_PAYMENT_HOURS = 2;

    // Todos los pagos son en pesos uruguayos (ESPECIFICACION.md, sección 3)
    public static final String CURRENCY = "UYU";

    // Lo que se devuelve cuando el prestador suspende la actividad o el horario (ESPECIFICACION.md, sección 3)
    public static final BigDecimal FULL_REFUND_PERCENTAGE = new BigDecimal("100");

    // Un reembolso en estos estados ya está hecho (o el servicio lo está procesando): no se pide otro
    private static final List<PaymentStatus> REFUND_DONE = List.of(PaymentStatus.APPROVED, PaymentStatus.PENDING);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
                          PaymentGateway paymentGateway) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentGateway = paymentGateway;
    }

    // Abre un intento de pago nuevo para la reserva del turista.
    // Si el servicio de pagos falla, el intento queda en ERROR y se devuelve sin URL: la reserva sigue
    // PENDING y el turista puede reintentar (otro intento, con attemptNumber + 1)
    @Transactional
    public PaymentStart startPayment(Long bookingId, Long touristId) {
        Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        checkOwner(booking, touristId);
        LocalDateTime now = LocalDateTime.now();
        checkPayable(booking, now);

        // Antes de abrir otro intento se consultan los anteriores que quedaron abiertos
        // (por ejemplo, el turista cerró la página de pago): si alguno ya se cobró, no se cobra de nuevo
        for (PaymentTransaction previous : paymentTransactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.PENDING)) {
            refreshStatus(previous, now);
        }
        if (paymentTransactionRepository.existsByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.APPROVED)) {
            throw new PaymentException("Esta reserva ya tiene un pago aprobado.");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBooking(booking);
        transaction.setAmount(booking.getTotalAmount());
        transaction.setType(PaymentType.CHARGE);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setAttemptNumber((int) paymentTransactionRepository
                .countByBookingIdAndType(bookingId, PaymentType.CHARGE) + 1);

        // El turista va a estar en la página de pago: si la retención siguiera siendo la de 10 minutos,
        // la reserva podría vencer mientras paga. Desde que intenta pagar, rige el plazo de 2 horas
        extendHold(booking, now);

        try {
            PaymentCreation creation = paymentGateway.createPayment(booking, transaction.getAmount());
            transaction.setExternalReference(creation.externalReference());
            paymentTransactionRepository.save(transaction);
            return new PaymentStart(transaction, creation.paymentUrl());
        } catch (RuntimeException e) {
            // No se pudo crear el pago, así que no se cobró nada: se registra el intento como ERROR
            log.warn("No se pudo crear el pago de la reserva {} (intento {}): {}",
                    bookingId, transaction.getAttemptNumber(), e.getMessage());
            transaction.setStatus(PaymentStatus.ERROR);
            transaction.setFailureReason("No se pudo conectar con el servicio de pagos. Probá de nuevo en unos minutos.");
            paymentTransactionRepository.save(transaction);
            return new PaymentStart(transaction, null);
        }
    }

    // Consulta en el servicio de pagos cómo quedó el intento y lo actualiza.
    // Un intento que ya tiene resultado final (APPROVED, REJECTED o ERROR) no se vuelve a consultar
    @Transactional
    public PaymentTransaction syncPayment(Long transactionId, Long touristId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new PaymentException("El pago no existe."));
        return sync(transaction, touristId);
    }

    // Igual que syncPayment, a partir de la referencia con la que vuelve el turista desde la página de pago
    @Transactional
    public PaymentTransaction syncPaymentByReference(String externalReference, Long touristId) {
        if (externalReference == null || externalReference.isBlank()) {
            throw new PaymentException("No se encontró el pago.");
        }
        PaymentTransaction transaction = paymentTransactionRepository.findByExternalReference(externalReference)
                .orElseThrow(() -> new PaymentException("No se encontró el pago."));
        return sync(transaction, touristId);
    }

    // Aviso (webhook) del servicio de pagos: actualiza el intento con esa referencia consultando el resultado real.
    // No hay turista con sesión, así que no se verifica el dueño: la referencia sale de consultar el pago al servicio
    // y no de lo que traiga el aviso. Es idempotente (un intento ya aprobado no se vuelve a tocar).
    // Devuelve el id de la reserva, o vacío si la referencia no corresponde a ningún intento nuestro
    @Transactional
    public Optional<Long> syncByNotification(String externalReference) {
        Optional<PaymentTransaction> found = paymentTransactionRepository.findByExternalReference(externalReference);
        if (found.isEmpty()) {
            log.warn("Aviso de pago con una referencia desconocida: {}", externalReference);
            return Optional.empty();
        }
        PaymentTransaction transaction = found.get();
        Long bookingId = transaction.getBooking().getId();
        // Se bloquea la reserva, igual que al volver el turista, para no pisarse con otro pedido
        paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        refreshStatus(transaction, LocalDateTime.now());
        return Optional.of(bookingId);
    }

    // Una reserva VENCIDA con un pago aprobado (el turista pagó cuando la retención ya había terminado) no puede
    // quedarse con el dinero: se reembolsa el 100 %. Si el servicio de pagos falla, el reembolso queda en ERROR y
    // RefundRetryTask lo reintenta. Solo se aplica a reservas vencidas, que nunca se confirmaron: una reserva
    // cancelada por el turista con una política sin reembolso también queda cobrada, y ahí no se devuelve nada.
    // No hace nada si la reserva no está vencida, no tiene pago aprobado o ya tiene un reembolso.
    // Devuelve true si pidió el reembolso
    @Transactional
    public boolean refundIfPaidButNotConfirmed(Long bookingId) {
        Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        if (booking.getStatus() != BookingStatus.EXPIRED || !hasApprovedCharge(bookingId)
                || paymentTransactionRepository.countByBookingIdAndType(bookingId, PaymentType.REFUND) > 0) {
            return false;
        }
        PaymentTransaction charge = approvedCharge(bookingId).orElseThrow();
        log.warn("La reserva {} está {} y tiene un pago aprobado: se reembolsa", bookingId, booking.getStatus());
        requestRefund(booking, charge, charge.getAmount(), true);
        return true;
    }

    // Consulta los intentos que siguen en proceso. Lo usa la pantalla de la reserva: si el turista pagó pero
    // la vuelta desde la página de pago no funcionó (por ejemplo, sin auto_return en la compu local),
    // al entrar o recargar la pantalla se entera igual del resultado
    @Transactional
    public void syncPendingPayments(Long bookingId, Long touristId) {
        Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        checkOwner(booking, touristId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransaction pending : paymentTransactionRepository.findByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.PENDING)) {
            refreshStatus(pending, now);
        }
    }

    // Resumen de la reserva y sus intentos de pago, para la pantalla del turista
    @Transactional(readOnly = true)
    public BookingPaymentDTO getBookingPayment(Long bookingId, Long touristId) {
        Booking booking = paymentTransactionRepository.findBookingById(bookingId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        checkOwner(booking, touristId);
        List<PaymentTransaction> attempts = paymentTransactionRepository
                .findByBookingIdAndTypeOrderByAttemptNumberAsc(bookingId, PaymentType.CHARGE);
        boolean alreadyPaid = attempts.stream().anyMatch(t -> t.getStatus() == PaymentStatus.APPROVED);
        LocalDateTime now = LocalDateTime.now();
        boolean payable = !alreadyPaid && isPayable(booking, now);
        List<PaymentTransaction> refunds = paymentTransactionRepository
                .findByBookingIdAndTypeOrderByAttemptNumberAsc(bookingId, PaymentType.REFUND);

        // Solo una reserva confirmada (pagada) se cancela desde acá; las pendientes vencen solas
        boolean cancellable = booking.getStatus() == BookingStatus.CONFIRMED && !isRefunded(bookingId);
        String blockedReason = null;
        BigDecimal refundIfCancelled = null;
        if (cancellable) {
            try {
                BigDecimal percentage = touristRefundPercentage(booking, now);
                BigDecimal charged = attempts.stream().filter(t -> t.getStatus() == PaymentStatus.APPROVED)
                        .map(PaymentTransaction::getAmount).findFirst().orElse(BigDecimal.ZERO);
                refundIfCancelled = refundAmount(charged, percentage);
            } catch (PaymentException e) {
                blockedReason = e.getMessage();
            }
        }
        return new BookingPaymentDTO(booking, attempts, payable, refunds, cancellable, blockedReason, refundIfCancelled);
    }

    // ===== Reembolsos =====

    // Política de cancelación que definió el prestador para la actividad (ESPECIFICACION.md, sección 3).
    // Devuelve el porcentaje que le corresponde al turista si cancela en este momento (puede ser 0).
    // Si la actividad no admite cancelación o ya pasó el plazo, tira PaymentException explicando por qué
    public BigDecimal touristRefundPercentage(Booking booking, LocalDateTime now) {
        ActivitySchedule schedule = booking.getActivitySchedule();
        Activity activity = schedule.getActivity();
        if (!activity.isCancellationAllowed()) {
            throw new PaymentException("Esta actividad no admite cancelaciones: el prestador definió que las "
                    + "reservas no se cancelan ni se reembolsan.");
        }
        int cutoffHours = activity.getCancellationCutoffHours() == null ? 0 : activity.getCancellationCutoffHours();
        LocalDateTime deadline = schedule.getStartDateTime().minusHours(cutoffHours);
        if (!now.isBefore(deadline)) {
            throw new PaymentException(cutoffHours == 0
                    ? "La actividad ya comenzó: la reserva ya no se puede cancelar ni reembolsar."
                    : "Ya pasó el plazo para cancelar: esta actividad admite cancelaciones hasta " + cutoffHours
                    + " horas antes del inicio. La reserva ya no se puede cancelar ni reembolsar.");
        }
        return activity.getRefundPercentage() == null ? BigDecimal.ZERO : activity.getRefundPercentage();
    }

    // Monto a devolver en pesos, redondeado a centésimos. Ejemplo: 50 % de 3000,00 es 1500,00
    public static BigDecimal refundAmount(BigDecimal charged, BigDecimal percentage) {
        return charged.multiply(percentage).divide(FULL_REFUND_PERCENTAGE, 2, RoundingMode.HALF_UP);
    }

    // Devuelve ese porcentaje del cobro aprobado de la reserva: crea un PaymentTransaction REFUND enlazado
    // al cobro (originalPayment) y se lo pide al servicio de pagos.
    // Si el servicio falla, el reembolso queda en ERROR (no se da por hecho) y se devuelve sin tirar excepción,
    // así queda registrado el intento. Quien llama decide qué hacer con la reserva según isDone
    @Transactional
    public PaymentTransaction refund(Booking booking, BigDecimal percentage) {
        if (percentage == null || percentage.signum() <= 0 || percentage.compareTo(FULL_REFUND_PERCENTAGE) > 0) {
            throw new PaymentException("El porcentaje a reembolsar tiene que ser mayor a 0 y como máximo 100.");
        }
        PaymentTransaction charge = approvedCharge(booking.getId())
                .orElseThrow(() -> new PaymentException("Esta reserva no tiene un pago aprobado para reembolsar."));
        if (isRefunded(booking.getId())) {
            throw new PaymentException("Esta reserva ya fue reembolsada.");
        }
        return requestRefund(booking, charge, refundAmount(charge.getAmount(), percentage),
                percentage.compareTo(FULL_REFUND_PERCENTAGE) == 0);
    }

    // Red de seguridad: reembolsa las reservas vencidas que tienen un pago aprobado y ningún reembolso (por ejemplo,
    // la reserva venció justo después de que el turista pagó y nadie volvió a mirarla). Lo llama RefundRetryTask.
    // Devuelve cuántos reembolsos pidió
    @Transactional
    public int refundPaidExpiredBookings() {
        int requested = 0;
        for (Long bookingId : paymentTransactionRepository.findExpiredPaidBookingIdsWithoutRefund()) {
            if (refundIfPaidButNotConfirmed(bookingId)) {
                requested++;
            }
        }
        return requested;
    }

    // Reintenta los reembolsos que fallaron en reservas ya canceladas o vencidas (por ejemplo, el prestador
    // suspendió el horario, o se cobró una reserva vencida, mientras el servicio de pagos no respondía).
    // Se pide el mismo monto que el intento anterior. Devuelve cuántos se completaron. Lo llama RefundRetryTask
    @Transactional
    public int retryFailedRefunds() {
        int done = 0;
        for (Long bookingId : paymentTransactionRepository.findBookingIdsWithFailedRefunds()) {
            // Se bloquea la reserva y se vuelve a verificar, por si otro pedido lo reintentó al mismo tiempo
            Booking booking = paymentTransactionRepository.findBookingForUpdate(bookingId).orElse(null);
            Optional<PaymentTransaction> charge = approvedCharge(bookingId);
            Optional<PaymentTransaction> last = paymentTransactionRepository
                    .findFirstByBookingIdAndTypeOrderByAttemptNumberDesc(bookingId, PaymentType.REFUND);
            if (booking == null || charge.isEmpty() || last.isEmpty() || isRefunded(bookingId)) {
                continue;
            }
            BigDecimal amount = last.get().getAmount();
            boolean fullRefund = amount.compareTo(charge.get().getAmount()) == 0;
            if (isDone(requestRefund(booking, charge.get(), amount, fullRefund))) {
                done++;
            }
        }
        return done;
    }

    public boolean hasApprovedCharge(Long bookingId) {
        return paymentTransactionRepository.existsByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.APPROVED);
    }

    // true si la reserva ya tiene un reembolso aprobado o en proceso
    public boolean isRefunded(Long bookingId) {
        return paymentTransactionRepository.existsByBookingIdAndTypeAndStatusIn(
                bookingId, PaymentType.REFUND, REFUND_DONE);
    }

    // Un reembolso aprobado o en proceso cuenta como hecho; REJECTED o ERROR no
    public static boolean isDone(PaymentTransaction refund) {
        return refund != null && REFUND_DONE.contains(refund.getStatus());
    }

    private Optional<PaymentTransaction> approvedCharge(Long bookingId) {
        return paymentTransactionRepository.findFirstByBookingIdAndTypeAndStatus(
                bookingId, PaymentType.CHARGE, PaymentStatus.APPROVED);
    }

    private PaymentTransaction requestRefund(Booking booking, PaymentTransaction charge, BigDecimal amount,
                                             boolean fullRefund) {
        PaymentTransaction refund = new PaymentTransaction();
        refund.setBooking(booking);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPayment(charge);
        refund.setAmount(amount);
        refund.setStatus(PaymentStatus.PENDING);
        refund.setAttemptNumber((int) paymentTransactionRepository
                .countByBookingIdAndType(booking.getId(), PaymentType.REFUND) + 1);
        try {
            GatewayRefundResult result = paymentGateway.refund(charge.getExternalReference(), amount, fullRefund);
            refund.setStatus(result.status());
            refund.setExternalReference(result.refundId());
            refund.setFailureReason(result.failureReason());
        } catch (RuntimeException e) {
            // No se sabe si el servicio llegó a devolver el dinero: queda ERROR y el reintento lo verifica
            // (Mercado Pago no devuelve de nuevo lo que ya figura reembolsado)
            log.warn("No se pudo reembolsar el pago {} de la reserva {} (intento {}): {}",
                    charge.getExternalReference(), booking.getId(), refund.getAttemptNumber(), e.getMessage());
            refund.setStatus(PaymentStatus.ERROR);
            refund.setFailureReason("No se pudo conectar con el servicio de pagos para hacer el reembolso.");
        }
        log.info("Reembolso de la reserva {}: {} {} ({}), estado {}", booking.getId(), amount, CURRENCY,
                fullRefund ? "total" : "parcial", refund.getStatus());
        return paymentTransactionRepository.save(refund);
    }

    private PaymentTransaction sync(PaymentTransaction transaction, Long touristId) {
        Booking booking = paymentTransactionRepository.findBookingForUpdate(transaction.getBooking().getId())
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        checkOwner(booking, touristId);
        refreshStatus(transaction, LocalDateTime.now());
        return transaction;
    }

    // Se consulta un intento PENDING y también uno REJECTED: dentro de la misma página de pago el turista puede
    // reintentar después de un rechazo y que esta vez se apruebe, y ese pago tiene la misma referencia.
    // APPROVED y ERROR son finales
    private void refreshStatus(PaymentTransaction transaction, LocalDateTime now) {
        boolean open = transaction.getStatus() == PaymentStatus.PENDING
                || transaction.getStatus() == PaymentStatus.REJECTED;
        if (!open || transaction.getExternalReference() == null) {
            return;
        }
        Booking booking = transaction.getBooking();
        GatewayPaymentStatus result;
        try {
            result = paymentGateway.getPaymentStatus(transaction.getExternalReference());
        } catch (RuntimeException e) {
            // No se pasa a ERROR: el pago pudo haberse acreditado igual y ERROR es un estado final.
            // Queda PENDING para volver a consultarlo
            log.warn("No se pudo consultar el pago {} de la reserva {}: {}",
                    transaction.getExternalReference(), booking.getId(), e.getMessage());
            transaction.setFailureReason("No se pudo consultar el estado del pago. Volvé a intentar en unos minutos.");
            extendHold(booking, now);
            return;
        }

        PaymentStatus status = result.status();
        String failureReason = status == PaymentStatus.APPROVED ? null : result.failureReason();
        // Un aprobado por otro monto o en otra moneda es un resultado inesperado: no se da por pagado
        if (status == PaymentStatus.APPROVED && !matchesCharge(result, transaction)) {
            log.warn("El pago {} de la reserva {} se aprobó por {} {} y se esperaban {} {}",
                    transaction.getExternalReference(), booking.getId(), result.amount(), result.currency(),
                    transaction.getAmount(), CURRENCY);
            status = PaymentStatus.ERROR;
            failureReason = "El monto cobrado no coincide con el de la reserva. Contactanos para revisarlo.";
        }
        transaction.setStatus(status);
        transaction.setFailureReason(failureReason);
        if (result.cardBrand() != null) {
            transaction.setCardBrand(result.cardBrand());
        }
        if (result.cardLastFour() != null) {
            transaction.setCardLastFour(result.cardLastFour());
        }

        if (status != PaymentStatus.APPROVED) {
            extendHold(booking, now);
        } else if (booking.getStatus() != BookingStatus.PENDING) {
            // Se cobró una reserva que ya venció o se canceló: no se confirma y habrá que reembolsarla
            log.warn("Pago {} aprobado para la reserva {}, que está {}: requiere reembolso",
                    transaction.getExternalReference(), booking.getId(), booking.getStatus());
        }
    }

    // C. Agustina dejó pedido en BookingService.createBooking que, si el pago no se completa en el momento,
    // el pago extienda heldUntil hasta 2 horas o hasta el límite de reserva de la actividad, lo que llegue
    // primero. Sin esto, BookingExpirationTask vence la reserva a los 10 minutos y el turista no puede reintentar.
    // Las 2 horas se cuentan desde que se creó la reserva (la letra dice "plazo improrrogable"), así reintentar
    // no lo alarga. Solo se usa el setter público de Booking: no se cancela nada ni se toca la tarea de vencimiento
    private void extendHold(Booking booking, LocalDateTime now) {
        // Una reserva que ya no está pendiente, o cuya retención ya venció, no se revive
        if (booking.getStatus() != BookingStatus.PENDING
                || booking.getHeldUntil() == null || !now.isBefore(booking.getHeldUntil())) {
            return;
        }
        LocalDateTime limit = booking.getCreatedAt().plusHours(PENDING_PAYMENT_HOURS);
        ActivitySchedule schedule = booking.getActivitySchedule();
        LocalDateTime bookingDeadline = schedule.getStartDateTime()
                .minusMinutes(schedule.getActivity().getBookingCutoffMinutes());
        if (bookingDeadline.isBefore(limit)) {
            limit = bookingDeadline;
        }
        // Nunca se acorta una retención
        if (limit.isAfter(booking.getHeldUntil())) {
            booking.setHeldUntil(limit);
        }
    }

    // Si el servicio no informa monto o moneda (el simulado), no hay con qué comparar
    private boolean matchesCharge(GatewayPaymentStatus result, PaymentTransaction transaction) {
        boolean amountOk = result.amount() == null || result.amount().compareTo(transaction.getAmount()) == 0;
        boolean currencyOk = result.currency() == null || CURRENCY.equals(result.currency());
        return amountOk && currencyOk;
    }

    private void checkOwner(Booking booking, Long touristId) {
        if (touristId == null || !Objects.equals(booking.getTourist().getId(), touristId)) {
            throw new PaymentException("La reserva no existe.");
        }
    }

    private void checkPayable(Booking booking, LocalDateTime now) {
        switch (booking.getStatus()) {
            case CONFIRMED -> throw new PaymentException("Esta reserva ya está confirmada.");
            case CANCELLED -> throw new PaymentException("Esta reserva fue cancelada.");
            case EXPIRED -> throw new PaymentException("Venció el plazo para pagar esta reserva.");
            case PENDING -> {
            }
        }
        if (booking.getHeldUntil() == null || !now.isBefore(booking.getHeldUntil())) {
            throw new PaymentException("Venció el plazo para pagar esta reserva.");
        }
        if (booking.getTotalAmount() == null || booking.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("Esta reserva no tiene un monto para pagar.");
        }
    }

    // Lo mismo que checkPayable, sin mensajes: para mostrar u ocultar el botón de pagar
    private boolean isPayable(Booking booking, LocalDateTime now) {
        try {
            checkPayable(booking, now);
            return true;
        } catch (PaymentException e) {
            return false;
        }
    }
}
