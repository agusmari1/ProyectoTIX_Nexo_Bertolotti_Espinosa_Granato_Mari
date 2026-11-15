package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Una fila de "Mis reservas". bookingCode solo viene si está confirmada, y reviewable indica
// que está confirmada y la actividad ya terminó, así que se puede calificar
public record TouristBookingDTO(Long bookingId, String activityName, LocalDateTime startDateTime,
                                int participantCount, BigDecimal totalAmount, BookingStatus status,
                                LocalDateTime heldUntil, String bookingCode, boolean reviewable) {
}
