package uy.edu.um.nexo.mnatural.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.dto.ScheduleAvailabilityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.entities.ScheduleStatus;
import uy.edu.um.nexo.mnatural.repository.ActivityCatalogRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Portal público de actividades (ESPECIFICACION.md, sección 2): listado con filtros y detalle
@Service
public class ActivityCatalogService {

    private final ActivityCatalogRepository catalogRepository;
    private final BookingService bookingService;

    public ActivityCatalogService(ActivityCatalogRepository catalogRepository, BookingService bookingService) {
        this.catalogRepository = catalogRepository;
        this.bookingService = bookingService;
    }

    // Actividades publicadas que cumplen todos los filtros indicados. Los filtros vacíos se ignoran
    @Transactional(readOnly = true)
    public List<Activity> search(ActivityFilterDTO filter) {
        List<Specification<Activity>> conditions = new ArrayList<>();
        conditions.add(isPublished());

        if (filter.getDepartment() != null) {
            conditions.add(inDepartment(filter.getDepartment()));
        }
        if (filter.getLocality() != null && !filter.getLocality().isBlank()) {
            conditions.add(localityContains(filter.getLocality().trim()));
        }
        if (filter.getActivityTypeId() != null) {
            conditions.add(hasType(filter.getActivityTypeId()));
        }
        if (filter.getDate() != null) {
            conditions.add(hasScheduleOn(filter.getDate(), LocalDateTime.now()));
        }

        // Si el visitante escribe el rango al revés (desde 2000 hasta 500), se da vuelta
        BigDecimal minPrice = filter.getMinPrice();
        BigDecimal maxPrice = filter.getMaxPrice();
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            BigDecimal swap = minPrice;
            minPrice = maxPrice;
            maxPrice = swap;
        }
        if (minPrice != null) {
            conditions.add(priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            conditions.add(priceAtMost(maxPrice));
        }

        return catalogRepository.findAll(Specification.allOf(conditions), Sort.by("name"));
    }

    // Solo se puede ver el detalle de una actividad publicada
    @Transactional(readOnly = true)
    public Optional<Activity> findPublished(Long activityId) {
        return catalogRepository.findByIdAndStatus(activityId, ActivityStatus.PUBLISHED);
    }

    // Próximas fechas con sus lugares libres. Los lugares salen de BookingService
    // (reservas confirmadas y pendientes vigentes), no del campo availableSpots de ActivitySchedule
    @Transactional(readOnly = true)
    public List<ScheduleAvailabilityDTO> upcomingSchedules(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduleAvailabilityDTO> result = new ArrayList<>();
        for (ActivitySchedule schedule : catalogRepository.findUpcomingSchedules(activity.getId(), now)) {
            // Mismo límite que valida BookingService al reservar
            LocalDateTime deadline = schedule.getStartDateTime().minusMinutes(activity.getBookingCutoffMinutes());
            boolean bookingOpen = now.isBefore(deadline);
            int spots = bookingService.availableSpots(schedule.getId());
            result.add(new ScheduleAvailabilityDTO(schedule, spots, bookingOpen));
        }
        return result;
    }

    private static Specification<Activity> isPublished() {
        return (root, query, cb) -> cb.equal(root.get("status"), ActivityStatus.PUBLISHED);
    }

    private static Specification<Activity> inDepartment(Department department) {
        return (root, query, cb) -> cb.equal(root.get("department"), department);
    }

    // Sin distinguir mayúsculas. Los tildes sí cuentan: "piriapolis" no encuentra "Piriápolis"
    private static Specification<Activity> localityContains(String text) {
        return (root, query, cb) -> cb.like(cb.lower(root.get("locality")), "%" + text.toLowerCase() + "%");
    }

    // Con una subconsulta, para no repetir la actividad si tiene varios tipos
    private static Specification<Activity> hasType(Long activityTypeId) {
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Activity> other = sub.correlate(root);
            Join<Activity, ActivityType> type = other.join("activityTypes");
            sub.select(type.get("id")).where(cb.equal(type.get("id"), activityTypeId));
            return cb.exists(sub);
        };
    }

    // Que tenga al menos un horario ese día, no suspendido y que todavía no haya empezado
    private static Specification<Activity> hasScheduleOn(LocalDate date, LocalDateTime now) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime from = dayStart.isAfter(now) ? dayStart : now;
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ActivitySchedule> schedule = sub.from(ActivitySchedule.class);
            sub.select(schedule.get("id")).where(
                    cb.equal(schedule.get("activity"), root),
                    cb.equal(schedule.get("status"), ScheduleStatus.SCHEDULED),
                    cb.greaterThan(schedule.get("startDateTime"), from),
                    cb.lessThan(schedule.get("startDateTime"), to));
            return cb.exists(sub);
        };
    }

    private static Specification<Activity> priceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("pricePerPerson"), minPrice);
    }

    private static Specification<Activity> priceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("pricePerPerson"), maxPrice);
    }
}
