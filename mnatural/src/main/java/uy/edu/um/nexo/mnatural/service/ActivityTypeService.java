package uy.edu.um.nexo.mnatural.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.exception.ActivityTypeException;
import uy.edu.um.nexo.mnatural.exception.ActivityTypeNotFoundException;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Administración de los tipos de actividad (la usa el admin).
// Un tipo desactivado deja de ofrecerse para actividades nuevas, pero las actividades que ya
// lo tenían lo conservan. Un tipo con actividades no se puede borrar.
@Service
public class ActivityTypeService {

    public static final int MAX_NAME_LENGTH = 255;

    private final ActivityTypeRepository activityTypeRepository;

    public ActivityTypeService(ActivityTypeRepository activityTypeRepository) {
        this.activityTypeRepository = activityTypeRepository;
    }

    // Todos (activos y desactivados), ordenados por nombre sin tener en cuenta las tildes
    @Transactional(readOnly = true)
    public List<ActivityType> findAll() {
        return activityTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(type -> normalize(type.getName())))
                .toList();
    }

    // Id del tipo -> cuántas actividades lo usan. Los tipos sin actividades no figuran
    @Transactional(readOnly = true)
    public Map<Long, Long> countActivitiesByType() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : activityTypeRepository.countActivitiesByType()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional
    public ActivityType create(String name) {
        String cleanName = cleanName(name);
        checkNotRepeated(cleanName, null);

        ActivityType type = new ActivityType();
        type.setName(cleanName);
        type.setEnabled(true);
        try {
            // saveAndFlush para que, si otro admin creó el mismo nombre al mismo tiempo,
            // la restricción única de la base salte acá y no al terminar la transacción
            return activityTypeRepository.saveAndFlush(type);
        } catch (DataIntegrityViolationException e) {
            throw new ActivityTypeException("Ya existe un tipo de actividad llamado \"" + cleanName + "\".");
        }
    }

    @Transactional
    public ActivityType rename(Long typeId, String name) {
        ActivityType type = findType(typeId);
        String cleanName = cleanName(name);
        // Puede cambiar solo mayúsculas o tildes de su propio nombre, pero no repetir el de otro tipo
        checkNotRepeated(cleanName, typeId);

        type.setName(cleanName);
        return activityTypeRepository.save(type);
    }

    @Transactional
    public ActivityType setEnabled(Long typeId, boolean enabled) {
        ActivityType type = findType(typeId);
        type.setEnabled(enabled);
        return activityTypeRepository.save(type);
    }

    @Transactional
    public void delete(Long typeId) {
        ActivityType type = findType(typeId);
        long inUse = activityTypeRepository.countActivitiesUsing(typeId);
        if (inUse > 0) {
            throw new ActivityTypeException("No se puede borrar \"" + type.getName() + "\" porque lo "
                    + (inUse == 1 ? "usa 1 actividad" : "usan " + inUse + " actividades")
                    + ". Desactivalo para que no se ofrezca más.");
        }
        activityTypeRepository.delete(type);
    }

    private ActivityType findType(Long typeId) {
        return activityTypeRepository.findById(typeId)
                .orElseThrow(() -> new ActivityTypeNotFoundException(typeId));
    }

    private String cleanName(String name) {
        String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            throw new ActivityTypeException("El nombre es obligatorio.");
        }
        if (clean.length() > MAX_NAME_LENGTH) {
            throw new ActivityTypeException("El nombre no puede superar los " + MAX_NAME_LENGTH + " caracteres.");
        }
        return clean;
    }

    // ownId es el tipo que se está renombrando (null si es uno nuevo): no se compara consigo mismo.
    // Hay pocos tipos, así que se comparan todos en memoria: la base no sabe ignorar tildes
    private void checkNotRepeated(String name, Long ownId) {
        String normalized = normalize(name);
        for (ActivityType other : activityTypeRepository.findAll()) {
            boolean isSelf = ownId != null && ownId.equals(other.getId());
            if (!isSelf && normalize(other.getName()).equals(normalized)) {
                throw new ActivityTypeException("Ya existe un tipo de actividad llamado \"" + other.getName() + "\".");
            }
        }
    }

    // "Gastronomía", "gastronomia" y " GASTRONOMÍA " dan el mismo resultado
    static String normalize(String name) {
        String withoutAccents = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
