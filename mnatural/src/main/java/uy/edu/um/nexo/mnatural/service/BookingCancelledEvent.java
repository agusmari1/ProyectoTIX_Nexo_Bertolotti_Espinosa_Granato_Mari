package uy.edu.um.nexo.mnatural.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Se publica cuando BookingCancellationService cancela una reserva. Trae copiados los datos del mail,
// así quien lo recibe no necesita volver a leer la base.
//   - refundAmount: lo que se devuelve (null si no corresponde reembolso);
//   - refundCompleted: true si el servicio de pagos ya lo aprobó; false si está en proceso o se va a
//     reintentar (RefundRetryTask)
public record BookingCancelledEvent(Long bookingId, String touristEmail, String touristName, String bookingCode,
                                    String activityName, LocalDateTime startDateTime, Reason reason,
                                    BigDecimal refundAmount, boolean refundCompleted) {

    public enum Reason {
        // El turista canceló su reserva
        TOURIST_CANCELLED,
        // El prestador suspendió la fecha y horario
        SCHEDULE_SUSPENDED
    }
}
