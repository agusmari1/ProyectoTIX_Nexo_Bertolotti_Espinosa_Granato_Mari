package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Review;

import java.time.LocalDateTime;

// Una reseña tal como se muestra en la página pública de la actividad. authorName es solo el nombre y la inicial del
// apellido ("Ana P."): ningún dato de contacto ni documento
public record ReviewDTO(Long id, String authorName, int rating, String comment, LocalDateTime createdAt,
                        String providerReply, LocalDateTime providerRepliedAt) {

    public static ReviewDTO from(Review review) {
        return new ReviewDTO(review.getId(), authorName(review), review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getProviderReply(), review.getProviderRepliedAt());
    }

    // "Ana Pérez" -> "Ana P."
    public static String authorName(Review review) {
        var tourist = review.getTourist();
        String name = tourist.getName() == null ? "" : tourist.getName().trim();
        String last = tourist.getLastName() == null ? "" : tourist.getLastName().trim();
        return last.isEmpty() ? name : name + " " + last.substring(0, 1).toUpperCase() + ".";
    }

    // Estrellas para mostrar: ★★★★☆
    public String stars() {
        return "★".repeat(rating) + "☆".repeat(Math.max(0, 5 - rating));
    }
}
