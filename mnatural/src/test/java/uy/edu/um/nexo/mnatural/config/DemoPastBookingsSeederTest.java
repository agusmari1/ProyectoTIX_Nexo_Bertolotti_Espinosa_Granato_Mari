package uy.edu.um.nexo.mnatural.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Los datos de demostración para mostrar reseñas en vivo. Contra la base real, con datos propios del test (prestador,
// turista y prefijo de códigos distintos de los de la demo) y @Transactional: se deshace todo al final de cada test
@SpringBootTest
@Transactional
class DemoPastBookingsSeederTest {

    private static final String PROVIDER_EMAIL = "zzdemo-prestador@test.local";
    private static final String TOURIST_EMAIL = "zzdemo-turista@test.local";
    private static final String PREFIX = "ZZD-";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ActivityTypeRepository activityTypeRepository;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private ActivityScheduleRepository scheduleRepository;
    @Autowired
    private TouristRepository touristRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private PaymentTransactionRepository paymentRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ReviewService reviewService;

    private Provider provider;
    private Business business;

    @BeforeEach
    void setUp() {
        provider = provider(ApprovalStatus.APPROVED);
        business = new Business();
        business.setName("Zzdemo Emprendimiento");
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle 1");
        business.setPhoneNumber("099111222");
        business.setEmail(PROVIDER_EMAIL);
        business.setProvider(provider);
        businessRepository.save(business);
    }

    private DemoPastBookingsSeeder seeder() {
        return new DemoPastBookingsSeeder(userRepository, touristRepository, activityRepository, scheduleRepository,
                bookingRepository, paymentRepository, reviewRepository, passwordEncoder, PROVIDER_EMAIL,
                TOURIST_EMAIL, PREFIX);
    }

    private Provider provider(ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setEmail(PROVIDER_EMAIL);
        provider.setPasswordHash("hash");
        provider.setName("Zzdemo");
        provider.setLastName("Prueba");
        provider.setDateOfBirth(LocalDate.of(1990, 1, 1));
        provider.setDocumentType(DocumentType.OTHER);
        provider.setDocumentNumber("00000000");
        provider.setCountry("Uruguay");
        provider.setApprovalStatus(status);
        return providerRepository.save(provider);
    }

    private void activities(int count) {
        ActivityType type = new ActivityType();
        type.setName("Zzdemo Tipo");
        activityTypeRepository.save(type);
        for (int i = 0; i < count; i++) {
            Activity activity = new Activity();
            activity.setName("Zzdemo Actividad " + i);
            activity.setDescription("Descripción");
            activity.setAddress("Punto de encuentro");
            activity.setDepartment(Department.ROCHA);
            activity.setLocality("Castillos");
            activity.setDurationMinutes(60);
            activity.setPricePerPerson(new BigDecimal("1000.00"));
            activity.setStatus(ActivityStatus.PUBLISHED);
            activity.setBusiness(business);
            activity.setActivityTypes(Set.of(type));
            activityRepository.save(activity);
        }
    }

    private List<Booking> demoBookings() {
        return bookingRepository.findAll().stream()
                .filter(b -> b.getBookingCode() != null && b.getBookingCode().startsWith(PREFIX)).toList();
    }

    private List<Review> demoReviews() {
        return reviewRepository.findAll().stream()
                .filter(r -> r.getBooking().getBookingCode().startsWith(PREFIX)).toList();
    }

    private Tourist demoTourist() {
        return (Tourist) userRepository.findByEmail(TOURIST_EMAIL).orElseThrow();
    }

    // ===== Qué crea =====

    @Test
    void itCreatesPastConfirmedBookingsWithParticipantsAndAnApprovedPayment() {
        activities(10);

        seeder().run();

        List<Booking> bookings = demoBookings();
        assertThat(bookings).hasSize(15);
        LocalDateTime now = LocalDateTime.now();
        for (Booking booking : bookings) {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            // Horarios que ya pasaron, aunque el servicio normal no deje crearlos
            assertThat(booking.getActivitySchedule().getStartDateTime()).isBefore(now.minusDays(6));
            assertThat(booking.getParticipants()).hasSize(booking.getParticipantCount());
            assertThat(booking.getTotalAmount()).isEqualByComparingTo(
                    new BigDecimal("1000.00").multiply(BigDecimal.valueOf(booking.getParticipantCount())));
            assertThat(paymentRepository.findByBookingIdAndTypeAndStatus(booking.getId(), PaymentType.CHARGE,
                    PaymentStatus.APPROVED)).hasSize(1);
        }
    }

    @Test
    void itCreatesPublishedReviewsWithDifferentRatingsAndSomeProviderReplies() {
        activities(10);

        seeder().run();

        List<Review> reviews = demoReviews();
        assertThat(reviews).hasSize(12);
        assertThat(reviews).allMatch(r -> r.getStatus() == ReviewStatus.VISIBLE);
        assertThat(reviews).extracting(Review::getRating).contains(2, 3, 4, 5);
        assertThat(reviews).filteredOn(r -> r.getProviderReply() != null).hasSize(4);
        assertThat(reviews).filteredOn(r -> r.getComment() == null).hasSize(1);
        // Hechas por distintos turistas, y ninguna por el de la demo en vivo
        assertThat(reviews).extracting(r -> r.getTourist().getId()).doesNotContain(demoTourist().getId());
        assertThat(reviews.stream().map(r -> r.getTourist().getId()).distinct().count()).isGreaterThanOrEqualTo(4);
        // En el mes en curso: así también cuentan para el top 3 de la API
        assertThat(reviews).allMatch(r -> r.getCreatedAt().getMonth() == LocalDate.now().getMonth());
    }

    @Test
    void theLiveDemoTouristHasPastConfirmedBookingsWithoutAReview() {
        activities(10);

        seeder().run();

        Tourist tourist = demoTourist();
        assertThat(passwordEncoder.matches("prueba1234", tourist.getPasswordHash())).isTrue();
        List<Booking> mine = demoBookings().stream().filter(b -> b.getTourist().getId().equals(tourist.getId()))
                .toList();
        assertThat(mine).hasSize(3);
        assertThat(mine).allMatch(b -> reviewRepository.findByBookingId(b.getId()).isEmpty());
        assertThat(reviewRepository.findReviewedBookingIdsOf(tourist.getId())).isEmpty();
    }

    // ===== Se puede valorar en vivo, con las reglas de siempre =====

    @Test
    void theSeededPastBookingsCanBeRatedLiveWithTheRealRulesAndOnlyOnce() {
        activities(10);
        seeder().run();
        Tourist tourist = demoTourist();
        Booking mine = demoBookings().stream().filter(b -> b.getTourist().getId().equals(tourist.getId()))
                .findFirst().orElseThrow();
        Long activityId = mine.getActivitySchedule().getActivity().getId();
        ReviewSummaryDTO before = reviewService.summaryOf(activityId);

        Review created = reviewService.create(mine.getId(), tourist.getId(), 5, "Lo valoro en vivo");

        assertThat(created.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        ReviewSummaryDTO after = reviewService.summaryOf(activityId);
        assertThat(after.count()).isEqualTo(before.count() + 1);
        assertThat(reviewRepository.findReviewedBookingIdsOf(tourist.getId())).containsExactly(mine.getId());
        // Una sola vez por reserva
        assertThatThrownBy(() -> reviewService.create(mine.getId(), tourist.getId(), 3, "otra"))
                .isInstanceOf(ReviewException.class);
    }

    @Test
    void anotherTouristCannotRateTheDemoTouristsBooking() {
        activities(10);
        seeder().run();
        Tourist tourist = demoTourist();
        Booking mine = demoBookings().stream().filter(b -> b.getTourist().getId().equals(tourist.getId()))
                .findFirst().orElseThrow();
        Tourist other = demoBookings().stream().map(Booking::getTourist)
                .filter(t -> !t.getId().equals(tourist.getId())).findFirst().orElseThrow();

        assertThatThrownBy(() -> reviewService.create(mine.getId(), other.getId(), 1, "ajena"))
                .isInstanceOf(ReviewException.class);
    }

    // ===== Idempotencia y casos sin datos =====

    @Test
    void runningItTwiceDoesNotDuplicateAnything() {
        activities(10);
        DemoPastBookingsSeeder seeder = seeder();

        seeder.run();
        long users = userRepository.count();
        long schedules = scheduleRepository.count();
        long payments = paymentRepository.count();
        seeder.run();
        seeder().run();

        assertThat(demoBookings()).hasSize(15);
        assertThat(demoReviews()).hasSize(12);
        assertThat(userRepository.count()).isEqualTo(users);
        assertThat(scheduleRepository.count()).isEqualTo(schedules);
        assertThat(paymentRepository.count()).isEqualTo(payments);
    }

    @Test
    void anExistingReviewOfTheLiveDemoIsNotTouchedWhenItRunsAgain() {
        activities(10);
        seeder().run();
        Tourist tourist = demoTourist();
        Booking mine = demoBookings().stream().filter(b -> b.getTourist().getId().equals(tourist.getId()))
                .findFirst().orElseThrow();
        reviewService.create(mine.getId(), tourist.getId(), 4, "Ya la valoré");

        seeder().run();

        assertThat(reviewRepository.findByBookingId(mine.getId())).get().extracting(Review::getComment)
                .isEqualTo("Ya la valoré");
        assertThat(demoReviews()).hasSize(13);
    }

    @Test
    void withFewActivitiesItStillCreatesEverythingReusingThem() {
        activities(2);

        seeder().run();

        assertThat(demoBookings()).hasSize(15);
        assertThat(demoReviews()).hasSize(12);
    }

    @Test
    void withoutPublishedActivitiesOrWithoutAnApprovedProviderItDoesNothingAndDoesNotFail() {
        // Sin actividades publicadas
        seeder().run();
        assertThat(demoBookings()).isEmpty();
        assertThat(userRepository.findByEmail(TOURIST_EMAIL)).isEmpty();

        // Con el prestador sin aprobar
        provider.setApprovalStatus(ApprovalStatus.PENDING);
        providerRepository.save(provider);
        activities(3);
        seeder().run();
        assertThat(demoBookings()).isEmpty();

        // Con un prestador que no existe
        Optional<?> none = userRepository.findByEmail("noexiste-zzdemo@test.local");
        assertThat(none).isEmpty();
    }

    // ===== Apagado por defecto =====

    @Test
    void itIsOffByDefaultAndOnlyExistsWhenThePropertyIsTrue() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DemoPastBookingsSeeder.class)
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(TouristRepository.class, () -> mock(TouristRepository.class))
                .withBean(ActivityRepository.class, () -> mock(ActivityRepository.class))
                .withBean(ActivityScheduleRepository.class, () -> mock(ActivityScheduleRepository.class))
                .withBean(BookingRepository.class, () -> mock(BookingRepository.class))
                .withBean(PaymentTransactionRepository.class, () -> mock(PaymentTransactionRepository.class))
                .withBean(ReviewRepository.class, () -> mock(ReviewRepository.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));

        runner.run(context -> assertThat(context).doesNotHaveBean(DemoPastBookingsSeeder.class));
        runner.withPropertyValues("app.seed.demo-past-bookings=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoPastBookingsSeeder.class));
        runner.withPropertyValues("app.seed.demo-past-bookings=true")
                .run(context -> assertThat(context).hasSingleBean(DemoPastBookingsSeeder.class));
    }
}
