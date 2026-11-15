package uy.edu.um.nexo.mnatural.dto;

import java.time.LocalDateTime;

// Un horario disponible con sus lugares libres
public record ApiScheduleDTO(Long id, LocalDateTime startDateTime, int availableSpots) {
}
