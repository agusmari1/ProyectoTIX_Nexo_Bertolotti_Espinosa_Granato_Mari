package uy.edu.um.nexo.mnatural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.Participant;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.PaymentType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.BookingRepository;
import uy.edu.um.nexo.mnatural.repository.PaymentTransactionRepository;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Datos de demostración para mostrar las reseñas EN VIVO: turistas que ya fueron a actividades (reservas confirmadas con
// su pago aprobado sobre horarios que ya pasaron), varias reseñas ya publicadas (con y sin comentario, con puntajes
// distintos y algunas con respuesta del prestador) y, para el turista "turistaprueba", reservas pasadas SIN reseñar para
// poder valorarlas durante la demostración.
// Crea usuarios con una contraseña conocida, por eso está APAGADO por defecto: solo corre con
// app.seed.demo-past-bookings=true (o la variable de entorno APP_SEED_DEMO_PAST_BOOKINGS=true). Nunca activarlo en una
// entrega. Necesita al prestador aprobado de prueba y sus actividades de demostración
// (app.seed.test-providers=true y app.seed.demo-activities=true); si faltan, avisa en el log y no hace nada.
// Es idempotente: cada reserva lleva un código fijo (MN-DEMO-01...) y las que ya existen no se vuelven a crear.
// Inserta con los repositorios porque el servicio de reservas no deja crear horarios en el pasado
@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed.demo-past-bookings", havingValue = "true")
public class DemoPastBookingsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoPastBookingsSeeder.class);

    static final String DEFAULT_PROVIDER_EMAIL = "aprobadoprueba@gmail.com";
    static final String DEFAULT_TOURIST_EMAIL = "turistaprueba@gmail.com";
    static final String DEFAULT_CODE_PREFIX = "MN-DEMO-";
    private static final String PASSWORD = "prueba1234";

    // Los turistas de demostración. El primero (índice 0) es el de la demo en vivo: no tiene ninguna reseña hecha
    private record DemoTourist(String name, String lastName, String email) {
    }

    // Una reserva pasada. rating 0 = sin reseña (para valorarla en vivo)
    private record DemoBooking(int tourist, int activity, int daysAgo, int people, int rating, String comment,
                               String reply) {
    }

    private static final List<String> OTHER_EMAILS = List.of("ana.perez", "martin.souza", "lucia.fernandez",
            "diego.alvarez", "carla.rodriguez");
    private static final List<String> COMPANIONS = List.of("Sofía", "Mateo", "Valentina", "Joaquín");

    private static final List<DemoBooking> BOOKINGS = List.of(
            // Reseñas ya publicadas (turistas 1 a 5)
            new DemoBooking(1, 0, 8, 2, 5, "¡Una experiencia increíble! El guía conocía cada rincón y el paisaje era "
                    + "hermoso. Volvería mañana.", "¡Muchas gracias, Ana! Los esperamos de nuevo."),
            new DemoBooking(2, 0, 10, 1, 4, "Muy buena actividad y bien organizada. Solo faltó un poco más de tiempo "
                    + "para sacar fotos.", null),
            new DemoBooking(3, 0, 12, 2, 5, "Excelente atención y muy puntuales. Lo recomiendo.", null),
            new DemoBooking(1, 1, 14, 3, 5, "Lo mejor del viaje. Totalmente recomendable para ir en familia.",
                    "Nos alegra mucho que hayan disfrutado en familia. ¡Gracias por elegirnos!"),
            new DemoBooking(4, 1, 16, 2, 4, "Hermoso lugar y buen precio.", null),
            new DemoBooking(2, 2, 18, 1, 3, "Estuvo bien, aunque el día estaba nublado y se vio menos de lo esperado.",
                    "Gracias por tu comentario, Martín. Con mejor clima la vista cambia mucho: te invitamos a volver."),
            new DemoBooking(5, 2, 20, 2, 4, "Buena experiencia, el equipo fue muy amable.", null),
            new DemoBooking(3, 3, 22, 2, 5, "Súper bien organizado, repetiría sin dudarlo.", null),
            new DemoBooking(4, 3, 24, 4, 5, "Superó lo que esperaba. Muy lindo para ir con amigos.", null),
            new DemoBooking(5, 4, 26, 2, 2, "No cumplió mis expectativas: había mucha gente y poco tiempo para disfrutar.",
                    "Lamentamos que no haya sido la experiencia que esperabas. Tomamos nota para mejorar."),
            new DemoBooking(1, 5, 28, 1, 4, null, null),
            new DemoBooking(2, 6, 30, 2, 5, "Un plan perfecto para el fin de semana. Gracias por todo.", null),
            // Para la demostración en vivo: el turista de prueba fue a estas actividades y todavía no las valoró
            new DemoBooking(0, 7, 9, 2, 0, null, null),
            new DemoBooking(0, 8, 13, 1, 0, null, null),
            new DemoBooking(0, 9, 17, 3, 0, null, null));

    private final UserRepository userRepository;
    private final TouristRepository touristRepository;
    private final ActivityRepository activityRepository;
    private final ActivityScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final String providerEmail;
    private final String touristEmail;
    private final String codePrefix;

    // Los tres últimos valores solo cambian en los tests, para no chocar con datos que ya haya en la base
    public DemoPastBookingsSeeder(UserRepository userRepository,
                                  TouristRepository touristRepository,
                                  ActivityRepository activityRepository,
                                  ActivityScheduleRepository scheduleRepository,
                                  BookingRepository bookingRepository,
                                  PaymentTransactionRepository paymentRepository,
                                  ReviewRepository reviewRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${app.seed.demo-past-bookings.provider-email:" + DEFAULT_PROVIDER_EMAIL + "}")
                                  String providerEmail,
                                  @Value("${app.seed.demo-past-bookings.tourist-email:" + DEFAULT_TOURIST_EMAIL + "}")
                                  String touristEmail,
                                  @Value("${app.seed.demo-past-bookings.code-prefix:" + DEFAULT_CODE_PREFIX + "}")
                                  String codePrefix) {
        this.userRepository = userRepository;
        this.touristRepository = touristRepository;
        this.activityRepository = activityRepository;
        this.scheduleRepository = scheduleRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.providerEmail = providerEmail;
        this.touristEmail = touristEmail;
        this.codePrefix = codePrefix;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Provider provider = userRepository.findByEmail(providerEmail)
                .filter(Provider.class::isInstance).map(Provider.class::cast)
                .filter(found -> found.getApprovalStatus() == ApprovalStatus.APPROVED)
                .orElse(null);
        if (provider == null) {
            log.warn("No se cargaron reservas pasadas de demostración: no hay un prestador aprobado ({}). "
                    + "Activá también app.seed.test-providers=true.", providerEmail);
            return;
        }
        // Las actividades publicadas del prestador, siempre en el mismo orden (por id)
        List<Activity> activities = activityRepository.findByBusinessProviderIdOrderByCreatedAtDesc(provider.getId())
                .stream()
                .filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED)
                .sorted(Comparator.comparing(Activity::getId))
                .toList();
        if (activities.isEmpty()) {
            log.warn("No se cargaron reservas pasadas de demostración: el prestador {} no tiene actividades publicadas. "
                    + "Activá también app.seed.demo-activities=true.", providerEmail);
            return;
        }

        List<Tourist> tourists = demoTourists();
        int created = 0;
        int reviews = 0;
        for (int i = 0; i < BOOKINGS.size(); i++) {
            DemoBooking demo = BOOKINGS.get(i);
            String code = codePrefix + String.format("%02d", i + 1);
            if (bookingRepository.existsByBookingCode(code)) {
                continue;
            }
            Activity activity = activities.get(demo.activity() % activities.size());
            Tourist tourist = tourists.get(demo.tourist());
            Booking booking = createBooking(code, i, demo, activity, tourist);
            created++;
            if (demo.rating() > 0) {
                createReview(booking, activity, tourist, demo, i);
                reviews++;
            }
        }
        log.info("Reservas pasadas de demostración: {} creadas ({} con reseña), {} ya existían. "
                        + "El turista {} puede valorar las que no tienen reseña (contraseña {})",
                created, reviews, BOOKINGS.size() - created, touristEmail, PASSWORD);
    }

    // El turista de la demo (turistaprueba) y los otros que escribieron las reseñas. Cada uno solo si no existe
    private List<Tourist> demoTourists() {
        String passwordHash = passwordEncoder.encode(PASSWORD);
        List<Tourist> result = new ArrayList<>();
        result.add(touristOf(new DemoTourist("Turista", "Prueba", touristEmail), passwordHash));
        String[][] others = {{"Ana", "Pérez"}, {"Martín", "Souza"}, {"Lucía", "Fernández"}, {"Diego", "Álvarez"},
                {"Carla", "Rodríguez"}};
        for (int i = 0; i < others.length; i++) {
            // Mismo dominio que el resto de los datos de prueba: no son direcciones reales
            String email = OTHER_EMAILS.get(i) + "@demo.mnatural.test";
            result.add(touristOf(new DemoTourist(others[i][0], others[i][1], email), passwordHash));
        }
        return result;
    }

    private Tourist touristOf(DemoTourist demo, String passwordHash) {
        return userRepository.findByEmail(demo.email())
                .filter(Tourist.class::isInstance).map(Tourist.class::cast)
                .orElseGet(() -> {
                    Tourist tourist = new Tourist();
                    tourist.setEmail(demo.email());
                    tourist.setPasswordHash(passwordHash);
                    tourist.setName(demo.name());
                    tourist.setLastName(demo.lastName());
                    tourist.setDateOfBirth(LocalDate.of(1992, 6, 15));
                    tourist.setDocumentType(DocumentType.CI);
                    tourist.setDocumentNumber(String.valueOf(40000000 + Math.abs(demo.email().hashCode()) % 9999999));
                    tourist.setCountry("Uruguay");
                    tourist.setLanguage("es");
                    return touristRepository.save(tourist);
                });
    }

    private Booking createBooking(String code, int index, DemoBooking demo, Activity activity, Tourist tourist) {
        // Un horario que ya pasó (a las 10:00, con un minuto distinto por reserva para que nunca se repita)
        LocalDateTime start = LocalDate.now().minusDays(demo.daysAgo()).atTime(10, index);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(start);
        schedule.setCapacity(Math.max(10, demo.people()));
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        scheduleRepository.save(schedule);

        BigDecimal total = activity.getPricePerPerson().multiply(BigDecimal.valueOf(demo.people()));
        Booking booking = new Booking();
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setParticipantCount(demo.people());
        booking.setHolderParticipates(true);
        booking.setTotalAmount(total);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setBookingCode(code);
        booking.setCreatedAt(start.minusDays(3));
        // El titular participa y también se carga como un participante más (igual que en una reserva real)
        booking.getParticipants().add(participant(booking, tourist.getName(), tourist.getLastName(),
                tourist.getDocumentType(), tourist.getDocumentNumber()));
        for (int p = 1; p < demo.people(); p++) {
            booking.getParticipants().add(participant(booking, COMPANIONS.get((p - 1) % COMPANIONS.size()),
                    tourist.getLastName(), DocumentType.CI, String.valueOf(50000000 + index * 10 + p)));
        }
        bookingRepository.save(booking);

        PaymentTransaction payment = new PaymentTransaction();
        payment.setBooking(booking);
        payment.setAmount(total);
        payment.setType(PaymentType.CHARGE);
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setAttemptNumber(1);
        payment.setExternalReference("demo-" + code);
        payment.setCardBrand("visa");
        payment.setCardLastFour("3704");
        payment.setCreatedAt(start.minusDays(3));
        paymentRepository.save(payment);
        return booking;
    }

    private Participant participant(Booking booking, String name, String lastName, DocumentType type, String number) {
        Participant participant = new Participant();
        participant.setBooking(booking);
        participant.setName(name);
        participant.setLastName(lastName);
        participant.setDocumentType(type);
        participant.setDocumentNumber(number);
        participant.setDateOfBirth(LocalDate.of(1990, 3, 10));
        return participant;
    }

    private void createReview(Booking booking, Activity activity, Tourist tourist, DemoBooking demo, int index) {
        // Publicadas en el mes en curso (después de ir a la actividad), así también aparecen en el top 3 de la API
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime createdAt = now.minusHours(index * 5L).truncatedTo(ChronoUnit.MINUTES);
        if (createdAt.isBefore(monthStart)) {
            createdAt = monthStart.plusMinutes(index);
        }
        Review review = new Review();
        review.setBooking(booking);
        review.setActivity(activity);
        review.setTourist(tourist);
        review.setRating(demo.rating());
        review.setComment(demo.comment());
        review.setStatus(ReviewStatus.VISIBLE);
        review.setCreatedAt(createdAt);
        if (demo.reply() != null) {
            review.setProviderReply(demo.reply());
            review.setProviderRepliedAt(createdAt.plusHours(2));
        }
        reviewRepository.save(review);
    }
}
