package uy.edu.um.nexo.mnatural.repository;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// Arma y guarda datos de prueba para los tests @DataJpaTest, que corren sobre H2 en memoria
// (nunca sobre la base real). Los datos son de mentira
public class TestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TestEntityManager em;

    public TestData(TestEntityManager em) {
        this.em = em;
    }

    public Provider provider(ApprovalStatus status) {
        Provider provider = fill(new Provider());
        provider.setApprovalStatus(status);
        return em.persist(provider);
    }

    public Tourist tourist(String name, String lastName) {
        Tourist tourist = fill(new Tourist());
        tourist.setName(name);
        tourist.setLastName(lastName);
        return em.persist(tourist);
    }

    public Business business(Provider provider, String name) {
        Business business = new Business();
        business.setName(name);
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle 1");
        business.setPhoneNumber("099000000");
        business.setEmail("negocio" + SEQUENCE.incrementAndGet() + "@ejemplo.test");
        business.setProvider(provider);
        return em.persist(business);
    }

    public ActivityType type(String name) {
        ActivityType type = new ActivityType();
        type.setName(name);
        return em.persist(type);
    }

    public Activity activity(Business business, String name, Department department, String locality,
                             String price, ActivityStatus status, ActivityType... types) {
        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setName(name);
        activity.setDescription("Descripción de " + name);
        activity.setAddress("Punto de encuentro");
        activity.setDepartment(department);
        activity.setLocality(locality);
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal(price));
        activity.setStatus(status);
        // Una actividad necesita al menos un tipo
        activity.setActivityTypes(types.length == 0 ? Set.of(type("Tipo " + SEQUENCE.incrementAndGet())) : Set.of(types));
        return em.persist(activity);
    }

    public ActivitySchedule schedule(Activity activity, LocalDateTime start, ScheduleStatus status) {
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(start);
        schedule.setCapacity(10);
        schedule.setStatus(status);
        return em.persist(schedule);
    }

    public Booking booking(Tourist tourist, ActivitySchedule schedule, BookingStatus status, int participants) {
        Booking booking = new Booking();
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setStatus(status);
        booking.setParticipantCount(participants);
        booking.setTotalAmount(schedule.getActivity().getPricePerPerson().multiply(BigDecimal.valueOf(participants)));
        booking.setHeldUntil(status == BookingStatus.PENDING ? LocalDateTime.now().plusMinutes(10) : null);
        return em.persist(booking);
    }

    public Review review(Booking booking, int rating, LocalDateTime createdAt) {
        Review review = new Review();
        review.setBooking(booking);
        review.setTourist(booking.getTourist());
        review.setActivity(booking.getActivitySchedule().getActivity());
        review.setRating(rating);
        review.setComment("Comentario " + rating);
        review.setCreatedAt(createdAt);
        return em.persist(review);
    }

    // Una reseña de un turista nuevo sobre un horario nuevo de la actividad
    public Review reviewOf(Activity activity, int rating, LocalDateTime createdAt) {
        // Un minuto distinto cada vez: no puede haber dos horarios iguales de la misma actividad
        LocalDateTime start = createdAt.minusDays(1).minusMinutes(SEQUENCE.incrementAndGet());
        ActivitySchedule schedule = schedule(activity, start, ScheduleStatus.FINISHED);
        Booking booking = booking(tourist("Turista", "Prueba"), schedule, BookingStatus.CONFIRMED, 1);
        return review(booking, rating, createdAt);
    }

    private <T extends User> T fill(T user) {
        int n = SEQUENCE.incrementAndGet();
        user.setEmail("usuario" + n + "@ejemplo.test");
        // No es una contraseña: el campo es obligatorio y estos usuarios nunca inician sesión
        user.setPasswordHash("sin-uso");
        user.setName("Nombre" + n);
        user.setLastName("Apellido");
        user.setDateOfBirth(LocalDate.of(1990, 1, 1));
        user.setDocumentType(DocumentType.CI);
        user.setDocumentNumber("1234567" + n);
        return user;
    }
}
