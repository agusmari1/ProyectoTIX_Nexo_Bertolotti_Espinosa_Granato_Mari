package uy.edu.um.nexo.mnatural.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityType;

import java.text.Normalizer;

// El criterio de búsqueda por palabra clave, en un solo lugar para que el buscador del portal (/actividades?q=) y la
// API (/api/activities/search?q=) busquen EXACTAMENTE igual: el texto puede estar en el nombre, la descripción, la
// localidad o el nombre de alguno de los tipos de la actividad, sin distinguir mayúsculas ni tildes.
// Solo arma la condición de texto: quién puede verse (publicada, prestador aprobado) lo agrega cada uso
public final class ActivityKeywordSearch {

    // Largo máximo de lo que se busca (lo mismo que valida la API)
    public static final int MAX_LENGTH = 100;

    private ActivityKeywordSearch() {
    }

    public static Specification<Activity> matches(String keyword) {
        return (root, query, cb) -> {
            // Con una subconsulta, para no repetir la actividad si tiene varios tipos
            Subquery<Long> typeMatch = query.subquery(Long.class);
            Root<Activity> sameActivity = typeMatch.correlate(root);
            Join<Activity, ActivityType> type = sameActivity.join("activityTypes");
            typeMatch.select(type.get("id")).where(contains(cb, type.get("name"), keyword));

            return cb.or(contains(cb, root.get("name"), keyword),
                    contains(cb, root.get("description"), keyword),
                    contains(cb, root.get("locality"), keyword),
                    cb.exists(typeMatch));
        };
    }

    // translate le saca los tildes a la columna en PostgreSQL y simplify al texto buscado.
    // La barra escapa % y _ para que se busquen como texto y no como comodines
    public static Predicate contains(CriteriaBuilder cb, Expression<String> column, String text) {
        Expression<String> withoutAccents = cb.function("translate", String.class,
                cb.lower(column), cb.literal("áéíóúüñ"), cb.literal("aeiouun"));
        String escaped = simplify(text).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(withoutAccents, "%" + escaped + "%", '\\');
    }

    static String simplify(String text) {
        return Normalizer.normalize(text.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
