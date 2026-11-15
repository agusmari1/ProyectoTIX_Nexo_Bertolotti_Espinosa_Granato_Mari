package uy.edu.um.nexo.mnatural.service;

import org.springframework.core.io.ClassPathResource;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Foto de cada actividad. Las imágenes están en static/img/actividades/ (los créditos, en CREDITOS.md):
//   1. la foto de la propia actividad: el archivo se llama como su título, sin tildes ni signos y con guiones
//      (por ejemplo "Caminata por Cabo Polonio" -> caminata-por-cabo-polonio.jpg);
//   2. si no tiene, la foto de su departamento (punta-del-este, colonia, montevideo, salto, rocha);
//   3. si tampoco hay, devuelve null y la pantalla simplemente no muestra imagen.
// Se usa desde las plantillas del portal con ${activityPhotos.urlFor(activity)} (lo pone en el modelo
// PublicActivityAdvice)
public class ActivityPhotos {

    static final String FOLDER = "static/img/actividades/";
    static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    private static final Map<Department, String> DEPARTMENT_FILES = Map.of(
            Department.MALDONADO, "punta-del-este",
            Department.COLONIA, "colonia",
            Department.MONTEVIDEO, "montevideo",
            Department.SALTO, "salto",
            Department.ROCHA, "rocha");

    // Se guarda lo que ya se buscó, incluso "no hay foto", para no consultar el disco en cada pantalla
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    // Foto de la actividad, o la de su departamento. null si no hay ninguna
    public String urlFor(Activity activity) {
        if (activity == null) {
            return null;
        }
        String own = activity.getName() == null ? null : cache.computeIfAbsent(slug(activity.getName()), this::find)
                .orElse(null);
        return own != null ? own : urlFor(activity.getDepartment());
    }

    // Foto de un departamento, o null
    public String urlFor(Department department) {
        String file = department == null ? null : DEPARTMENT_FILES.get(department);
        return file == null ? null : cache.computeIfAbsent(file, this::find).orElse(null);
    }

    // "Paseo en velero por la bahía" -> "paseo-en-velero-por-la-bahia"
    public static String slug(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private Optional<String> find(String name) {
        if (name.isEmpty()) {
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
