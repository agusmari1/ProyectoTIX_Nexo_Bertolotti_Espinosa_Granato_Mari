package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Lo que ve el turista en la pantalla de pago de su reserva: el resumen, los intentos y si puede pagar
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

    public BookingPaymentDTO(Booking booking, List<PaymentTransaction> attempts, boolean payable) {
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
}
