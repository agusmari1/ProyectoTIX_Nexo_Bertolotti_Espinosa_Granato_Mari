package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ProviderReviewDTO;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Sin filtros de seguridad: se prueba el controller. El service está simulado, no se toca la base de datos
@WebMvcTest(ProviderReviewReplyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderReviewReplyControllerTest {

    private static final String EMAIL = "prestador@example.com";
    private static final Principal PROVIDER = new UsernamePasswordAuthenticationToken(EMAIL, "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void theListShowsTheReviewsOfTheProvidersActivities() throws Exception {
        ProviderReviewDTO review = new ProviderReviewDTO(500L, "Cabalgata", "Ana P.", 4, "Muy linda",
                LocalDateTime.of(2026, 11, 2, 12, 0), null, null);
        when(reviewService.findForProvider(EMAIL)).thenReturn(List.of(review));

        mockMvc.perform(get("/provider/reviews").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/reviews"))
                .andExpect(model().attributeExists("reviews"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Muy linda")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Responder")));
    }

    @Test
    void anAlreadyRepliedReviewShowsTheReplyAndNoForm() throws Exception {
        ProviderReviewDTO review = new ProviderReviewDTO(500L, "Cabalgata", "Ana P.", 4, null,
                LocalDateTime.of(2026, 11, 2, 12, 0), "Gracias por venir", LocalDateTime.of(2026, 11, 3, 9, 0));
        when(reviewService.findForProvider(EMAIL)).thenReturn(List.of(review));

        mockMvc.perform(get("/provider/reviews").principal(PROVIDER))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gracias por venir")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("name=\"reply\""))));
    }

    @Test
    void theProviderCanReply() throws Exception {
        mockMvc.perform(post("/provider/reviews/500/reply").principal(PROVIDER).param("reply", "¡Gracias!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(reviewService).reply(500L, EMAIL, "¡Gracias!");
    }

    @Test
    void ifTheServiceRefusesTheReplyTheProviderSeesTheReason() throws Exception {
        org.mockito.Mockito.doThrow(new ReviewException("Ya respondiste esta reseña."))
                .when(reviewService).reply(500L, EMAIL, "otra");

        mockMvc.perform(post("/provider/reviews/500/reply").principal(PROVIDER).param("reply", "otra"))
                .andExpect(redirectedUrl("/provider/reviews"))
                .andExpect(flash().attribute("errorMessage", "Ya respondiste esta reseña."));
    }

    @Test
    void thereIsNoRouteForTheProviderToDeleteAReview() throws Exception {
        mockMvc.perform(post("/provider/reviews/500/delete").principal(PROVIDER))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/provider/reviews/500").principal(PROVIDER))
                .andExpect(status().is4xxClientError());
    }
}
