package uy.edu.um.nexo.mnatural.controllers;

import jakarta.persistence.EntityManager;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El ranking contra la base real y con la seguridad activa, sin iniciar sesión. Valida la consulta de verdad
// (agrupar, ordenar, desempatar, filtrar por mes y por estado). @Transactional deshace todo al final de cada test.
// Se usa SALTO: las actividades de los otros tests de la API son de Maldonado
@SpringBootTest
@Transactional
class TopActivitiesIntegrationTest {

    private static final Department DEPARTMENT = Department.SALTO;
    private static final YearMonth MONTH = YearMonth.now();

    @Autowired
    private WebApplicationContext context;
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
    private ReviewRepository reviewRepository;
    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private Business approved;
    private Business pending;
    private ActivityType type;
    private Tourist tourist;
    private int sequence;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        approved = business("Zztop Aprobado", "zztop-ok@test.local", ApprovalStatus.APPROVED);
        pending = business("Zztop Pendiente", "zztop-pend@test.local", ApprovalStatus.PENDING);
        type = new ActivityType();
        type.setName("Zztop Tipo");
        activityTypeRepository.save(type);
        tourist = new Tourist();
        tourist.setEmail("zztop-turista@test.local");
        tourist.setPasswordHash("hash");
        tourist.setName("Zz");
        tourist.setLastName("Top");
        tourist.setDateOfBirth(LocalDate.of(1990, 1, 1));
        tourist.setDocumentType(DocumentType.OTHER);
        tourist.setDocumentNumber("1");
        tourist.setCountry("Uruguay");
        touristRepository.save(tourist);
    }

    @Test
    void theRankingIsOrderedByAverageThenByNumberOfRatingsAndHasAtMostThree() throws Exception {
        Activity a = activity("Zztop A", approved, ActivityStatus.PUBLISHED, DEPARTMENT);   // 5, 5, 4 -> 4,67 (3)
        Activity b = activity("Zztop B", approved, ActivityStatus.PUBLISHED, DEPARTMENT);   // 5 -> 5,0 (1)
        Activity c = activity("Zztop C", approved, ActivityStatus.PUBLISHED, DEPARTMENT);   // 5, 5 -> 5,0 (2)
        Activity d = activity("Zztop D", approved, ActivityStatus.PUBLISHED, DEPARTMENT);   // 4 -> 4,0 (1)
        review(a, 5, ReviewStatus.VISIBLE, now());
        review(a, 5, ReviewStatus.VISIBLE, now());
        review(a, 4, ReviewStatus.VISIBLE, now());
        review(b, 5, ReviewStatus.VISIBLE, now());
        review(c, 5, ReviewStatus.VISIBLE, now());
        review(c, 5, ReviewStatus.VISIBLE, now());
        review(d, 4, ReviewStatus.VISIBLE, now());

        // C y B empatan en 5,0: gana C por tener más valoraciones. D queda afuera por ser la cuarta
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].activity.name", contains("Zztop C", "Zztop B", "Zztop A")))
                .andExpect(jsonPath("$[0].averageRating").value(5.0))
                .andExpect(jsonPath("$[0].ratingCount").value(2))
                .andExpect(jsonPath("$[2].averageRating").value(4.67))
                .andExpect(jsonPath("$[2].ratingCount").value(3));
    }

    @Test
    void deletedReviewsDoNotCountForTheAverageOrTheRanking() throws Exception {
        Activity a = activity("Zztop A", approved, ActivityStatus.PUBLISHED, DEPARTMENT);
        Activity b = activity("Zztop B", approved, ActivityStatus.PUBLISHED, DEPARTMENT);
        review(a, 5, ReviewStatus.VISIBLE, now());
        review(a, 1, ReviewStatus.VISIBLE, now());   // promedio 3,0
        review(a, 5, ReviewStatus.DELETED, now());   // no cuenta
        review(b, 5, ReviewStatus.DELETED, now());   // B solo tiene una eliminada: no entra

        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activity.name").value("Zztop A"))
                .andExpect(jsonPath("$[0].averageRating").value(3.0))
                .andExpect(jsonPath("$[0].ratingCount").value(2));
    }

    @Test
    void onlyReviewsOfTheRequestedMonthCount() throws Exception {
        Activity a = activity("Zztop A", approved, ActivityStatus.PUBLISHED, DEPARTMENT);
        review(a, 5, ReviewStatus.VISIBLE, now());
        review(a, 1, ReviewStatus.VISIBLE, MONTH.minusMonths(1).atDay(15).atTime(12, 0));
        // Último instante del mes anterior y primer instante de este: cada uno cuenta en su mes
        Activity b = activity("Zztop B", approved, ActivityStatus.PUBLISHED, DEPARTMENT);
        review(b, 2, ReviewStatus.VISIBLE, MONTH.atDay(1).atStartOfDay());
        review(b, 4, ReviewStatus.VISIBLE, MONTH.atDay(1).atStartOfDay().minusSeconds(1));

        // Mes actual (sin parámetro y con el parámetro): A tiene solo el 5 y B solo el 2
        for (String month : new String[]{null, MONTH.toString()}) {
            var request = get("/api/activities/top").param("department", DEPARTMENT.name());
            if (month != null) {
                request = request.param("month", month);
            }
            mockMvc.perform(request)
                    .andExpect(jsonPath("$[*].activity.name", contains("Zztop A", "Zztop B")))
                    .andExpect(jsonPath("$[0].averageRating").value(5.0))
                    .andExpect(jsonPath("$[0].ratingCount").value(1))
                    .andExpect(jsonPath("$[1].averageRating").value(2.0));
        }

        // Mes anterior: B (4) le gana a A (1)
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name())
                        .param("month", MONTH.minusMonths(1).toString()))
                .andExpect(jsonPath("$[*].activity.name", contains("Zztop B", "Zztop A")))
                .andExpect(jsonPath("$[0].averageRating").value(4.0))
                .andExpect(jsonPath("$[1].averageRating").value(1.0));

        // Un mes sin reseñas devuelve una lista vacía, no un error
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name())
                        .param("month", MONTH.plusMonths(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void onlyPublishedActivitiesOfApprovedProvidersInTheDepartmentAppear() throws Exception {
        Activity ok = activity("Zztop Ok", approved, ActivityStatus.PUBLISHED, DEPARTMENT);
        Activity draft = activity("Zztop Borrador", approved, ActivityStatus.DRAFT, DEPARTMENT);
        Activity ofPending = activity("Zztop De Pendiente", pending, ActivityStatus.PUBLISHED, DEPARTMENT);
        Activity elsewhere = activity("Zztop Otro Depto", approved, ActivityStatus.PUBLISHED, Department.ROCHA);
        for (Activity activity : new Activity[]{ok, draft, ofPending, elsewhere}) {
            review(activity, 5, ReviewStatus.VISIBLE, now());
        }

        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()))
                .andExpect(jsonPath("$[*].activity.name", contains("Zztop Ok")));
    }

    @Test
    void anInvalidMonthIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()).param("month", "noviembre"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor de 'month' no es válido."));
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()).param("month", "2026-13"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/activities/top").param("department", DEPARTMENT.name()).param("month", "11/2026"))
                .andExpect(status().isBadRequest());
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private Review review(Activity activity, int rating, ReviewStatus status, LocalDateTime createdAt) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.of(2099, 1, 1, 8, 0).plusMinutes(sequence));
        schedule.setCapacity(10);
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        scheduleRepository.save(schedule);

        Booking booking = new Booking();
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setParticipantCount(1);
        booking.setTotalAmount(new BigDecimal("100.00"));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingCode("ZT-" + (++sequence) + "-" + System.nanoTime());
        bookingRepository.save(booking);

        Review review = new Review();
        review.setBooking(booking);
        review.setActivity(activity);
        review.setTourist(tourist);
        review.setRating(rating);
        review.setStatus(status);
        review.setCreatedAt(createdAt);
        return reviewRepository.saveAndFlush(review);
    }

    private Business business(String name, String email, ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setEmail(email);
        provider.setPasswordHash("hash");
        provider.setName("Zztop");
        provider.setLastName("Prueba");
        provider.setDateOfBirth(LocalDate.of(1990, 1, 1));
        provider.setDocumentType(DocumentType.OTHER);
        provider.setDocumentNumber("00000000");
        provider.setCountry("Uruguay");
        provider.setApprovalStatus(status);
        providerRepository.save(provider);

        Business business = new Business();
        business.setName(name);
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle 1");
        business.setPhoneNumber("099111222");
        business.setEmail(email);
        business.setProvider(provider);
        return businessRepository.save(business);
    }

    private Activity activity(String name, Business business, ActivityStatus status, Department department) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setDescription("Descripción de prueba");
        activity.setAddress("Punto de encuentro");
        activity.setDepartment(department);
        activity.setLocality("Localidad");
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("1000.00"));
        activity.setStatus(status);
        activity.setBusiness(business);
        activity.setActivityTypes(Set.of(type));
        return activityRepository.save(activity);
    }
}
