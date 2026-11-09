package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uy.edu.um.nexo.mnatural.dto.BookingRequestDTO;
import uy.edu.um.nexo.mnatural.dto.ParticipantDTO;
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
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.NotEnoughSpotsException;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// El bloqueo de fila lo hace PostgreSQL y las reservas de cada hilo se tienen que guardar de verdad,
// así que esto no se puede probar con mocks ni con rollback. Por eso usa un esquema aparte de la
// base de Docker: nunca toca las tablas de la aplicación y sus tablas se borran al terminar
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=booking_concurrency_test",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class BookingServiceConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private ActivityScheduleRepository activityScheduleRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityTypeRepository activityTypeRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private TouristRepository touristRepository;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private int touristCount;
    private Activity activity;
    private ActivitySchedule schedule;

    @BeforeEach
    void setUp() {
        Provider provider = new Provider();
        fillPerson(provider, "prestador-" + suffix + "@test.uy");
        provider.setApprovalStatus(ApprovalStatus.APPROVED);
        provider = providerRepository.save(provider);

        Business business = new Business();
        business.setName("Emprendimiento " + suffix);
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle de Prueba 123");
        business.setPhoneNumber("099000000");
        business.setEmail(provider.getEmail());
        business.setProvider(provider);
        business = businessRepository.save(business);

        ActivityType activityType = new ActivityType();
        activityType.setName("Tipo " + suffix);
        activityType = activityTypeRepository.save(activityType);

        activity = new Activity();
        activity.setName("Actividad " + suffix);
        activity.setDescription("Actividad de prueba");
        activity.setAddress("Punto de encuentro");
        activity.setDepartment(Department.values()[0]);
        activity.setLocality("Localidad");
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("500.00"));
        activity.setCancellationAllowed(false);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setBusiness(business);
        activity.getActivityTypes().add(activityType);
        activity = activityRepository.save(activity);
    }

    @Test
    void aBookingHoldsItsSpotsInTheDatabase() {
        createSchedule(5);

        bookingService.createBooking(newTourist(), request(3));

        assertThat(bookingService.availableSpots(schedule.getId())).isEqualTo(2);
    }

    // Dos turistas piden 2 lugares cada uno al mismo tiempo y solo quedan 3:
    // gracias al bloqueo, uno reserva y el otro recibe NotEnoughSpotsException
    @Test
    void twoSimultaneousBookingsCannotOversell() throws Exception {
        createSchedule(3);

        List<Object> results = runAtTheSameTime(2, 2);

        assertThat(results).filteredOn(result -> result instanceof Booking).hasSize(1);
        assertThat(results).filteredOn(result -> result instanceof NotEnoughSpotsException).hasSize(1);
        assertThat(bookingService.availableSpots(schedule.getId())).isEqualTo(1);
    }

    // Lo mismo con más turistas a la vez: 8 piden 1 lugar y solo hay 5
    @Test
    void manySimultaneousBookingsFillExactlyTheCapacity() throws Exception {
        createSchedule(5);

        List<Object> results = runAtTheSameTime(8, 1);

        assertThat(results).filteredOn(result -> result instanceof Booking).hasSize(5);
        assertThat(results).filteredOn(result -> result instanceof NotEnoughSpotsException).hasSize(3);
        assertThat(bookingService.availableSpots(schedule.getId())).isZero();
    }

    @Test
    void expiredHoldReleasesSpots() {
        createSchedule(2);
        Booking booking = bookingService.createBooking(newTourist(), request(2));
        assertThat(bookingService.availableSpots(schedule.getId())).isZero();

        // Se simula que pasaron los 10 minutos
        booking.setHeldUntil(LocalDateTime.now().minusMinutes(1));
        bookingRepository.save(booking);

        assertThat(bookingService.availableSpots(schedule.getId())).isEqualTo(2);
        assertThat(bookingService.expireHeldBookings()).isGreaterThanOrEqualTo(1);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.EXPIRED);
    }

    // Lanza "threads" reservas de "spotsEach" lugares a la vez. Devuelve la reserva creada
    // o la excepción de cada una
    private List<Object> runAtTheSameTime(int threads, int spotsEach) throws Exception {
        List<Tourist> bookers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            bookers.add(newTourist());
        }

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Tourist tourist : bookers) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return bookingService.createBooking(tourist, request(spotsEach));
                    } catch (NotEnoughSpotsException e) {
                        return e;
                    }
                }));
            }
            start.countDown();

            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private void createSchedule(int capacity) {
        schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(7).withNano(0));
        schedule.setCapacity(capacity);
        schedule = activityScheduleRepository.save(schedule);
    }

    // El titular participa y suma "totalParticipants - 1" acompañantes
    private BookingRequestDTO request(int totalParticipants) {
        BookingRequestDTO dto = new BookingRequestDTO();
        dto.setActivityScheduleId(schedule.getId());
        dto.setHolderParticipates(true);
        for (int i = 1; i < totalParticipants; i++) {
            ParticipantDTO participant = new ParticipantDTO();
            participant.setName("Acompañante");
            participant.setLastName(String.valueOf(i));
            participant.setDocumentType(DocumentType.CI);
            participant.setDocumentNumber("1234567" + i);
            participant.setDateOfBirth(LocalDate.of(1995, 5, 5));
            dto.getParticipants().add(participant);
        }
        return dto;
    }

    private Tourist newTourist() {
        Tourist tourist = new Tourist();
        fillPerson(tourist, "turista-" + touristCount++ + "-" + suffix + "@test.uy");
        return touristRepository.save(tourist);
    }

    private void fillPerson(uy.edu.um.nexo.mnatural.entities.User user, String email) {
        user.setEmail(email);
        user.setPasswordHash("no-se-usa");
        user.setName("Prueba");
        user.setLastName("Reserva");
        user.setDateOfBirth(LocalDate.of(1990, 1, 1));
        user.setDocumentType(DocumentType.CI);
        user.setDocumentNumber("00000000");
    }
}
