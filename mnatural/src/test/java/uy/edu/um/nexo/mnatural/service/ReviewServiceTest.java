package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import uy.edu.um.nexo.mnatural.dto.BookingReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewFormDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio y ActivityService son mocks, no hace falta la base
class ReviewServiceTest {

    private static final Long TOURIST_ID = 7L;
    private static final Long BOOKING_ID = 30L;
    private static final String PROVIDER_EMAIL = "prestador@ejemplo.test";

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final ReviewService service = new ReviewService(reviewRepository, activityService);

    private Activity activity;
    private ActivitySchedule schedule;
    private Booking booking;

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        tourist.setName("Ana");
        tourist.setLastName("Pérez");

        activity = new Activity();
        activity.setId(5L);
        activity.setName("Kayak");
        activity.setDurationMinutes(120);

        schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        // Empezó hace 3 horas y duraba 2: ya terminó
        schedule.setStartDateTime(LocalDateTime.now().minusHours(3));

        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setStatus(BookingStatus.CONFIRMED);

        when(reviewRepository.findBookingById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(reviewRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
        when(reviewRepository.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void theOwnerOfAConfirmedFinishedBookingCanReviewIt() {
        Review review = service.create(BOOKING_ID, TOURIST_ID, form(4, "  Muy lindo paseo  "));

        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getComment()).isEqualTo("Muy lindo paseo");
        assertThat(review.getActivity()).isSameAs(activity);
        assertThat(review.getTourist()).isSameAs(booking.getTourist());
        assertThat(review.getBooking()).isSameAs(booking);
    }

    @Test
    void cannotReviewBeforeTheActivityEnds() {
        // Empezó hace una hora pero dura dos: todavía no terminó
        schedule.setStartDateTime(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(5, "Bien")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("cuando termine");
        assertThat(service.getBookingReview(BOOKING_ID, TOURIST_ID).canReview()).isFalse();
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    void cannotReviewABookingThatIsNotConfirmed() {
        for (BookingStatus status : List.of(BookingStatus.PENDING, BookingStatus.CANCELLED, BookingStatus.EXPIRED)) {
            booking.setStatus(status);
            assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(5, "Bien")))
                    .isInstanceOf(ReviewException.class)
                    .hasMessageContaining("confirmadas");
        }
    }

    @Test
    void anotherTouristSeesTheBookingAsNonexistent() {
        assertThatThrownBy(() -> service.create(BOOKING_ID, 99L, form(5, "Bien")))
                .isInstanceOf(ReviewException.class)
                .hasMessage("La reserva no existe.");
        assertThatThrownBy(() -> service.getBookingReview(BOOKING_ID, 99L))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.delete(BOOKING_ID, 99L))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void onlyOneReviewPerBooking() {
        when(reviewRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existingReview()));

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(3, "Otra vez")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("una sola reseña");
    }

    @Test
    void twoSimultaneousReviewsAreStoppedByTheUniqueConstraint() {
        when(reviewRepository.saveAndFlush(any(Review.class))).thenThrow(new DataIntegrityViolationException("duplicada"));

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(3, "Bien")))
                .isInstanceOf(ReviewException.class)
                .hasMessageContaining("una sola reseña");
    }

    @Test
    void ratingMustBeBetweenOneAndFiveAndCommentIsRequired() {
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(6, "Bien")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(0, "Bien")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(4, "   ")))
                .isInstanceOf(ReviewException.class);
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, form(4, "a".repeat(1001))))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void deletingClearsRatingCommentAndReplyAndBlocksANewReview() {
        Review review = existingReview();
        review.setProviderReply("Gracias");
        review.setRepliedAt(LocalDateTime.now());
        when(reviewRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(review));

        service.delete(BOOKING_ID, TOURIST_ID);

        assertThat(review.isDeleted()).isTrue();
        assertThat(review.getRating()).isNull();
        assertThat(review.getComment()).isNull();
        assertThat(review.getProviderReply()).isNull();
        BookingReviewDTO page = service.getBookingReview(BOOKING_ID, TOURIST_ID);
        assertThat(page.review()).isNull();
        assertThat(page.canReview()).isFalse();
        assertThat(page.blockedReason()).contains("Eliminaste");
    }

    @Test
    void cannotDeleteWhenThereIsNoReview() {
        assertThatThrownBy(() -> service.delete(BOOKING_ID, TOURIST_ID))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void pageShowsTheExistingReview() {
        when(reviewRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existingReview()));

        BookingReviewDTO page = service.getBookingReview(BOOKING_ID, TOURIST_ID);

        assertThat(page.canReview()).isFalse();
        assertThat(page.review().rating()).isEqualTo(4);
        // Solo el nombre y la inicial del apellido
        assertThat(page.review().authorName()).isEqualTo("Ana P.");
    }

    @Test
    void theProviderOwnerCanReplyOnce() {
        Review review = existingReview();
        when(activityService.findOwnActivity(PROVIDER_EMAIL, 5L)).thenReturn(activity);
        when(reviewRepository.findById(80L)).thenReturn(Optional.of(review));

        service.reply(PROVIDER_EMAIL, 5L, 80L, " ¡Gracias por venir! ");

        assertThat(review.getProviderReply()).isEqualTo("¡Gracias por venir!");
        assertThat(review.getRepliedAt()).isNotNull();
        // El comentario del turista no cambia
        assertThat(review.getComment()).isEqualTo("Lindo");
        assertThatThrownBy(() -> service.reply(PROVIDER_EMAIL, 5L, 80L, "Otra respuesta"))
                .isInstanceOf(ReviewException.class)
                .hasMessage("Ya respondiste esta reseña.");
    }

    @Test
    void theProviderCannotReplyToAReviewOfAnotherActivity() {
        Activity other = new Activity();
        other.setId(6L);
        Review review = existingReview();
        review.setActivity(other);
        when(activityService.findOwnActivity(PROVIDER_EMAIL, 5L)).thenReturn(activity);
        when(reviewRepository.findById(80L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.reply(PROVIDER_EMAIL, 5L, 80L, "Hola"))
                .isInstanceOf(ReviewException.class);
        assertThat(review.getProviderReply()).isNull();
    }

    @Test
    void aProviderThatIsNotTheOwnerCannotSeeOrReply() {
        when(activityService.findOwnActivity("otro@ejemplo.test", 5L))
                .thenThrow(new AccessDeniedException("no"));

        assertThatThrownBy(() -> service.findForProvider("otro@ejemplo.test", 5L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reply("otro@ejemplo.test", 5L, 80L, "Hola"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void publicReviewsComeFromTheVisibleQuery() {
        when(reviewRepository.findVisibleByActivityId(5L)).thenReturn(List.of(existingReview()));

        List<ReviewViewDTO> reviews = service.findVisibleReviews(5L);

        assertThat(reviews).extracting(ReviewViewDTO::comment).containsExactly("Lindo");
    }

    @Test
    void topRatedUsesTheWholeCalendarMonthAndThreeResults() {
        service.findTopRated(Department.ROCHA, YearMonth.of(2026, 2));

        verify(reviewRepository).findTopRated(Department.ROCHA, LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 3, 1, 0, 0), PageRequest.of(0, 3));
    }

    private Review existingReview() {
        Review review = new Review();
        review.setId(80L);
        review.setBooking(booking);
        review.setTourist(booking.getTourist());
        review.setActivity(activity);
        review.setRating(4);
        review.setComment("Lindo");
        review.setCreatedAt(LocalDateTime.now().minusDays(1));
        return review;
    }

    private ReviewFormDTO form(int rating, String comment) {
        ReviewFormDTO form = new ReviewFormDTO();
        form.setRating(rating);
        form.setComment(comment);
        return form;
    }
}
