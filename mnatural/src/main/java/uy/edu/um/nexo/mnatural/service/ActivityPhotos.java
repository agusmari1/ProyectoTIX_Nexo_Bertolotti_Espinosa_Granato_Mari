package uy.edu.um.nexo.mnatural.service;

import org.springframework.core.io.ClassPathResource;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Foto de las actividades de cada departamento. Las imágenes están en static/img/actividades/ con el nombre del
// lugar (punta-del-este, colonia, montevideo, salto, rocha). Si el departamento no tiene foto, o el archivo todavía
// no está, devuelve null y la pantalla simplemente no muestra imagen. Se usa desde las plantillas del portal
// con ${activityPhotos.urlFor(activity.department)} (lo pone en el modelo PublicActivityAdvice)
public class ActivityPhotos {

    static final String FOLDER = "static/img/actividades/";
    static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    private static final Map<Department, String> FILE_NAMES = Map.of(
            Department.MALDONADO, "punta-del-este",
            Department.COLONIA, "colonia",
            Department.MONTEVIDEO, "montevideo",
            Department.SALTO, "salto",
            Department.ROCHA, "rocha");

    // Se guarda lo que ya se buscó, incluso "no hay foto", para no consultar el disco en cada pantalla
    private final Map<Department, Optional<String>> cache = new ConcurrentHashMap<>();

    // Ruta pública de la foto (por ejemplo /img/actividades/rocha.webp) o null si no hay
    public String urlFor(Department department) {
        if (department == null) {
            return null;
        }
        return cache.computeIfAbsent(department, this::find).orElse(null);
    }

    private Optional<String> find(Department department) {
        String name = FILE_NAMES.get(department);
        if (name == null) {
            return Optional.empty();
        }
        for (String extension : EXTENSIONS) {
            if (new ClassPathResource(FOLDER + name + "." + extension).exists()) {
                return Optional.of("/img/actividades/" + name + "." + extension);
            }
        }
        return Optional.empty();
    }
}
