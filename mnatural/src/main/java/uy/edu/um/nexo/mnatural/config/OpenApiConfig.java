package uy.edu.um.nexo.mnatural.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

// Documentación de la API pública con springdoc: Swagger UI en /swagger-ui.html y el JSON en /v3/api-docs/publica.
// Solo se documentan las rutas /api/** (las pantallas Thymeleaf no son parte de la API).
// Las descripciones y los ejemplos se agregan acá, con un OpenApiCustomizer, para no modificar
// PublicApiController. La documentación escrita a mano sigue en API.md
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("API pública de Uruguay Mnatural")
                .version("1")
                .description("API REST de solo lectura (ESPECIFICACION.md, sección 9). No requiere autenticación. "
                        + "Solo muestra actividades publicadas y emprendimientos de prestadores aprobados, "
                        + "y nunca datos privados (contraseñas, documentos, correos personales ni reservas)."));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("publica")
                .pathsToMatch("/api/**")
                .addOpenApiCustomizer(endpointDescriptions())
                .build();
    }

    // Ruta -> resumen y descripción; nombre del parámetro -> descripción y ejemplo
    private OpenApiCustomizer endpointDescriptions() {
        Map<String, String[]> operations = Map.of(
                "/api/businesses", new String[]{"Listado de emprendimientos",
                        "Emprendimientos de prestadores aprobados, ordenados por nombre."},
                "/api/businesses/{id}/status", new String[]{"Verificar un emprendimiento",
                        "Indica si el emprendimiento existe y si su prestador está aprobado. "
                                + "Si no existe responde {\"exists\": false, \"providerApproved\": false}."},
                "/api/activities/available", new String[]{"Actividades disponibles en un día",
                        "Actividades con horarios que todavía se pueden reservar ese día y tienen lugares libres, "
                                + "en un departamento o una localidad (al menos uno de los dos)."},
                "/api/activities/top", new String[]{"Top 3 del mes por departamento",
                        "Las 3 actividades con mejor promedio de las reseñas creadas en el mes en curso, "
                                + "desempatando por cantidad de valoraciones. Puede devolver menos de 3."},
                "/api/activities/search", new String[]{"Buscar actividades por palabra clave",
                        "Busca en el nombre, la descripción, la localidad y los tipos, sin distinguir "
                                + "mayúsculas ni tildes. Máximo 100 caracteres."});
        Map<String, String[]> parameters = Map.of(
                "id", new String[]{"Identificador del emprendimiento", "1"},
                "date", new String[]{"Día a consultar, con formato AAAA-MM-DD", "2026-12-20"},
                "department", new String[]{"Departamento, en mayúsculas y con guion bajo (por ejemplo RIO_NEGRO)", "ROCHA"},
                "locality", new String[]{"Localidad (búsqueda parcial)", "La Paloma"},
                "q", new String[]{"Palabra clave", "kayak"});

        return openApi -> openApi.getPaths().forEach((path, item) -> {
            Operation get = item.getGet();
            String[] texts = operations.get(path);
            if (get == null || texts == null) {
                return;
            }
            get.setSummary(texts[0]);
            get.setDescription(texts[1]);
            if (get.getParameters() != null) {
                for (Parameter parameter : get.getParameters()) {
                    String[] info = parameters.get(parameter.getName());
                    if (info != null) {
                        parameter.setDescription(info[0]);
                        parameter.setExample(info[1]);
                    }
                }
            }
        });
    }
}
