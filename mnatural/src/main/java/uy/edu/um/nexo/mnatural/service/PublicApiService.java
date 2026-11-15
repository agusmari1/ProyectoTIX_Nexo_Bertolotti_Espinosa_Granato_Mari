package uy.edu.um.nexo.mnatural.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
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
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.exception.InvalidApiRequestException;
import uy.edu.um.nexo.mnatural.repository.ActivityCatalogRepository;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.PublicApiRepository;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// API pública para otras plataformas (ESPECIFICACION.md, sección 9). Devuelve siempre DTOs,
// y solo actividades publicadas de prestadores aprobados
@Service
public class PublicApiService {

    private static final int MAX_KEYWORD_LENGTH = ActivityKeywordSearch.MAX_LENGTH;
    private static final int TOP_SIZE = 3;

    private final PublicApiRepository apiRepository;
    private final ActivityCatalogRepository catalogRepository;
    private final BusinessRepository businessRepository;
    private final BookingService bookingService;
    private final ReviewRepository reviewRepository;

    public PublicApiService(PublicApiRepository apiRepository,
                            ActivityCatalogRepository catalogRepository,
                            BusinessRepository businessRepository,
                            BookingService bookingService,
                            ReviewRepository reviewRepository) {
        this.apiRepository = apiRepository;
        this.catalogRepository = catalogRepository;
        this.businessRepository = businessRepository;
        this.bookingService = bookingService;
        this.reviewRepository = reviewRepository;
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

    // Hasta 3 actividades publicadas del departamento con mejor promedio de las reseñas visibles publicadas en
    // el mes pedido (por defecto, el mes en curso), desempatando por mayor cantidad de valoraciones.
    // Las reseñas eliminadas no cuentan y una actividad sin reseñas ese mes no entra al ranking
    @Transactional(readOnly = true)
    public List<ApiTopActivityDTO> findTopOfMonth(Department department, YearMonth month) {
        YearMonth period = month != null ? month : YearMonth.now();
        List<Object[]> rows = reviewRepository.rankActivities(department,
                period.atDay(1).atStartOfDay(), period.plusMonths(1).atDay(1).atStartOfDay(),
                PageRequest.of(0, TOP_SIZE));
        return rows.stream()
                .map(row -> new ApiTopActivityDTO(ApiActivityDTO.from((Activity) row[0]),
                        Math.round(((Double) row[1]) * 100.0) / 100.0, (Long) row[2]))
                .toList();
    }

    // Misma consulta para el mes actual
    @Transactional(readOnly = true)
    public List<ApiTopActivityDTO> findTopOfMonth(Department department) {
        return findTopOfMonth(department, null);
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

    // La condición de texto es la misma que usa el buscador del portal (ActivityKeywordSearch)
    private static Specification<Activity> matchesKeyword(String keyword) {
        Specification<Activity> text = ActivityKeywordSearch.matches(keyword);
        return (root, query, cb) -> cb.and(isPublic(root, cb), text.toPredicate(root, query, cb));
    }

    // Lo único que puede ver la API: actividades publicadas de prestadores aprobados
    private static Predicate isPublic(Path<Activity> activity, CriteriaBuilder cb) {
        return cb.and(
                cb.equal(activity.get("status"), ActivityStatus.PUBLISHED),
                cb.equal(activity.get("business").get("provider").get("approvalStatus"), ApprovalStatus.APPROVED));
    }

    private static Predicate contains(CriteriaBuilder cb, Expression<String> column, String text) {
        return ActivityKeywordSearch.contains(cb, column, text);
    }
}
