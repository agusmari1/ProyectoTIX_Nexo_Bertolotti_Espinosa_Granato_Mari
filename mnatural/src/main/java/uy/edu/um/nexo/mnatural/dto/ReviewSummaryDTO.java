package uy.edu.um.nexo.mnatural.dto;

// Promedio y cantidad de valoraciones vigentes de una actividad (las eliminadas no cuentan)
public record ReviewSummaryDTO(Long activityId, Double average, Long count) {
}
