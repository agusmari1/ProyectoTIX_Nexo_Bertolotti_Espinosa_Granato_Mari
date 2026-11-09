package uy.edu.um.nexo.mnatural.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Test unitario: arma un documento OpenAPI de mentira y le aplica el customizer, sin levantar la app
class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void onlyThePublicApiIsDocumented() {
        GroupedOpenApi group = config.publicApi();

        assertThat(group.getGroup()).isEqualTo("publica");
        assertThat(group.getPathsToMatch()).containsExactly("/api/**");
    }

    @Test
    void addsSummaryDescriptionAndParameterExamples() {
        Operation search = new Operation().parameters(List.of(new Parameter().name("q")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/activities/search", new PathItem().get(search)));

        config.publicApi().getOpenApiCustomizers().forEach(customizer -> customizer.customise(openApi));

        assertThat(search.getSummary()).isEqualTo("Buscar actividades por palabra clave");
        assertThat(search.getDescription()).contains("sin distinguir");
        assertThat(search.getParameters().getFirst().getExample()).isEqualTo("kayak");
    }

    @Test
    void theApiInfoHasATitle() {
        assertThat(config.apiInfo().getInfo().getTitle()).isEqualTo("API pública de Uruguay Mnatural");
    }
}
