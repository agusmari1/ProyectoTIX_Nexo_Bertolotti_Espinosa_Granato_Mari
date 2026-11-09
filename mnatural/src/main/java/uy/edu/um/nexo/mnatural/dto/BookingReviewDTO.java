package uy.edu.um.nexo.mnatural.dto;

import java.time.LocalDateTime;

// Lo que ve el turista en la pantalla de calificar una reserva:
//   - review: su reseña, si ya la dejó (null si no la dejó o si la eliminó);
//   - canReview: si puede dejarla ahora;
//   - blockedReason: por qué no puede, cuando canReview es false y todavía no tiene reseña
public record BookingReviewDTO(Long bookingId, Long activityId, String activityName, LocalDateTime startDateTime,
                               ReviewViewDTO review, boolean canReview, String blockedReason) {
}
