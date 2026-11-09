package uy.edu.um.nexo.mnatural.dto;

// Una actividad del top 3 del mes con su valoración promedio y su cantidad de valoraciones
public record ApiTopActivityDTO(ApiActivityDTO activity, double averageRating, long ratingCount) {
}
