package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ReviewTargetDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.ReviewService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Sin filtros de seguridad: se prueba el controller. Los services están simulados, no se toca la base de datos
@WebMvcTest(TouristReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class TouristReviewControllerTest {

    private static final String EMAIL = "turista@example.com";
    private static final Principal TOURIST = new UsernamePasswordAuthenticationToken(EMAIL, "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private UserRepository userRepository;

    private ReviewTargetDTO target() {
        return new ReviewTargetDTO(30L, 10L, "Cabalgata", LocalDateTime.of(2026, 11, 1, 10, 0), "MN-ABCD2345");
    }

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        tourist.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(tourist));
        when(reviewService.findOwn(30L, 7L)).thenReturn(Optional.empty());
        when(reviewService.targetFor(30L, 7L)).thenReturn(target());
    }

    @Test
    void theFormIsShownWhenThereIsNoReviewYet() throws Exception {
        mockMvc.perform(get("/reviews/bookings/30").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/form"))
                .andExpect(model().attributeExists("target", "review"));
    }

    @Test
    void theExistingReviewIsShownWithTheDeleteButton() throws Exception {
        Review review = new Review();
        review.setId(500L);
        review.setRating(4);
        review.setStatus(ReviewStatus.VISIBLE);
        review.setCreatedAt(LocalDateTime.now());
        when(reviewService.findOwn(30L, 7L)).thenReturn(Optional.of(review));
        when(reviewService.describe(30L, 7L)).thenReturn(target());

        mockMvc.perform(get("/reviews/bookings/30").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/form"))
                .andExpect(model().attribute("deleted", false))
                .andExpect(model().attributeExists("existing"));
    }

    @Test
    void aDeletedReviewIsShownAsDeletedAndCannotBeReplaced() throws Exception {
        Review review = new Review();
        review.setId(500L);
        review.setRating(4);
        review.setStatus(ReviewStatus.DELETED);
        review.setCreatedAt(LocalDateTime.now());
        when(reviewService.findOwn(30L, 7L)).thenReturn(Optional.of(review));
        when(reviewService.describe(30L, 7L)).thenReturn(target());

        mockMvc.perform(get("/reviews/bookings/30").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(model().attribute("deleted", true));
    }

    @Test
    void aValidReviewIsCreatedAndTheTouristGoesBackToTheirBookings() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST)
                        .param("rating", "5").param("comment", "Hermoso"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/turista/reservas"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(reviewService).create(30L, 7L, 5, "Hermoso");
    }

    @Test
    void theRatingIsRequiredAndMustBeFromOneToFive() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST).param("comment", "x"))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/form"))
                .andExpect(model().attributeHasFieldErrors("review", "rating"));
        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST).param("rating", "9"))
                .andExpect(model().attributeHasFieldErrors("review", "rating"));
        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST).param("rating", "0"))
                .andExpect(model().attributeHasFieldErrors("review", "rating"));

        verify(reviewService, never()).create(any(), any(), any(), any());
    }

    @Test
    void aTooLongCommentIsRejectedByTheForm() throws Exception {
        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST)
                        .param("rating", "3").param("comment", "a".repeat(1001)))
                .andExpect(model().attributeHasFieldErrors("review", "comment"));

        verify(reviewService, never()).create(any(), any(), any(), any());
    }

    @Test
    void ifTheServiceRefusesTheReviewTheTouristSeesTheReasonInTheirBookings() throws Exception {
        when(reviewService.create(30L, 7L, 5, "x"))
                .thenThrow(new ReviewException("Vas a poder calificar la actividad cuando termine."));

        mockMvc.perform(post("/reviews/bookings/30").principal(TOURIST).param("rating", "5").param("comment", "x"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/turista/reservas"))
                .andExpect(flash().attribute("errorMessage", "Vas a poder calificar la actividad cuando termine."));
    }

    @Test
    void openingTheFormOfAnActivityThatIsNotFinishedRedirectsWithTheReason() throws Exception {
        when(reviewService.targetFor(30L, 7L)).thenThrow(new ReviewException("Todavía no terminó."));

        mockMvc.perform(get("/reviews/bookings/30").principal(TOURIST))
                .andExpect(redirectedUrl("/turista/reservas"))
                .andExpect(flash().attribute("errorMessage", "Todavía no terminó."));
    }

    @Test
    void deletingAReviewGoesThroughTheServiceAndReturnsToTheBookings() throws Exception {
        mockMvc.perform(post("/reviews/500/delete").principal(TOURIST))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/turista/reservas"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(reviewService).delete(500L, 7L);
    }

    @Test
    void whoIsNotATouristCannotUseTheReviewScreens() throws Exception {
        Admin admin = new Admin();
        admin.setId(1L);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/reviews/500/delete")
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", "x")))
                .andExpect(redirectedUrl("/turista/reservas"))
                .andExpect(flash().attributeExists("errorMessage"));

        verify(reviewService, never()).delete(any(), any());
    }
}
