package uy.edu.um.nexo.mnatural.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ActivityDTO;
import uy.edu.um.nexo.mnatural.dto.ActivityScheduleDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.repository.ActivityRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityScheduleRepository;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Alta de actividades y horarios desde el espacio del prestador.
// Todos los métodos reciben el email del usuario con sesión iniciada y verifican que sea
// un prestador aprobado; así nadie puede ver ni modificar actividades de otro prestador
@Service
public class ActivityService {

    private static final BigDecimal MAX_REFUND_PERCENTAGE = new BigDecimal("100");

    private final ActivityRepository activityRepository;
    private final ActivityScheduleRepository activityScheduleRepository;
    private final ActivityTypeRepository activityTypeRepository;
    private final UserRepository userRepository;

    public ActivityService(ActivityRepository activityRepository,
                           ActivityScheduleRepository activityScheduleRepository,
                           ActivityTypeRepository activityTypeRepository,
                           UserRepository userRepository) {
        this.activityRepository = activityRepository;
        this.activityScheduleRepository = activityScheduleRepository;
        this.activityTypeRepository = activityTypeRepository;
        this.userRepository = userRepository;
    }

    // Emprendimientos del prestador, para elegir a cuál pertenece la actividad
    @Transactional(readOnly = true)
    public List<Business> findBusinessesOf(String email) {
        List<Business> businesses = currentProvider(email).getBusinesses();
        // Se copia la lista para que la vista no dependa de la colección lazy de Provider
        return businesses.stream()
                .sorted(Comparator.comparing(Business::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // Tipos que el administrador dejó habilitados, por nombre
    @Transactional(readOnly = true)
    public List<ActivityType> findEnabledTypes() {
        return activityTypeRepository.findAll().stream()
                .filter(ActivityType::isEnabled)
                .sorted(Comparator.comparing(ActivityType::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Activity> findActivitiesOf(String email) {
        Provider provider = currentProvider(email);
        return activityRepository.findByBusinessProviderIdOrderByCreatedAtDesc(provider.getId());
    }

    @Transactional(readOnly = true)
    public Activity findOwnActivity(String email, Long activityId) {
        return ownActivity(currentProvider(email), activityId);
    }

    @Transactional(readOnly = true)
    public List<ActivitySchedule> findSchedules(String email, Long activityId) {
        Activity activity = ownActivity(currentProvider(email), activityId);
        return activityRepository.findSchedulesOf(activity.getId());
    }

    @Transactional
    public Activity create(String email, ActivityDTO dto) {
        Provider provider = currentProvider(email);
        Business business = resolveBusiness(provider, dto.getBusinessId());
        Set<ActivityType> types = resolveTypes(dto.getActivityTypeIds());
        validateActivity(dto);

        Activity activity = new Activity();
        activity.setBusiness(business);
        activity.setActivityTypes(types);
        activity.setName(dto.getName().trim());
        activity.setDescription(dto.getDescription().trim());
        activity.setAddress(dto.getAddress().trim());
        activity.setDepartment(dto.getDepartment());
        activity.setLocality(dto.getLocality().trim());
        activity.setDurationMinutes(dto.getDurationMinutes());
        activity.setPricePerPerson(dto.getPricePerPerson());
        activity.setMinAge(dto.getMinAge());
        activity.setRequirements(blankToNull(dto.getRequirements()));
        activity.setEquipment(blankToNull(dto.getEquipment()));
        activity.setBookingCutoffMinutes(dto.getBookingCutoffMinutes());
        activity.setCancellationAllowed(dto.isCancellationAllowed());
        // Si no admite cancelaciones, el plazo y el reembolso no aplican
        if (dto.isCancellationAllowed()) {
            activity.setCancellationCutoffHours(dto.getCancellationCutoffHours());
            activity.setRefundPercentage(dto.getRefundPercentage());
        }
        // No requiere aprobación del administrador (especificación, sección 6)
        activity.setStatus(ActivityStatus.PUBLISHED);
        return activityRepository.save(activity);
    }

    @Transactional
    public ActivitySchedule addSchedule(String email, Long activityId, ActivityScheduleDTO dto) {
        Activity activity = ownActivity(currentProvider(email), activityId);

        if (dto.getCapacity() == null || dto.getCapacity() <= 0) {
            throw new ActivityValidationException("capacity", "La cantidad de cupos tiene que ser mayor a 0.");
        }
        if (dto.getStartDateTime() == null || !dto.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new ActivityValidationException("startDateTime", "La fecha y hora tienen que ser futuras.");
        }
        boolean alreadyExists = activityRepository.findSchedulesOf(activity.getId()).stream()
                .anyMatch(s -> s.getStartDateTime().equals(dto.getStartDateTime()));
        if (alreadyExists) {
            throw duplicateSchedule();
        }

        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(dto.getStartDateTime());
        schedule.setCapacity(dto.getCapacity());
        try {
            // saveAndFlush para que, si otro pedido cargó el mismo horario al mismo tiempo,
            // la restricción única de la base salte acá y no al terminar la transacción
            return activityScheduleRepository.saveAndFlush(schedule);
        } catch (DataIntegrityViolationException e) {
            throw duplicateSchedule();
        }
    }

    // El usuario con sesión tiene que ser un prestador aprobado. El login ya no deja entrar
    // a los pendientes ni a los rechazados, pero se controla igual por si cambió de estado
    private Provider currentProvider(String email) {
        if (userRepository.findByEmail(email).orElse(null) instanceof Provider provider
                && provider.getApprovalStatus() == ApprovalStatus.APPROVED) {
            return provider;
        }
        throw new AccessDeniedException("Solo un prestador aprobado puede gestionar actividades.");
    }

    private Activity ownActivity(Provider provider, Long activityId) {
        return activityRepository.findByIdAndBusinessProviderId(activityId, provider.getId())
                .orElseThrow(() -> new ActivityNotFoundException(activityId));
    }

    // Si no eligió emprendimiento y tiene uno solo, se usa ese. Si eligió, tiene que ser suyo
    private Business resolveBusiness(Provider provider, Long businessId) {
        List<Business> businesses = provider.getBusinesses();
        if (businesses.isEmpty()) {
            throw new ActivityValidationException(null, "Todavía no tenés ningún emprendimiento registrado.");
        }
        if (businessId == null && businesses.size() == 1) {
            return businesses.getFirst();
        }
        return businesses.stream()
                .filter(b -> b.getId().equals(businessId))
                .findFirst()
                .orElseThrow(() -> new ActivityValidationException("businessId", "Elegí uno de tus emprendimientos."));
    }

    // Al menos un tipo, y todos tienen que existir y estar habilitados
    private Set<ActivityType> resolveTypes(Set<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            throw new ActivityValidationException("activityTypeIds", "Elegí al menos un tipo de actividad.");
        }
        List<ActivityType> types = activityTypeRepository.findAllById(typeIds);
        if (types.size() != typeIds.size() || !types.stream().allMatch(ActivityType::isEnabled)) {
            throw new ActivityValidationException("activityTypeIds", "Alguno de los tipos elegidos no está disponible.");
        }
        return new HashSet<>(types);
    }

    private void validateActivity(ActivityDTO dto) {
        if (dto.getPricePerPerson() == null || dto.getPricePerPerson().signum() <= 0) {
            throw new ActivityValidationException("pricePerPerson", "El precio tiene que ser mayor a 0.");
        }
        if (dto.getDurationMinutes() == null || dto.getDurationMinutes() <= 0) {
            throw new ActivityValidationException("durationMinutes", "La duración tiene que ser mayor a 0.");
        }
        if (dto.getMinAge() != null && dto.getMinAge() < 0) {
            throw new ActivityValidationException("minAge", "La edad mínima no puede ser negativa.");
        }
        if (dto.getBookingCutoffMinutes() == null || dto.getBookingCutoffMinutes() < 0) {
            throw new ActivityValidationException("bookingCutoffMinutes", "El límite de reserva no puede ser negativo.");
        }
        if (dto.isCancellationAllowed()) {
            if (dto.getCancellationCutoffHours() == null) {
                throw new ActivityValidationException("cancellationCutoffHours",
                        "Indicá con cuántas horas de anticipación se puede cancelar.");
            }
            if (dto.getCancellationCutoffHours() < 0) {
                throw new ActivityValidationException("cancellationCutoffHours",
                        "La anticipación no puede ser negativa.");
            }
            BigDecimal refund = dto.getRefundPercentage();
            if (refund == null) {
                throw new ActivityValidationException("refundPercentage", "Indicá qué porcentaje se reembolsa.");
            }
            if (refund.signum() < 0 || refund.compareTo(MAX_REFUND_PERCENTAGE) > 0) {
                throw new ActivityValidationException("refundPercentage", "El porcentaje tiene que estar entre 0 y 100.");
            }
        }
    }

    private ActivityValidationException duplicateSchedule() {
        return new ActivityValidationException("startDateTime", "Esta actividad ya tiene un horario en esa fecha y hora.");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
