package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.config.SecurityConfig;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Solo la capa web, con la seguridad real (SecurityConfig). Los services son mocks: no se usa la base
@WebMvcTest(ProviderReviewController.class)
@Import(SecurityConfig.class)
class ProviderReviewControllerTest {

    private static final String PROVIDER_EMAIL = "prestador@ejemplo.test";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private ActivityService activityService;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = PROVIDER_EMAIL, roles = "PROVIDER")
    void showsTheReviewsWithTheReplyForm() throws Exception {
        when(activityService.findOwnActivity(PROVIDER_EMAIL, 5L)).thenReturn(activity());
        when(reviewService.findForProvider(PROVIDER_EMAIL, 5L)).thenReturn(List.of(
                new ReviewViewDTO(80L, "Ana P.", 5, "Excelente guía", LocalDateTime.now(), null, null)));

        mockMvc.perform(get("/provider/activities/5/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/activities/reviews"))
                .andExpect(content().string(containsString("Excelente guía")))
                .andExpect(content().string(containsString("Publicar respuesta")));
    }

    @Test
    @WithMockUser(username = "ana@ejemplo.test", roles = "TOURIST")
    void aTouristCannotEnter() throws Exception {
        mockMvc.perform(get("/provider/activities/5/reviews"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = PROVIDER_EMAIL, roles = "PROVIDER")
    void anActivityOfAnotherProviderGoesBackToMyActivities() throws Exception {
        when(activityService.findOwnActivity(PROVIDER_EMAIL, 9L)).thenThrow(new ActivityNotFoundException(9L));

        mockMvc.perform(get("/provider/activities/9/reviews"))
                .andExpect(redirectedUrl("/provider/activities"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = PROVIDER_EMAIL, roles = "PROVIDER")
    void replyingPublishesTheReply() throws Exception {
        mockMvc.perform(post("/provider/activities/5/reviews/80/reply").with(csrf()).param("reply", "¡Gracias!"))
                .andExpect(redirectedUrl("/provider/activities/5/reviews"))
                .andExpect(flash().attribute("successMessage", "Publicaste tu respuesta."));

        verify(reviewService).reply(PROVIDER_EMAIL, 5L, 80L, "¡Gracias!");
    }

    @Test
    @WithMockUser(username = PROVIDER_EMAIL, roles = "PROVIDER")
    void aSecondReplyShowsTheReason() throws Exception {
        when(reviewService.reply(PROVIDER_EMAIL, 5L, 80L, "Otra")).thenThrow(new ReviewException("Ya respondiste esta reseña."));

        mockMvc.perform(post("/provider/activities/5/reviews/80/reply").with(csrf()).param("reply", "Otra"))
                .andExpect(flash().attribute("errorMessage", "Ya respondiste esta reseña."));
    }

    @Test
    @WithMockUser(username = PROVIDER_EMAIL, roles = "PROVIDER")
    void thereIsNoRouteToDeleteAReview() throws Exception {
        mockMvc.perform(post("/provider/activities/5/reviews/80/delete").with(csrf()))
                .andExpect(status().is4xxClientError());

        verify(reviewService, never()).delete(any(), any());
    }

    private Activity activity() {
        Activity activity = new Activity();
        activity.setId(5L);
        activity.setName("Kayak");
        return activity;
    }
}
