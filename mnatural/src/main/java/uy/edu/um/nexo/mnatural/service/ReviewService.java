package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ProviderReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewTargetDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Reseñas de actividades (ESPECIFICACION.md, sección 5):
//   - la deja el TITULAR de una reserva CONFIRMADA, una vez que la actividad terminó (inicio del horario + duración),
//     y una sola por reserva (aunque la haya eliminado, no puede dejar otra);
//   - el turista puede eliminar la suya: pasa a DELETED, no se borra y deja de contar para el promedio;
//   - el prestador puede responder públicamente (una vez) pero no eliminar.
// El promedio y la lista públicos cuentan solo las reseñas VISIBLES
@Service
public class ReviewService {

    public static final int MAX_TEXT_LENGTH = 1000;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReviewRepository reviewRepository;
    private final ApprovedProviderResolver providerResolver;

    public ReviewService(ReviewRepository reviewRepository, ApprovedProviderResolver providerResolver) {
        this.reviewRepository = reviewRepository;
        this.providerResolver = providerResolver;
    }

    // ===== Turista =====

    // Verifica que el turista pueda reseñar esa reserva y devuelve qué actividad es. Tira ReviewException con el motivo
    @Transactional(readOnly = true)
    public ReviewTargetDTO targetFor(Long bookingId, Long touristId) {
        Booking booking = eligibleBooking(bookingId, touristId);
        if (reviewRepository.findByBookingId(bookingId).isPresent()) {
            throw new ReviewException("Ya dejaste una reseña para esta reserva.");
        }
        return toTarget(booking);
    }

    // La reseña que el turista ya dejó para esa reserva (visible o eliminada), para mostrársela
    @Transactional(readOnly = true)
    public Optional<Review> findOwn(Long bookingId, Long touristId) {
        Booking booking = reviewRepository.findBookingForReview(bookingId).orElse(null);
        if (booking == null || !Objects.equals(booking.getTourist().getId(), touristId)) {
            return Optional.empty();
        }
        return reviewRepository.findByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public ReviewTargetDTO describe(Long bookingId, Long touristId) {
        Booking booking = reviewRepository.findBookingForReview(bookingId)
                .filter(b -> Objects.equals(b.getTourist().getId(), touristId))
                .orElseThrow(() -> new ReviewException("La reserva no existe."));
        return toTarget(booking);
    }

    @Transactional
    public Review create(Long bookingId, Long touristId, Integer rating, String comment) {
        Booking booking = eligibleBooking(bookingId, touristId);
        if (reviewRepository.findByBookingId(bookingId).isPresent()) {
            throw new ReviewException("Ya dejaste una reseña para esta reserva.");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new ReviewException("El puntaje tiene que ser de 1 a 5.");
        }
        String text = clean(comment);
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new ReviewException("El comentario no puede superar los " + MAX_TEXT_LENGTH + " caracteres.");
        }

        Review review = new Review();
        review.setBooking(booking);
        review.setActivity(booking.getActivitySchedule().getActivity());
        review.setTourist(booking.getTourist());
        review.setRating(rating);
        review.setComment(text);
        review.setStatus(ReviewStatus.VISIBLE);
        return reviewRepository.save(review);
    }

    // El turista elimina SU reseña: no se borra de la base, pasa a DELETED. Queda fuera de la lista y del promedio
    @Transactional
    public void delete(Long reviewId, Long touristId) {
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> Objects.equals(r.getTourist().getId(), touristId))
                .orElseThrow(() -> new ReviewException("La reseña no existe."));
        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new ReviewException("Esa reseña ya fue eliminada.");
        }
        review.setStatus(ReviewStatus.DELETED);
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }

    // ===== Público =====

    // Promedio, cantidad y lista de las reseñas VISIBLES de una actividad
    @Transactional(readOnly = true)
    public ReviewSummaryDTO summaryOf(Long activityId) {
        List<Object[]> stats = reviewRepository.averageAndCountOf(activityId);
        Double average = null;
        long count = 0;
        if (!stats.isEmpty() && stats.getFirst()[0] != null) {
            average = ((Number) stats.getFirst()[0]).doubleValue();
            count = ((Number) stats.getFirst()[1]).longValue();
        }
        List<ReviewDTO> reviews = reviewRepository.findVisibleByActivityId(activityId).stream()
                .map(ReviewDTO::from)
                .toList();
        return new ReviewSummaryDTO(average, count, reviews);
    }

    // ===== Prestador =====

    @Transactional(readOnly = true)
    public List<ProviderReviewDTO> findForProvider(String providerEmail) {
        Provider provider = providerResolver.require(providerEmail);
        return reviewRepository.findVisibleByProviderId(provider.getId()).stream()
                .map(ProviderReviewDTO::from)
                .toList();
    }

    // El prestador responde públicamente a una reseña de SUS actividades. Una sola respuesta, y no puede eliminarla
    @Transactional
    public Review reply(Long reviewId, String providerEmail, String text) {
        Provider provider = providerResolver.require(providerEmail);
        Review review = reviewRepository.findWithProviderById(reviewId)
                .filter(r -> Objects.equals(r.getActivity().getBusiness().getProvider().getId(), provider.getId()))
                .orElseThrow(() -> new ReviewException("La reseña no existe."));
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw new ReviewException("Esa reseña ya no está visible.");
        }
        if (review.getProviderReply() != null) {
            throw new ReviewException("Ya respondiste esta reseña.");
        }
        String reply = clean(text);
        if (reply == null) {
            throw new ReviewException("Escribí la respuesta.");
        }
        if (reply.length() > MAX_TEXT_LENGTH) {
            throw new ReviewException("La respuesta no puede superar los " + MAX_TEXT_LENGTH + " caracteres.");
        }
        review.setProviderReply(reply);
        review.setProviderRepliedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    // ===== Reglas =====

    // La reserva tiene que ser del turista, estar confirmada y su actividad tiene que haber terminado.
    // Mismo cálculo que el botón "Calificar" de "Mis reservas" (BookingService.toTouristBooking)
    private Booking eligibleBooking(Long bookingId, Long touristId) {
        Booking booking = reviewRepository.findBookingForReview(bookingId)
                .filter(b -> touristId != null && Objects.equals(b.getTourist().getId(), touristId))
                .orElseThrow(() -> new ReviewException("La reserva no existe."));
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ReviewException("Solo se puede calificar una reserva confirmada.");
        }
        ActivitySchedule schedule = booking.getActivitySchedule();
        Activity activity = schedule.getActivity();
        LocalDateTime end = schedule.getStartDateTime().plusMinutes(activity.getDurationMinutes());
        if (!end.isBefore(LocalDateTime.now())) {
            throw new ReviewException("Vas a poder calificar la actividad cuando termine (el "
                    + end.format(DATE_TIME_FORMAT) + ").");
        }
        return booking;
    }

    private ReviewTargetDTO toTarget(Booking booking) {
        ActivitySchedule schedule = booking.getActivitySchedule();
        Activity activity = schedule.getActivity();
        return new ReviewTargetDTO(booking.getId(), activity.getId(), activity.getName(), schedule.getStartDateTime(),
                booking.getBookingCode());
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
