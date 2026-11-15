package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;
import uy.edu.um.nexo.mnatural.weather.WeatherService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El campo de búsqueda por palabra clave de /actividades. Los services están simulados, no se toca la base de datos
@WebMvcTest(PublicActivityController.class)
class PublicActivitySearchTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityCatalogService catalogService;
    @MockitoBean
    private ActivityTypeRepository activityTypeRepository;
    @MockitoBean(name = "weatherService")
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        when(catalogService.search(any())).thenReturn(List.of());
        when(activityTypeRepository.findAll(any(Sort.class))).thenReturn(List.of());
    }

    @Test
    void theListShowsTheKeywordFieldInBothLanguages() throws Exception {
        mockMvc.perform(get("/actividades"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"q\"")))
                .andExpect(content().string(containsString("Buscar por palabra clave")));
        mockMvc.perform(get("/actividades").cookie(new Cookie("lang", "en")))
                .andExpect(content().string(containsString("Search by keyword")));
    }

    @Test
    void theKeywordReachesTheServiceUnderTheSameNameAsTheApi() throws Exception {
        mockMvc.perform(get("/actividades").param("q", "Piriápolis"))
                .andExpect(status().isOk());

        ArgumentCaptor<ActivityFilterDTO> filter = ArgumentCaptor.forClass(ActivityFilterDTO.class);
        verify(catalogService).search(filter.capture());
        assertThat(filter.getValue().getQ()).isEqualTo("Piriápolis");
        assertThat(filter.getValue().isEmpty()).isFalse();
    }

    @Test
    void theSearchedTextStaysInTheBoxAndIsEscaped() throws Exception {
        mockMvc.perform(get("/actividades").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"&lt;script&gt;alert(1)&lt;/script&gt;\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>alert(1)"))));
    }

    @Test
    void searchingWithNoResultsExplainsThatNothingMatchedTheFilters() throws Exception {
        mockMvc.perform(get("/actividades").param("q", "xyz"))
                .andExpect(content().string(containsString("No encontramos actividades con esos filtros")));
    }

    @Test
    void withoutKeywordNothingChanges() throws Exception {
        mockMvc.perform(get("/actividades"))
                .andExpect(content().string(containsString("Todavía no hay actividades publicadas")));

        ArgumentCaptor<ActivityFilterDTO> filter = ArgumentCaptor.forClass(ActivityFilterDTO.class);
        verify(catalogService).search(filter.capture());
        assertThat(filter.getValue().getQ()).isNull();
        assertThat(filter.getValue().isEmpty()).isTrue();
    }
}
