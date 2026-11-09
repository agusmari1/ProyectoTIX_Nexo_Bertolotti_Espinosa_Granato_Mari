package uy.edu.um.nexo.mnatural.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Lo que ve el turista antes de reservar una fecha. blockedReason dice por qué no se puede
// reservar (sin lugares, fuera de plazo, horario suspendido) y es null si se puede
public record BookingSummaryDTO(Long scheduleId, Long activityId, String activityName, String businessName,
                                String locality, LocalDateTime startDateTime, int durationMinutes,
                                BigDecimal pricePerPerson, Integer minAge, int availableSpots, int holdMinutes,
                                String blockedReason) {

    public boolean isBookable() {
        return blockedReason == null;
    }
}
