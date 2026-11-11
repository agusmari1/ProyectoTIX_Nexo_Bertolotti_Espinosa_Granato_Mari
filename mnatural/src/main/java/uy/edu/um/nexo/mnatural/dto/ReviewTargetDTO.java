package uy.edu.um.nexo.mnatural.dto;

import java.time.LocalDateTime;

// Qué actividad se está reseñando (para la pantalla de reseña del turista)
public record ReviewTargetDTO(Long bookingId, Long activityId, String activityName, LocalDateTime startDateTime,
                              String bookingCode) {
}
