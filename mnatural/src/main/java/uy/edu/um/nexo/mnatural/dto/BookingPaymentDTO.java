package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Lo que ve el turista en la pantalla de pago de su reserva: el resumen, los intentos, si puede pagar
// y, si está confirmada, si la puede cancelar y cuánto se le devolvería
public class BookingPaymentDTO {

    private final Long bookingId;
    private final String activityName;
    private final LocalDateTime startDateTime;
    private final int participantCount;
    private final BigDecimal totalAmount;
    private final BookingStatus status;
    private final String bookingCode;
    private final LocalDateTime heldUntil;
    private final List<PaymentTransaction> attempts;
    private final boolean payable;
    private final List<PaymentTransaction> refunds;
    private final boolean cancellable;
    // Por qué no se puede cancelar según la política del prestador (null si se puede)
    private final String cancellationBlockedReason;
    // Lo que se le devolvería si cancela ahora (null si no puede cancelar)
    private final BigDecimal refundIfCancelled;

    public BookingPaymentDTO(Booking booking, List<PaymentTransaction> attempts, boolean payable,
                             List<PaymentTransaction> refunds, boolean cancellable,
                             String cancellationBlockedReason, BigDecimal refundIfCancelled) {
        this.bookingId = booking.getId();
        this.activityName = booking.getActivitySchedule().getActivity().getName();
        this.startDateTime = booking.getActivitySchedule().getStartDateTime();
        this.participantCount = booking.getParticipantCount();
        this.totalAmount = booking.getTotalAmount();
        this.status = booking.getStatus();
        // El código existe desde que se crea la reserva, pero solo vale (y se muestra) una vez confirmada
        this.bookingCode = booking.getStatus() == BookingStatus.CONFIRMED ? booking.getBookingCode() : null;
        this.heldUntil = booking.getHeldUntil();
        this.attempts = attempts;
        this.payable = payable;
        this.refunds = refunds;
        this.cancellable = cancellable;
        this.cancellationBlockedReason = cancellationBlockedReason;
        this.refundIfCancelled = refundIfCancelled;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public String getActivityName() {
        return activityName;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public LocalDateTime getHeldUntil() {
        return heldUntil;
    }

    public List<PaymentTransaction> getAttempts() {
        return attempts;
    }

    public boolean isPayable() {
        return payable;
    }

    public List<PaymentTransaction> getRefunds() {
        return refunds;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public String getCancellationBlockedReason() {
        return cancellationBlockedReason;
    }

    public BigDecimal getRefundIfCancelled() {
        return refundIfCancelled;
    }
}
