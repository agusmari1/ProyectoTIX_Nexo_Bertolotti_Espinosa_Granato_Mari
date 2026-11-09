package uy.edu.um.nexo.mnatural.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BookingReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewFormDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

// Reseñas de actividades (ESPECIFICACION.md, sección 5):
//   - el turista califica de 1 a 5 y comenta una reserva suya CONFIRMED cuya actividad ya terminó
//     (inicio del horario más la duración), una sola vez por reserva;
//   - el turista puede eliminar su reseña: deja de verse y de contar para el promedio;
//   - el prestador dueño de la actividad puede responder una vez, pero no editar ni borrar la reseña.
// Las reservas se leen y nunca se modifican
@Service
public class ReviewService {

    public static final int MAX_TEXT_LENGTH = 1000;
    // Top de actividades del mes (API pública)
    public static final int TOP_SIZE = 3;

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReviewRepository reviewRepository;
    private final ActivityService activityService;

    public ReviewService(ReviewRepository reviewRepository, ActivityService activityService) {
        this.reviewRepository = reviewRepository;
        this.activityService = activityService;
    }

    // ===== Turista =====

    // Datos de la pantalla de calificar una reserva del turista
    @Transactional(readOnly = true)
    public BookingReviewDTO getBookingReview(Long bookingId, Long touristId) {
        Booking booking = ownBooking(bookingId, touristId);
        ActivitySchedule schedule = booking.getActivitySchedule();
        Activity activity = schedule.getActivity();
        Optional<Review> existing = reviewRepository.findByBookingId(bookingId);

        ReviewViewDTO review = existing.filter(r -> !r.isDeleted()).map(ReviewViewDTO::from).orElse(null);
        String blockedReason = existing.isPresent()
                ? (existing.get().isDeleted() ? "Eliminaste la reseña de esta reserva: no se puede volver a calificar." : null)
                : notReviewableReason(booking, LocalDateTime.now());
        boolean canReview = existing.isEmpty() && blockedReason == null;

        return new BookingReviewDTO(booking.getId(), activity.getId(), activity.getName(), schedule.getStartDateTime(),
                review, canReview, blockedReason);
    }

    @Transactional
    public Review create(Long bookingId, Long touristId, ReviewFormDTO form) {
        Booking booking = ownBooking(bookingId, touristId);
        String reason = notReviewableReason(booking, LocalDateTime.now());
        if (reason != null) {
            throw new ReviewException(reason);
        }
        if (reviewRepository.findByBookingId(bookingId).isPresent()) {
            throw new ReviewException("Ya calificaste esta reserva: se puede dejar una sola reseña por reserva.");
        }
        Integer rating = form.getRating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new ReviewException("La calificación va de 1 a 5.");
        }
        String comment = cleanText(form.getComment(), "Escribí un comentario.");

        Review review = new Review();
        review.setBooking(booking);
        review.setTourist(booking.getTourist());
        review.setActivity(booking.getActivitySchedule().getActivity());
        review.setRating(rating);
        review.setComment(comment);
        try {
            // saveAndFlush: si llegan dos envíos a la vez, la restricción única de booking_id salta acá
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new ReviewException("Ya calificaste esta reserva: se puede dejar una sola reseña por reserva.");
        }
    }

    // El turista elimina su reseña: se vacían la nota, el comentario y la respuesta del prestador
    @Transactional
    public void delete(Long bookingId, Long touristId) {
        ownBooking(bookingId, touristId);
        Review review = reviewRepository.findByBookingId(bookingId)
                .filter(r -> !r.isDeleted())
                .orElseThrow(() -> new ReviewException("Esta reserva no tiene una reseña para eliminar."));
        review.setRating(null);
        review.setComment(null);
        review.setProviderReply(null);
        review.setRepliedAt(null);
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
    }

    // ===== Público =====

    @Transactional(readOnly = true)
    public List<ReviewViewDTO> findVisibleReviews(Long activityId) {
        return reviewRepository.findVisibleByActivityId(activityId).stream()
                .map(ReviewViewDTO::from)
                .toList();
    }

    // Id de actividad -> promedio y cantidad. Las actividades sin reseñas no figuran
    @Transactional(readOnly = true)
    public Map<Long, ReviewSummaryDTO> summariesByActivity() {
        return reviewRepository.summarizeAllActivities().stream()
                .collect(Collectors.toMap(ReviewSummaryDTO::activityId, Function.identity()));
    }

    // Las 3 actividades mejor puntuadas del departamento con las reseñas creadas en ese mes.
    // Puede devolver menos de 3 (o ninguna)
    @Transactional(readOnly = true)
    public List<ReviewSummaryDTO> findTopRated(Department department, YearMonth month) {
        return reviewRepository.findTopRated(department, month.atDay(1).atStartOfDay(),
                month.plusMonths(1).atDay(1).atStartOfDay(), PageRequest.of(0, TOP_SIZE));
    }

    // ===== Prestador =====

    // ActivityService.findOwnActivity verifica que sea un prestador aprobado y dueño de la actividad
    @Transactional(readOnly = true)
    public List<ReviewViewDTO> findForProvider(String providerEmail, Long activityId) {
        Activity activity = activityService.findOwnActivity(providerEmail, activityId);
        return findVisibleReviews(activity.getId());
    }

    @Transactional
    public Review reply(String providerEmail, Long activityId, Long reviewId, String text) {
        Activity activity = activityService.findOwnActivity(providerEmail, activityId);
        Review review = reviewRepository.findById(reviewId)
                .filter(r -> !r.isDeleted() && Objects.equals(r.getActivity().getId(), activity.getId()))
                .orElseThrow(() -> new ReviewException("Esa reseña no existe o ya no está disponible."));
        if (review.getProviderReply() != null) {
            throw new ReviewException("Ya respondiste esta reseña.");
        }
        review.setProviderReply(cleanText(text, "Escribí tu respuesta."));
        review.setRepliedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    // ===== Reglas =====

    // Momento en que termina la actividad reservada: inicio del horario más la duración
    public static LocalDateTime endOf(ActivitySchedule schedule) {
        return schedule.getStartDateTime().plusMinutes(schedule.getActivity().getDurationMinutes());
    }

    // null si la reserva se puede calificar ahora; si no, el motivo para mostrarle al turista
    private String notReviewableReason(Booking booking, LocalDateTime now) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return "Solo se pueden calificar reservas confirmadas.";
        }
        LocalDateTime end = endOf(booking.getActivitySchedule());
        if (now.isBefore(end)) {
            return "Vas a poder calificar la actividad cuando termine, a partir del "
                    + end.format(DATE_TIME_FORMAT) + ".";
        }
        return null;
    }

    // Para cualquier otro turista (o un id que no existe), la reserva "no existe"
    private Booking ownBooking(Long bookingId, Long touristId) {
        Booking booking = reviewRepository.findBookingById(bookingId)
                .orElseThrow(() -> new ReviewException("La reserva no existe."));
        if (touristId == null || !Objects.equals(booking.getTourist().getId(), touristId)) {
            throw new ReviewException("La reserva no existe.");
        }
        return booking;
    }

    private String cleanText(String text, String emptyMessage) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new ReviewException(emptyMessage);
        }
        if (clean.length() > MAX_TEXT_LENGTH) {
            throw new ReviewException("El texto no puede superar los " + MAX_TEXT_LENGTH + " caracteres.");
        }
        return clean;
    }
}
