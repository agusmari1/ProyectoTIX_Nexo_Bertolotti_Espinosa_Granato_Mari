package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Review;

import java.time.LocalDateTime;

// Una reseña tal como la ve el prestador en su panel, con lo necesario para responderla
public record ProviderReviewDTO(Long id, String activityName, String authorName, int rating, String comment,
                                LocalDateTime createdAt, String providerReply, LocalDateTime providerRepliedAt) {

    public static ProviderReviewDTO from(Review review) {
        return new ProviderReviewDTO(review.getId(), review.getActivity().getName(), ReviewDTO.authorName(review),
                review.getRating(), review.getComment(), review.getCreatedAt(), review.getProviderReply(),
                review.getProviderRepliedAt());
    }

    public boolean replied() {
        return providerReply != null;
    }

    public String stars() {
        return "★".repeat(rating) + "☆".repeat(Math.max(0, 5 - rating));
    }
}
