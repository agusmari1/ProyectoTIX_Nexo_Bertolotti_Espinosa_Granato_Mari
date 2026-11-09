package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.config.SecurityConfig;
import uy.edu.um.nexo.mnatural.dto.ReviewSummaryDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifica que el listado y el detalle públicos (sin login) muestren las reseñas con el fragmento
// templates/fragments/reviews.html. Los services son mocks: no se usa la base
@WebMvcTest(PublicActivityController.class)
@Import(SecurityConfig.class)
class PublicReviewModelAdviceTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityCatalogService catalogService;
    @MockitoBean
    private ActivityTypeRepository activityTypeRepository;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    private Activity activity;

    @BeforeEach
    void setUp() {
        Business business = new Business();
        business.setName("Emprendimiento");
        activity = new Activity();
        activity.setId(5L);
        activity.setName("Kayak al atardecer");
        activity.setDescription("Remada tranquila");
        activity.setAddress("Rambla");
        activity.setDepartment(Department.ROCHA);
        activity.setLocality("La Paloma");
        activity.setDurationMinutes(90);
        activity.setPricePerPerson(new BigDecimal("1500"));
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setBusiness(business);
        when(reviewService.summariesByActivity()).thenReturn(Map.of(5L, new ReviewSummaryDTO(5L, 4.5, 2L)));
    }

    @Test
    void theListShowsTheAverageOfEachActivity() throws Exception {
        when(catalogService.search(any())).thenReturn(List.of(activity));

        mockMvc.perform(get("/actividades"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("4,5")))
                .andExpect(content().string(containsString("(2 valoraciones)")));
    }

    @Test
    void anActivityWithoutReviewsSaysSo() throws Exception {
        when(reviewService.summariesByActivity()).thenReturn(Map.of());
        when(catalogService.search(any())).thenReturn(List.of(activity));

        mockMvc.perform(get("/actividades"))
                .andExpect(content().string(containsString("Sin valoraciones todavía")));
    }

    @Test
    void theDetailShowsTheCommentsAndTheProviderReply() throws Exception {
        when(catalogService.findPublished(5L)).thenReturn(Optional.of(activity));
        when(catalogService.upcomingSchedules(activity)).thenReturn(List.of());
        when(reviewService.findVisibleReviews(5L)).thenReturn(List.of(new ReviewViewDTO(80L, "Ana P.", 5,
                "Muy recomendable", LocalDateTime.now(), "¡Gracias, Ana!", LocalDateTime.now())));

        mockMvc.perform(get("/actividades/5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana P.")))
                .andExpect(content().string(containsString("Muy recomendable")))
                .andExpect(content().string(containsString("Respuesta del prestador")))
                .andExpect(content().string(containsString("¡Gracias, Ana!")));
    }
}
