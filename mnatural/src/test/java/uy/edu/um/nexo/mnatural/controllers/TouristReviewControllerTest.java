package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.config.SecurityConfig;
import uy.edu.um.nexo.mnatural.dto.BookingReviewDTO;
import uy.edu.um.nexo.mnatural.dto.ReviewViewDTO;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Solo la capa web, con la seguridad real (SecurityConfig). Los services son mocks: no se usa la base
@WebMvcTest(TouristReviewController.class)
@Import(SecurityConfig.class)
class TouristReviewControllerTest {

    private static final String TOURIST_EMAIL = "ana@ejemplo.test";
    private static final Long TOURIST_ID = 7L;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ReviewService reviewService;
    @MockitoBean
    private UserRepository userRepository;
    // Lo necesita SecurityConfig
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        tourist.setEmail(TOURIST_EMAIL);
        when(userRepository.findByEmail(TOURIST_EMAIL)).thenReturn(Optional.of(tourist));
    }

    @Test
    void withoutLoginGoesToTheLoginPage() throws Exception {
        mockMvc.perform(get("/reviews/bookings/30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void showsTheFormWhenTheBookingCanBeReviewed() throws Exception {
        when(reviewService.getBookingReview(30L, TOURIST_ID)).thenReturn(page(true, null, null));

        mockMvc.perform(get("/reviews/bookings/30"))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/booking"))
                .andExpect(content().string(containsString("Publicar reseña")));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void showsTheExistingReviewWithTheDeleteButton() throws Exception {
        ReviewViewDTO review = new ReviewViewDTO(80L, "Ana P.", 4, "Muy lindo", LocalDateTime.now(), null, null);
        when(reviewService.getBookingReview(30L, TOURIST_ID)).thenReturn(page(false, review, null));

        mockMvc.perform(get("/reviews/bookings/30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Muy lindo")))
                .andExpect(content().string(containsString("Eliminar mi reseña")));
    }

    @Test
    @WithMockUser(username = "prestador@ejemplo.test", roles = "PROVIDER")
    void aUserThatIsNotATouristSeesTheBookingAsNonexistent() throws Exception {
        when(userRepository.findByEmail("prestador@ejemplo.test")).thenReturn(Optional.of(new Provider()));

        mockMvc.perform(get("/reviews/bookings/30"))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/error"))
                .andExpect(model().attribute("errorMessage", "La reserva no existe."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void anotherTouristsBookingShowsTheErrorPage() throws Exception {
        when(reviewService.getBookingReview(31L, TOURIST_ID)).thenThrow(new ReviewException("La reserva no existe."));

        mockMvc.perform(get("/reviews/bookings/31"))
                .andExpect(view().name("reviews/error"));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void postingAValidReviewSavesItAndRedirects() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30").with(csrf())
                        .param("rating", "5").param("comment", "Excelente"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reviews/bookings/30"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(reviewService).create(eq(30L), eq(TOURIST_ID), any());
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void anInvalidFormIsShownAgainWithoutSaving() throws Exception {
        when(reviewService.getBookingReview(30L, TOURIST_ID)).thenReturn(page(true, null, null));

        mockMvc.perform(post("/reviews/bookings/30").with(csrf())
                        .param("rating", "9").param("comment", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/booking"))
                .andExpect(model().attributeHasFieldErrors("form", "rating", "comment"));

        verify(reviewService, never()).create(any(), any(), any());
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aRuleViolationIsShownOnTheForm() throws Exception {
        when(reviewService.create(eq(30L), eq(TOURIST_ID), any()))
                .thenThrow(new ReviewException("Vas a poder calificar la actividad cuando termine."));
        when(reviewService.getBookingReview(30L, TOURIST_ID)).thenReturn(page(false, null, "Todavía no terminó."));

        mockMvc.perform(post("/reviews/bookings/30").with(csrf())
                        .param("rating", "5").param("comment", "Bien"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "Vas a poder calificar la actividad cuando termine."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void postingWithoutTheCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30").param("rating", "5").param("comment", "Bien"))
                .andExpect(status().isForbidden());

        verify(reviewService, never()).create(any(), any(), any());
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void deletingRedirectsWithAMessage() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30/delete").with(csrf()))
                .andExpect(redirectedUrl("/reviews/bookings/30"))
                .andExpect(flash().attribute("successMessage", "Eliminaste tu reseña."));

        verify(reviewService).delete(30L, TOURIST_ID);
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void deletingWithoutAReviewShowsTheReason() throws Exception {
        doThrow(new ReviewException("Esta reserva no tiene una reseña para eliminar."))
                .when(reviewService).delete(30L, TOURIST_ID);

        mockMvc.perform(post("/reviews/bookings/30/delete").with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Esta reserva no tiene una reseña para eliminar."));
    }

    private BookingReviewDTO page(boolean canReview, ReviewViewDTO review, String blockedReason) {
        return new BookingReviewDTO(30L, 5L, "Kayak", LocalDateTime.now().minusDays(1), review, canReview, blockedReason);
    }
}
