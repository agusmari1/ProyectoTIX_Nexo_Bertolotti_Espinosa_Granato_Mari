package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Review;

import java.time.LocalDateTime;

// Una reseña tal como se muestra en pantalla. Del turista solo se muestra el nombre y la inicial
// del apellido: nunca el correo ni el documento
public record ReviewViewDTO(Long id, String authorName, int rating, String comment, LocalDateTime createdAt,
                            String providerReply, LocalDateTime repliedAt) {

    public static ReviewViewDTO from(Review review) {
        String lastName = review.getTourist().getLastName();
        String author = review.getTourist().getName()
                + (lastName == null || lastName.isBlank() ? "" : " " + lastName.trim().charAt(0) + ".");
        return new ReviewViewDTO(review.getId(), author, review.getRating(), review.getComment(),
                review.getCreatedAt(), review.getProviderReply(), review.getRepliedAt());
    }
}
