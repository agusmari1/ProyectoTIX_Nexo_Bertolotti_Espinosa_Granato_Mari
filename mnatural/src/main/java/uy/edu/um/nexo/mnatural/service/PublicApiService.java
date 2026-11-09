package uy.edu.um.nexo.mnatural.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ApiActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ApiAvailableActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ApiBusinessDTO;
import uy.edu.um.nexo.mnatural.dto.ApiBusinessStatusDTO;
import uy.edu.um.nexo.mnatural.dto.ApiScheduleDTO;
import uy.edu.um.nexo.mnatural.dto.ApiTopActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.exception.InvalidApiRequestException;
import uy.edu.um.nexo.mnatural.repository.ActivityCatalogRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.PublicApiRepository;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// API pública para otras plataformas (ESPECIFICACION.md, sección 9). Devuelve siempre DTOs,
// y solo actividades publicadas de prestadores aprobados
@Service
public class PublicApiService {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private final PublicApiRepository apiRepository;
    private final ActivityCatalogRepository catalogRepository;
    private final BusinessRepository businessRepository;
    private final BookingService bookingService;

    public PublicApiService(PublicApiRepository apiRepository,
                            ActivityCatalogRepository catalogRepository,
                            BusinessRepository businessRepository,
                            BookingService bookingService) {
        this.apiRepository = apiRepository;
        this.catalogRepository = catalogRepository;
        this.businessRepository = businessRepository;
        this.bookingService = bookingService;
    }

    @Transactional(readOnly = true)
    public List<ApiBusinessDTO> findBusinesses() {
        return apiRepository.findBusinessesOfApprovedProviders().stream()
                .map(ApiBusinessDTO::from)
                .toList();
    }

    // Que un emprendimiento no exista también es una respuesta válida, no un error
    @Transactional(readOnly = true)
    public ApiBusinessStatusDTO businessStatus(Long businessId) {
        return businessRepository.findById(businessId)
                .map(this::statusOf)
                .orElseGet(ApiBusinessStatusDTO::notFound);
    }

    private ApiBusinessStatusDTO statusOf(Business business) {
        boolean approved = business.getProvider().getApprovalStatus() == ApprovalStatus.APPROVED;
        return approved ? ApiBusinessStatusDTO.approved(business.getName()) : ApiBusinessStatusDTO.notApproved();
    }

    // Actividades que todavía se pueden reservar ese día, con sus horarios y lugares libres.
    // Un horario queda afuera si ya pasó el límite para reservar o si no le quedan lugares
    @Transactional(readOnly = true)
    public List<ApiAvailableActivityDTO> findAvailable(LocalDate date, Department department, String locality) {
        boolean hasLocality = locality != null && !locality.isBlank();
        if (department == null && !hasLocality) {
            throw new InvalidApiRequestException("Indicá un departamento (department) o una localidad (locality).");
        }

        List<Specification<ActivitySchedule>> conditions = new ArrayList<>();
        conditions.add(publicScheduleOn(date));
        if (department != null) {
            conditions.add(scheduleInDepartment(department));
        }
        if (hasLocality) {
            conditions.add(scheduleInLocality(locality));
        }

        LocalDateTime now = LocalDateTime.now();
        // LinkedHashMap: conserva el orden por hora de los horarios
        Map<Long, ApiAvailableActivityDTO> result = new LinkedHashMap<>();
        for (ActivitySchedule schedule : apiRepository.findAll(Specification.allOf(conditions), Sort.by("startDateTime"))) {
            Activity activity = schedule.getActivity();
            // Mismo límite que valida BookingService al reservar
            LocalDateTime deadline = schedule.getStartDateTime().minusMinutes(activity.getBookingCutoffMinutes());
            if (!now.isBefore(deadline)) {
                continue;
            }
            int spots = bookingService.availableSpots(schedule.getId());
            if (spots > 0) {
                result.computeIfAbsent(activity.getId(),
                                id -> new ApiAvailableActivityDTO(ApiActivityDTO.from(activity), new ArrayList<>()))
                        .schedules()
                        .add(new ApiScheduleDTO(schedule.getId(), schedule.getStartDateTime(), spots));
            }
        }
        return List.copyOf(result.values());
    }

    // TODO: las reseñas (ESPECIFICACION.md, sección 5) todavía no existen. Cuando existan, devolver las
    // 3 actividades publicadas del departamento con mejor promedio de las reseñas publicadas en el mes
    // calendario en curso, desempatando por mayor cantidad de valoraciones
    @Transactional(readOnly = true)
    public List<ApiTopActivityDTO> findTopOfMonth(Department department) {
        return List.of();
    }

    // Busca en el nombre, la descripción, la localidad y los tipos, sin distinguir mayúsculas ni tildes
    @Transactional(readOnly = true)
    public List<ApiActivityDTO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new InvalidApiRequestException("Indicá una palabra para buscar (q).");
        }
        if (keyword.trim().length() > MAX_KEYWORD_LENGTH) {
            throw new InvalidApiRequestException("La búsqueda no puede superar los " + MAX_KEYWORD_LENGTH + " caracteres.");
        }
        return catalogRepository.findAll(matchesKeyword(keyword), Sort.by("name")).stream()
                .map(ApiActivityDTO::from)
                .toList();
    }

    private static Specification<ActivitySchedule> publicScheduleOn(LocalDate date) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ScheduleStatus.SCHEDULED),
                cb.greaterThanOrEqualTo(root.get("startDateTime"), date.atStartOfDay()),
                cb.lessThan(root.get("startDateTime"), date.plusDays(1).atStartOfDay()),
                isPublic(root.get("activity"), cb));
    }

    private static Specification<ActivitySchedule> scheduleInDepartment(Department department) {
        return (root, query, cb) -> cb.equal(root.get("activity").get("department"), department);
    }

    private static Specification<ActivitySchedule> scheduleInLocality(String locality) {
        return (root, query, cb) -> contains(cb, root.get("activity").get("locality"), locality);
    }

    private static Specification<Activity> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            // Con una subconsulta, para no repetir la actividad si tiene varios tipos
            Subquery<Long> typeMatch = query.subquery(Long.class);
            Root<Activity> sameActivity = typeMatch.correlate(root);
            Join<Activity, ActivityType> type = sameActivity.join("activityTypes");
            typeMatch.select(type.get("id")).where(contains(cb, type.get("name"), keyword));

            return cb.and(
                    isPublic(root, cb),
                    cb.or(contains(cb, root.get("name"), keyword),
                            contains(cb, root.get("description"), keyword),
                            contains(cb, root.get("locality"), keyword),
                            cb.exists(typeMatch)));
        };
    }

    // Lo único que puede ver la API: actividades publicadas de prestadores aprobados
    private static Predicate isPublic(Path<Activity> activity, CriteriaBuilder cb) {
        return cb.and(
                cb.equal(activity.get("status"), ActivityStatus.PUBLISHED),
                cb.equal(activity.get("business").get("provider").get("approvalStatus"), ApprovalStatus.APPROVED));
    }

    // translate le saca los tildes a la columna en PostgreSQL y simplify al texto buscado.
    // La barra escapa % y _ para que se busquen como texto y no como comodines
    private static Predicate contains(CriteriaBuilder cb, Expression<String> column, String text) {
        Expression<String> withoutAccents = cb.function("translate", String.class,
                cb.lower(column), cb.literal("áéíóúüñ"), cb.literal("aeiouun"));
        String escaped = simplify(text).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(withoutAccents, "%" + escaped + "%", '\\');
    }

    private static String simplify(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
