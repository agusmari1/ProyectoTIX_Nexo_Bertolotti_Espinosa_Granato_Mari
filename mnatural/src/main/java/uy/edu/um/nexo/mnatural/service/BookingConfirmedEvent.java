package uy.edu.um.nexo.mnatural.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Se publica cuando BookingConfirmationService confirma una reserva.
// Trae copiados los datos del mail, así quien lo recibe no necesita volver a leer la base
public record BookingConfirmedEvent(Long bookingId, String touristEmail, String touristName,
                                    String bookingCode, String activityName, LocalDateTime startDateTime,
                                    int participantCount, BigDecimal totalAmount) {
}
