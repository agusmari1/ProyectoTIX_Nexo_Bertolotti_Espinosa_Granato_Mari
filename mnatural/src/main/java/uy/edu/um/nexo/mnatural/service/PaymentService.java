package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.payment.GatewayPaymentStatus;
import uy.edu.um.nexo.mnatural.payment.PaymentCreation;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;
import uy.edu.um.nexo.mnatural.payment.PaymentStart;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

// El pago es en dos pasos:
//   1. startPayment: registra el intento en PENDING y devuelve la URL donde paga el turista;
//   2. syncPayment: cuando el turista vuelve, consulta el resultado y actualiza el intento.
// Este servicio nunca confirma ni cancela reservas: confirmar lo hace BookingConfirmationService
// (solo con un pago APPROVED) y vencer las que no se pagaron lo hace BookingExpirationTask
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Máximo que una reserva puede quedar pendiente de pago, contado desde que se creó (ESPECIFICACION.md, sección 3)
    public static final int PENDING_PAYMENT_HOURS = 2;

    // Todos los pagos son en pesos uruguayos (ESPECIFICACION.md, sección 3)
    public static final String CURRENCY = "UYU";

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
        boolean payable = !alreadyPaid && isPayable(booking, LocalDateTime.now());
        return new BookingPaymentDTO(booking, attempts, payable);
    }

    private PaymentTransaction sync(PaymentTransaction transaction, Long touristId) {
        Booking booking = paymentTransactionRepository.findBookingForUpdate(transaction.getBooking().getId())
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
        checkOwner(booking, touristId);
        refreshStatus(transaction, LocalDateTime.now());
        return transaction;
    }

    private void refreshStatus(PaymentTransaction transaction, LocalDateTime now) {
        if (transaction.getStatus() != PaymentStatus.PENDING || transaction.getExternalReference() == null) {
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
