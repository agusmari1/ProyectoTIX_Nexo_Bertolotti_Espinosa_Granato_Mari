package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import uy.edu.um.nexo.mnatural.dto.ProviderReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewTargetDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Reglas de las reseñas. Repositorio y resolver de prestador son mocks: no se toca la base de datos
class ReviewServiceTest {

    private static final Long TOURIST_ID = 7L;
    private static final Long BOOKING_ID = 30L;
    private static final String PROVIDER_EMAIL = "prestador@ejemplo.test";

    private final ReviewRepository repository = mock(ReviewRepository.class);
    private final ApprovedProviderResolver resolver = mock(ApprovedProviderResolver.class);

    private ReviewService service;
    private Booking booking;
    private Activity activity;
    private ActivitySchedule schedule;
    private Provider provider;

    @BeforeEach
    void setUp() {
        service = new ReviewService(repository, resolver);

        provider = new Provider();
        provider.setId(3L);
        provider.setEmail(PROVIDER_EMAIL);
        Business business = new Business();
        business.setProvider(provider);

        activity = new Activity();
        activity.setId(10L);
        activity.setName("Cabalgata");
        activity.setDurationMinutes(60);
        activity.setBusiness(business);

        // La actividad empezó hace 2 horas y duraba 1: ya terminó
        schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().minusHours(2));

        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        tourist.setName("Ana");
        tourist.setLastName("Pérez");

        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingCode("MN-ABCD2345");

        when(repository.findBookingForReview(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(repository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());
        when(repository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(resolver.require(PROVIDER_EMAIL)).thenReturn(provider);
    }

    private Review review(ReviewStatus status) {
        Review review = new Review();
        review.setId(500L);
        review.setBooking(booking);
        review.setActivity(activity);
        review.setTourist(booking.getTourist());
        review.setRating(4);
        review.setComment("Muy linda");
        review.setStatus(status);
        review.setCreatedAt(LocalDateTime.now().minusHours(1));
        return review;
    }

    // ===== Dejar una reseña =====

    @Test
    void theHolderOfAConfirmedFinishedBookingCanReview() {
        Review review = service.create(BOOKING_ID, TOURIST_ID, 5, "  Excelente paseo  ");

        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getComment()).isEqualTo("Excelente paseo");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(review.getBooking()).isSameAs(booking);
        assertThat(review.getActivity()).isSameAs(activity);
        assertThat(review.getTourist()).isSameAs(booking.getTourist());
    }

    @Test
    void theCommentIsOptional() {
        assertThat(service.create(BOOKING_ID, TOURIST_ID, 3, null).getComment()).isNull();
        assertThat(service.create(BOOKING_ID, TOURIST_ID, 3, "   ").getComment()).isNull();
    }

    @Test
    void onlyTheHolderOfTheBookingCanReviewAndNothingRevealsOtherPeoplesBookings() {
        assertThatThrownBy(() -> service.create(BOOKING_ID, 99L, 5, "x"))
                .isInstanceOf(ReviewException.class).hasMessage("La reserva no existe.");
        assertThatThrownBy(() -> service.create(BOOKING_ID, null, 5, "x")).isInstanceOf(ReviewException.class);
        when(repository.findBookingForReview(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(404L, TOURIST_ID, 5, "x"))
                .isInstanceOf(ReviewException.class).hasMessage("La reserva no existe.");
        verify(repository, never()).save(any());
    }

    @Test
    void aBookingThatIsNotConfirmedCannotBeReviewed() {
        for (BookingStatus status : new BookingStatus[]{BookingStatus.PENDING, BookingStatus.CANCELLED,
                BookingStatus.EXPIRED}) {
            booking.setStatus(status);

            assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "x"))
                    .as(status.name()).isInstanceOf(ReviewException.class).hasMessageContaining("confirmada");
        }
        verify(repository, never()).save(any());
    }

