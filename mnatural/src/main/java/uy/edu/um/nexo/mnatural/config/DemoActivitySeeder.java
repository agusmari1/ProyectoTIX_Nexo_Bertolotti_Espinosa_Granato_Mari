package uy.edu.um.nexo.mnatural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Llena la base con actividades de demostración (3 por departamento, con horarios futuros) para probar el portal y
// mostrarlo. Está APAGADO por defecto: solo corre con app.seed.demo-activities=true. Las actividades se asignan al
// prestador de prueba aprobado (el que crea TestProviderSeeder con app.seed.test-providers=true), y los tipos que
// falten se crean. No duplica nada si se corre más de una vez (compara por nombre de actividad).
// Los datos son de ejemplo: nunca activarlo en una entrega real.
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.demo-activities", havingValue = "true")
public class DemoActivitySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoActivitySeeder.class);

    static final String DEMO_PROVIDER_EMAIL = "aprobadoprueba@gmail.com";

    // Horarios que se crean para cada actividad: (días desde hoy, hora)
    static final int[][] SCHEDULES = {{3, 10}, {7, 15}, {14, 10}, {21, 16}};
    static final int CAPACITY = 12;

    record Demo(String name, String description, String address, Department department, String locality,
                int durationMinutes, String price, Integer minAge, String requirements, String equipment,
                int bookingCutoffMinutes, Integer cancellationHours, String refundPercentage, List<String> types) {
    }

    static final List<Demo> DEMOS = List.of(
            // Maldonado (Punta del Este)
            new Demo("Paseo en velero por la bahía", "Navegación de dos horas por la bahía de Punta del Este con vista a la Isla Gorriti y la costa. Incluye chalecos salvavidas y una bebida a bordo.",
                    "Puerto de Punta del Este", Department.MALDONADO, "Punta del Este", 120, "2800", 6,
                    "Saber nadar no es obligatorio; los menores van acompañados.", "Ropa cómoda, protector solar y campera liviana.",
                    120, 48, "80", List.of("Aventura", "Naturaleza")),
            new Demo("Bicicleta por la costa esteña", "Recorrido guiado en bicicleta por la rambla, con paradas en los miradores y las playas más lindas.",
                    "Parada 5 de la Playa Mansa", Department.MALDONADO, "Punta del Este", 150, "1500", 12,
                    "Saber andar en bicicleta.", "Casco y bicicleta incluidos. Traer agua y protector solar.",
                    60, 24, "100", List.of("Aventura", "Naturaleza")),
            new Demo("Atardecer con degustación en Punta Ballena", "Caminata corta hasta el mirador y degustación de productos regionales mientras cae el sol sobre el mar.",
                    "Mirador de Punta Ballena", Department.MALDONADO, "Punta Ballena", 105, "1900", null,
                    null, "Calzado cómodo y una campera para la tarde.",
                    90, 24, "70", List.of("Gastronomía", "Relax")),

            // Colonia
            new Demo("Recorrido a pie por el Barrio Histórico", "Visita guiada por las calles empedradas, el Portón de Campo y las murallas del casco histórico de Colonia del Sacramento.",
                    "Portón de Campo", Department.COLONIA, "Colonia del Sacramento", 90, "900", null,
                    null, "Calzado cómodo, sombrero y agua.",
                    60, 24, "100", List.of("Cultura")),
            new Demo("Paseo en bicicleta por la rambla de Colonia", "Pedaleada tranquila junto al Río de la Plata, con parada para ver el atardecer.",
                    "Rambla de las Américas", Department.COLONIA, "Colonia del Sacramento", 120, "1100", 10,
                    "Saber andar en bicicleta.", "Bicicleta y casco incluidos.",
                    60, 24, "100", List.of("Aventura", "Naturaleza")),
            new Demo("Cata de vinos en una bodega coloniense", "Recorrido por la bodega, explicación del proceso de elaboración y degustación de tres vinos con una tabla de quesos.",
                    "Camino a las bodegas, Colonia", Department.COLONIA, "Colonia del Sacramento", 120, "2200", 18,
                    "Solo para mayores de 18 años.", null,
                    180, 72, "50", List.of("Gastronomía", "Cultura")),

            // Montevideo
            new Demo("Ciudad Vieja y Plaza Independencia", "Caminata guiada por la Ciudad Vieja: la Plaza Independencia, el Palacio Salvo, la Puerta de la Ciudadela y la peatonal Sarandí.",
                    "Plaza Independencia", Department.MONTEVIDEO, "Montevideo", 120, "800", null,
                    null, "Calzado cómodo y agua.",
                    60, 24, "100", List.of("Cultura")),
            new Demo("Recorrido gastronómico por el Mercado del Puerto", "Probamos los platos más típicos de las parrillas del Mercado del Puerto y conocemos su historia.",
                    "Mercado del Puerto", Department.MONTEVIDEO, "Montevideo", 150, "1600", null,
                    "Avisar con anticipación si hay alergias o dietas especiales.", null,
                    120, 48, "80", List.of("Gastronomía", "Cultura")),
            new Demo("Rambla de Montevideo en bicicleta", "Pedaleada por la rambla, desde Ciudad Vieja hasta Pocitos, con paradas para fotos y un café.",
                    "Rambla Sur y Gran Bretaña", Department.MONTEVIDEO, "Montevideo", 150, "1200", 10,
                    "Saber andar en bicicleta.", "Bicicleta y casco incluidos.",
                    60, 24, "100", List.of("Aventura", "Naturaleza")),

            // Salto
            new Demo("Jornada de relax en las Termas del Daymán", "Día completo en las termas con acceso a las piscinas de agua caliente y traslado desde la ciudad de Salto.",
                    "Termas del Daymán", Department.SALTO, "Termas del Daymán", 300, "1300", null,
                    null, "Malla, toalla y ojotas.",
                    120, 48, "80", List.of("Relax")),
            new Demo("Paseo en lancha por el río Uruguay", "Navegación por el río Uruguay para ver la costa y las islas, con explicación del guía sobre la fauna del lugar.",
                    "Puerto de Salto", Department.SALTO, "Salto", 90, "1000", 5,
                    null, "Protector solar y gorra.",
                    90, 24, "100", List.of("Naturaleza", "Aventura")),
            new Demo("Visita a la Represa de Salto Grande", "Recorrido guiado por la represa binacional y su mirador, con explicación de cómo se genera la energía.",
                    "Represa de Salto Grande", Department.SALTO, "Salto", 120, "700", 8,
                    "Llevar documento de identidad.", null,
                    120, 24, "100", List.of("Cultura")),

            // Rocha
            new Demo("Caminata por Cabo Polonio", "Recorrido a pie por las dunas, el faro y la costa del Cabo Polonio, un pueblo sin electricidad de red.",
                    "Entrada al Cabo Polonio", Department.ROCHA, "Cabo Polonio", 180, "1400", 8,
                    "Estado físico para caminar sobre arena.", "Calzado cerrado, agua, protector solar y campera.",
                    120, 48, "80", List.of("Aventura", "Naturaleza")),
            new Demo("Lobos marinos en el Cabo Polonio", "Visita al mirador de la colonia de lobos marinos, con explicación sobre su comportamiento.",
                    "Faro del Cabo Polonio", Department.ROCHA, "Cabo Polonio", 90, "900", null,
                    "Respetar la distancia con los animales.", "Cámara y ropa de abrigo.",
                    90, 24, "100", List.of("Naturaleza")),
            new Demo("Cabalgata frente al mar en La Paloma", "Paseo a caballo por la playa y el campo con guía, apto para principiantes.",
                    "Camino a la playa, La Paloma", Department.ROCHA, "La Paloma", 120, "1800", 10,
                    "Peso máximo de 95 kg.", "Pantalón largo y calzado cerrado. Casco incluido.",
                    180, 48, "70", List.of("Aventura", "Naturaleza")));

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ActivityRepository activityRepository;
    private final ActivityScheduleRepository scheduleRepository;
    private final ActivityTypeRepository typeRepository;

    public DemoActivitySeeder(UserRepository userRepository,
                              BusinessRepository businessRepository,
                              ActivityRepository activityRepository,
                              ActivityScheduleRepository scheduleRepository,
                              ActivityTypeRepository typeRepository) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.activityRepository = activityRepository;
        this.scheduleRepository = scheduleRepository;
        this.typeRepository = typeRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Business business = findDemoBusiness();
        if (business == null) {
            log.warn("No se cargaron actividades de demostración: no hay un prestador aprobado con emprendimiento ({}). "
                    + "Activá también app.seed.test-providers=true.", DEMO_PROVIDER_EMAIL);
            return;
        }

        Set<String> existingNames = new HashSet<>();
        activityRepository.findByBusinessProviderIdOrderByCreatedAtDesc(business.getProvider().getId())
                .forEach(activity -> existingNames.add(activity.getName()));
        Map<String, ActivityType> types = new HashMap<>();

        int created = 0;
        for (Demo demo : DEMOS) {
            if (existingNames.contains(demo.name())) {
                continue;
            }
            createActivity(business, demo, types);
            created++;
        }
        log.info("Actividades de demostración: {} creadas, {} ya existían", created, DEMOS.size() - created);
    }

    private Business findDemoBusiness() {
        if (userRepository.findByEmail(DEMO_PROVIDER_EMAIL).orElse(null) instanceof Provider provider
                && provider.getApprovalStatus() == ApprovalStatus.APPROVED) {
            return businessRepository.findByProviderId(provider.getId()).stream().findFirst().orElse(null);
        }
        return null;
    }

    private void createActivity(Business business, Demo demo, Map<String, ActivityType> typeCache) {
        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setName(demo.name());
        activity.setDescription(demo.description());
        activity.setAddress(demo.address());
        activity.setDepartment(demo.department());
        activity.setLocality(demo.locality());
        activity.setDurationMinutes(demo.durationMinutes());
        activity.setPricePerPerson(new BigDecimal(demo.price()));
        activity.setMinAge(demo.minAge());
        activity.setRequirements(demo.requirements());
        activity.setEquipment(demo.equipment());
        activity.setBookingCutoffMinutes(demo.bookingCutoffMinutes());
        boolean cancellable = demo.cancellationHours() != null;
        activity.setCancellationAllowed(cancellable);
        if (cancellable) {
            // El plazo de cancelación se guarda en minutos
            activity.setCancellationCutoffMinutes(demo.cancellationHours() * 60);
            activity.setRefundPercentage(new BigDecimal(demo.refundPercentage()));
        }
        activity.setStatus(ActivityStatus.PUBLISHED);
        Set<ActivityType> types = new HashSet<>();
        demo.types().forEach(name -> types.add(typeCache.computeIfAbsent(name, this::findOrCreateType)));
        activity.setActivityTypes(types);
        Activity saved = activityRepository.save(activity);

        LocalDate today = LocalDate.now();
        for (int[] when : SCHEDULES) {
            ActivitySchedule schedule = new ActivitySchedule();
            schedule.setActivity(saved);
            schedule.setStartDateTime(LocalDateTime.of(today.plusDays(when[0]), java.time.LocalTime.of(when[1], 0)));
            schedule.setCapacity(CAPACITY);
            scheduleRepository.save(schedule);
        }
    }

    // El tipo se busca ignorando mayúsculas; si no existe se crea habilitado
    private ActivityType findOrCreateType(String name) {
        return typeRepository.findAll().stream()
                .filter(type -> type.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    ActivityType type = new ActivityType();
                    type.setName(name);
                    type.setEnabled(true);
                    return typeRepository.save(type);
                });
    }
}
