package uy.edu.um.nexo.mnatural.dto;

import java.util.List;

// Valoraciones de una actividad para su página pública: el promedio y la cantidad (solo reseñas visibles) y la lista.
// average es null si todavía no hay ninguna
public record ReviewSummaryDTO(Double average, long count, List<ReviewDTO> reviews) {

    public boolean hasReviews() {
        return count > 0;
    }

    // Promedio con una coma decimal, como se escribe en Uruguay: "4,5"
    public String averageText() {
        return average == null ? "-" : String.format(java.util.Locale.forLanguageTag("es-UY"), "%.1f", average);
    }

    // El promedio redondeado al entero más cercano, para pintar las estrellas
    public String stars() {
        int rounded = average == null ? 0 : (int) Math.round(average);
        return "★".repeat(rounded) + "☆".repeat(Math.max(0, 5 - rounded));
    }
}