    @Test
    void anActivityThatHasNotFinishedYetCannotBeReviewed() {
        schedule.setStartDateTime(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "x"))
                .isInstanceOf(ReviewException.class).hasMessageContaining("cuando termine");
    }

    @Test
    void anActivityInProgressCannotBeReviewedYet() {
        // Empezó hace 30 minutos y dura 60: termina dentro de media hora
        schedule.setStartDateTime(LocalDateTime.now().minusMinutes(30));

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "x")).isInstanceOf(ReviewException.class);
    }

    @Test
    void theActivityEndsAtTheStartPlusItsDuration() {
        // Empezó hace 61 minutos y dura 60: terminó hace un minuto
        schedule.setStartDateTime(LocalDateTime.now().minusMinutes(61));

        assertThat(service.create(BOOKING_ID, TOURIST_ID, 4, "x")).isNotNull();

        // Con una duración de 3 horas, la misma salida todavía no terminó
        activity.setDurationMinutes(180);
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 4, "x")).isInstanceOf(ReviewException.class);
    }

    @Test
    void thereIsOnlyOneReviewPerBookingEvenIfTheFirstWasDeleted() {
        when(repository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(review(ReviewStatus.VISIBLE)));
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "otra"))
                .isInstanceOf(ReviewException.class).hasMessageContaining("Ya dejaste una reseña");

        when(repository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(review(ReviewStatus.DELETED)));
        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "otra"))
                .isInstanceOf(ReviewException.class).hasMessageContaining("Ya dejaste una reseña");
        verify(repository, never()).save(any());
    }

    @Test
    void theRatingMustBeBetweenOneAndFive() {
        for (Integer bad : new Integer[]{null, 0, 6, -1, 100}) {
            assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, bad, "x"))
                    .as(String.valueOf(bad)).isInstanceOf(ReviewException.class).hasMessageContaining("1 a 5");
        }
        for (int good = 1; good <= 5; good++) {
            assertThat(service.create(BOOKING_ID, TOURIST_ID, good, "x").getRating()).isEqualTo(good);
        }
    }

    @Test
    void theCommentHasALengthLimit() {
        assertThat(service.create(BOOKING_ID, TOURIST_ID, 5, "a".repeat(ReviewService.MAX_TEXT_LENGTH))).isNotNull();

        assertThatThrownBy(() -> service.create(BOOKING_ID, TOURIST_ID, 5, "a".repeat(ReviewService.MAX_TEXT_LENGTH + 1)))
                .isInstanceOf(ReviewException.class).hasMessageContaining("1000");
    }

    // ===== Pantalla de la reseña =====

    @Test
    void theTargetDescribesTheActivityBeingReviewed() {
        ReviewTargetDTO target = service.targetFor(BOOKING_ID, TOURIST_ID);

        assertThat(target.activityName()).isEqualTo("Cabalgata");
        assertThat(target.activityId()).isEqualTo(10L);
        assertThat(target.bookingCode()).isEqualTo("MN-ABCD2345");
    }

    @Test
    void theTargetIsNotAvailableIfThereIsAlreadyAReview() {
        when(repository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(review(ReviewStatus.VISIBLE)));

        assertThatThrownBy(() -> service.targetFor(BOOKING_ID, TOURIST_ID)).isInstanceOf(ReviewException.class);
    }

    @Test
    void theTouristCanSeeTheirOwnReviewButNotSomeoneElses() {
        when(repository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(review(ReviewStatus.VISIBLE)));

        assertThat(service.findOwn(BOOKING_ID, TOURIST_ID)).isPresent();
        assertThat(service.findOwn(BOOKING_ID, 99L)).isEmpty();
        assertThat(service.findOwn(404L, TOURIST_ID)).isEmpty();
    }

    // ===== Eliminar =====

    @Test
    void deletingDoesNotRemoveTheReviewItJustMakesItDeleted() {
        Review review = review(ReviewStatus.VISIBLE);
        when(repository.findById(500L)).thenReturn(Optional.of(review));

        service.delete(500L, TOURIST_ID);

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
        assertThat(review.getDeletedAt()).isNotNull();
        verify(repository).save(review);
        verify(repository, never()).delete(any(Review.class));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void onlyTheAuthorCanDeleteTheirReview() {
        Review review = review(ReviewStatus.VISIBLE);
        when(repository.findById(500L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(500L, 99L)).isInstanceOf(ReviewException.class);
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        verify(repository, never()).save(any());
    }

    @Test
    void aReviewThatDoesNotExistOrWasAlreadyDeletedCannotBeDeleted() {
        when(repository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L, TOURIST_ID)).isInstanceOf(ReviewException.class);

        when(repository.findById(500L)).thenReturn(Optional.of(review(ReviewStatus.DELETED)));
        assertThatThrownBy(() -> service.delete(500L, TOURIST_ID))
                .isInstanceOf(ReviewException.class).hasMessageContaining("ya fue eliminada");
    }

    // ===== Promedio y lista públicos =====

    @Test
    void theSummaryUsesTheAverageAndCountOfVisibleReviewsAndTheirList() {
        when(repository.averageAndCountOf(10L)).thenReturn(List.<Object[]>of(new Object[]{4.5, 2L}));
        when(repository.findVisibleByActivityId(10L)).thenReturn(List.of(review(ReviewStatus.VISIBLE)));

        ReviewSummaryDTO summary = service.summaryOf(10L);

        assertThat(summary.average()).isEqualTo(4.5);
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.hasReviews()).isTrue();
        assertThat(summary.reviews()).hasSize(1);
        assertThat(summary.reviews().getFirst().authorName()).isEqualTo("Ana P.");
    }

    @Test
    void anActivityWithoutReviewsHasNoAverage() {
        when(repository.averageAndCountOf(10L)).thenReturn(List.<Object[]>of(new Object[]{null, 0L}));
        when(repository.findVisibleByActivityId(10L)).thenReturn(List.of());

        ReviewSummaryDTO summary = service.summaryOf(10L);

        assertThat(summary.average()).isNull();
        assertThat(summary.count()).isZero();
        assertThat(summary.hasReviews()).isFalse();
        assertThat(summary.averageText()).isEqualTo("-");
    }

    @Test
    void theAverageIsShownWithADecimalCommaAndStars() {
        assertThat(new ReviewSummaryDTO(4.5, 2, List.of()).averageText()).isEqualTo("4,5");
        assertThat(new ReviewSummaryDTO(4.0, 1, List.of()).averageText()).isEqualTo("4,0");
        assertThat(new ReviewSummaryDTO(4.5, 2, List.of()).stars()).isEqualTo("★★★★★");
        assertThat(new ReviewSummaryDTO(2.4, 2, List.of()).stars()).isEqualTo("★★☆☆☆");
    }

    @Test
    void theAuthorIsShownWithTheNameAndTheInitialOfTheLastNameOnly() {
        Review review = review(ReviewStatus.VISIBLE);

        assertThat(ReviewDTO.from(review).authorName()).isEqualTo("Ana P.");
        assertThat(ReviewDTO.from(review).stars()).isEqualTo("★★★★☆");

        review.getTourist().setLastName(null);
        assertThat(ReviewDTO.from(review).authorName()).isEqualTo("Ana");
    }

    // ===== Prestador =====

    @Test
    void theProviderSeesTheVisibleReviewsOfTheirActivities() {
        when(repository.findVisibleByProviderId(3L)).thenReturn(List.of(review(ReviewStatus.VISIBLE)));

        List<ProviderReviewDTO> reviews = service.findForProvider(PROVIDER_EMAIL);

        assertThat(reviews).hasSize(1);
        assertThat(reviews.getFirst().activityName()).isEqualTo("Cabalgata");
        assertThat(reviews.getFirst().replied()).isFalse();
    }

    @Test
    void theProviderCanReplyOnceToAReviewOfTheirActivity() {
        Review review = review(ReviewStatus.VISIBLE);
        when(repository.findWithProviderById(500L)).thenReturn(Optional.of(review));

        service.reply(500L, PROVIDER_EMAIL, "  ¡Gracias por venir!  ");

        assertThat(review.getProviderReply()).isEqualTo("¡Gracias por venir!");
        assertThat(review.getProviderRepliedAt()).isNotNull();
        // Responder no cambia la reseña ni su puntaje
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(review.getRating()).isEqualTo(4);
    }

    @Test
    void theProviderCannotReplyToAnotherProvidersReview() {
        Provider other = new Provider();
        other.setId(99L);
        Business otherBusiness = new Business();
        otherBusiness.setProvider(other);
        activity.setBusiness(otherBusiness);
        when(repository.findWithProviderById(500L)).thenReturn(Optional.of(review(ReviewStatus.VISIBLE)));

        assertThatThrownBy(() -> service.reply(500L, PROVIDER_EMAIL, "hola"))
                .isInstanceOf(ReviewException.class).hasMessage("La reseña no existe.");
    }

    @Test
    void theProviderCannotReplyTwiceOrToADeletedReviewOrWithAnEmptyText() {
        Review replied = review(ReviewStatus.VISIBLE);
        replied.setProviderReply("Ya contesté");
        when(repository.findWithProviderById(500L)).thenReturn(Optional.of(replied));
        assertThatThrownBy(() -> service.reply(500L, PROVIDER_EMAIL, "otra")).hasMessageContaining("Ya respondiste");

        when(repository.findWithProviderById(500L)).thenReturn(Optional.of(review(ReviewStatus.DELETED)));
        assertThatThrownBy(() -> service.reply(500L, PROVIDER_EMAIL, "hola")).hasMessageContaining("ya no está visible");

        when(repository.findWithProviderById(500L)).thenReturn(Optional.of(review(ReviewStatus.VISIBLE)));
        assertThatThrownBy(() -> service.reply(500L, PROVIDER_EMAIL, "   ")).hasMessageContaining("Escribí la respuesta");
        assertThatThrownBy(() -> service.reply(500L, PROVIDER_EMAIL, "a".repeat(1001))).hasMessageContaining("1000");
    }

    @Test
    void aProviderThatIsNotApprovedCannotUseTheReviewScreens() {
        when(resolver.require("otro@ejemplo.test")).thenThrow(new AccessDeniedException("no aprobado"));

        assertThatThrownBy(() -> service.findForProvider("otro@ejemplo.test")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reply(500L, "otro@ejemplo.test", "hola")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theServiceOffersNoWayForTheProviderToDeleteAReview() {
        // La letra: el prestador puede responder pero no eliminar. El único método que elimina exige ser el turista autor
        Review review = review(ReviewStatus.VISIBLE);
        when(repository.findById(500L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(500L, provider.getId())).isInstanceOf(ReviewException.class);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        verify(repository, never()).save(captor.capture());
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
    }
}
