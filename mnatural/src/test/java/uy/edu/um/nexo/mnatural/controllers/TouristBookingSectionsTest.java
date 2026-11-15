package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.ReviewRepository;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// "Mis reservas": próximas y pasadas, y el botón "Valorar" solo donde corresponde.
// Los services y el repositorio están simulados: no se toca la base de datos
@WebMvcTest(TouristBookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TouristBookingSectionsTest {

    private static final String EMAIL = "turista@ejemplo.test";
    private static final Principal TOURIST = new UsernamePasswordAuthenticationToken(EMAIL, "x");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private TouristService touristService;
    @MockitoBean
    private ReviewRepository reviewRepository;

    private TouristBookingDTO booking(long id, String activity, LocalDateTime start, BookingStatus status,
                                      boolean reviewable) {
        boolean confirmed = status == BookingStatus.CONFIRMED;
        return new TouristBookingDTO(id, activity, start, 2, new BigDecimal("1500.00"), status, null,
                confirmed ? "MN-CODE000" + id : null, reviewable);
    }

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        when(touristService.getByEmail(EMAIL)).thenReturn(tourist);
    }

    private String page() throws Exception {
        MvcResult result = mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void theBookingsAreSplitIntoUpcomingAndPastWithTheirDataAndCode() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                booking(1, "Cabalgata futura", now.plusDays(5), BookingStatus.CONFIRMED, false),
                booking(2, "Kayak pasado", now.minusDays(8), BookingStatus.CONFIRMED, true)));

        String html = page();

        assertThat(html).contains("id=\"upcoming-bookings\"", "id=\"past-bookings\"");
        int upcoming = html.indexOf("id=\"upcoming-bookings\"");
        int past = html.indexOf("id=\"past-bookings\"");
        assertThat(upcoming).isLessThan(past);
        // Cada una en su sección: la futura antes de "Pasadas" y la pasada después
        assertThat(html.indexOf("Cabalgata futura")).isBetween(upcoming, past);
        assertThat(html.indexOf("Kayak pasado")).isGreaterThan(past);
        assertThat(html).contains("Confirmada", "MN-CODE0001", "MN-CODE0002", "1.500,00");
    }

    @Test
    void aSectionWithNoBookingsIsNotShown() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                booking(2, "Kayak pasado", LocalDateTime.now().minusDays(8), BookingStatus.CONFIRMED, true)));

        assertThat(page()).doesNotContain("id=\"upcoming-bookings\"").contains("id=\"past-bookings\"");

        when(bookingService.findBookingsOf(7L)).thenReturn(List.of());
        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(content().string(containsString("Todavía no tenés reservas")))
                .andExpect(content().string(not(containsString("id=\"past-bookings\""))));
    }

    @Test
    void ratingIsOfferedOnlyOnPastConfirmedBookingsWithoutAReview() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                booking(1, "Futura confirmada", now.plusDays(5), BookingStatus.CONFIRMED, false),
                booking(2, "Pasada sin reseña", now.minusDays(8), BookingStatus.CONFIRMED, true),
                booking(3, "Pasada ya valorada", now.minusDays(9), BookingStatus.CONFIRMED, true),
                booking(4, "Pasada cancelada", now.minusDays(10), BookingStatus.CANCELLED, false),
                booking(5, "Pasada vencida", now.minusDays(11), BookingStatus.EXPIRED, false)));
        when(reviewRepository.findReviewedBookingIdsOf(7L)).thenReturn(List.of(3L));

        String html = page();

        // Un solo botón "Valorar", y lleva a la reseña de la reserva 2
        assertThat(html.split("rate-button", -1).length - 1).isEqualTo(1);
        assertThat(html).contains("href=\"/reviews/bookings/2\"");
        assertThat(html).contains(">Valorar<");
        // La ya valorada ofrece ver su reseña (no valorar de nuevo) y queda marcada
        assertThat(html).contains("Ver mi reseña").contains("Valorada");
        assertThat(html.split("Ver mi reseña", -1).length - 1).isEqualTo(1);
        // Se busca solo lo de ESTE turista
        verify(reviewRepository).findReviewedBookingIdsOf(7L);
    }

    @Test
    void aFutureOrPendingBookingNeverOffersToRate() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                booking(1, "Futura", now.plusDays(1), BookingStatus.CONFIRMED, false),
                booking(2, "Pendiente", now.plusDays(2), BookingStatus.PENDING, false)));

        assertThat(page()).doesNotContain("rate-button").doesNotContain("Ver mi reseña");
    }

    @Test
    void theButtonIsInEnglishWhenThePageIsInEnglish() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                booking(2, "Kayak pasado", LocalDateTime.now().minusDays(8), BookingStatus.CONFIRMED, true)));

        mockMvc.perform(get("/turista/reservas").principal(TOURIST).param("lang", "en"))
                .andExpect(content().string(containsString(">Rate it<")))
                .andExpect(content().string(containsString("Past")));
    }
}
