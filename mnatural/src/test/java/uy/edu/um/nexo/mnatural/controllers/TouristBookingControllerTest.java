package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TouristBookingController.class)
class TouristBookingControllerTest {

    private static final Principal TOURIST = new UsernamePasswordAuthenticationToken("turista@ejemplo.test", null, List.of());

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private TouristService touristService;

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        when(touristService.getByEmail("turista@ejemplo.test")).thenReturn(tourist);
    }

    @Test
    void withoutBookingsItInvitesToSeeTheActivities() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of());

        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todavía no tenés reservas")));
    }

    @Test
    void pendingBookingShowsTheDeadlineAndLinksToThePayment() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(new TouristBookingDTO(1L, "Cabalgata",
                LocalDateTime.of(2030, 1, 1, 10, 0), 2, new BigDecimal("1000.00"), BookingStatus.PENDING,
                LocalDateTime.of(2029, 12, 31, 14, 35), null, false)));

        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(content().string(containsString("Cabalgata")))
                .andExpect(content().string(containsString("Pendiente de pago")))
                .andExpect(content().string(containsString("14:35")))
                .andExpect(content().string(containsString("1.000,00")))
                .andExpect(content().string(containsString("/payments/bookings/1")))
                .andExpect(content().string(not(containsString("Calificar"))));
    }

    @Test
    void confirmedBookingShowsItsCode() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(new TouristBookingDTO(1L, "Cabalgata",
                LocalDateTime.of(2030, 1, 1, 10, 0), 2, new BigDecimal("1000.00"), BookingStatus.CONFIRMED,
                null, "MN-ABC", false)));

        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(content().string(containsString("Confirmada")))
                .andExpect(content().string(containsString("MN-ABC")))
                .andExpect(content().string(not(containsString("Calificar"))));
    }

    @Test
    void reviewableBookingLinksToReviews() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(new TouristBookingDTO(1L, "Cabalgata",
                LocalDateTime.of(2020, 1, 1, 10, 0), 2, new BigDecimal("1000.00"), BookingStatus.CONFIRMED,
                null, "MN-ABC", true)));

        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(content().string(containsString("Calificar")))
                .andExpect(content().string(containsString("/reviews/bookings/1")));
    }

    @Test
    void cancelledAndExpiredBookingsShowTheirStatus() throws Exception {
        when(bookingService.findBookingsOf(7L)).thenReturn(List.of(
                new TouristBookingDTO(1L, "Cabalgata", LocalDateTime.of(2030, 1, 1, 10, 0), 2,
                        new BigDecimal("1000.00"), BookingStatus.CANCELLED, null, null, false),
                new TouristBookingDTO(2L, "Kayak", LocalDateTime.of(2030, 1, 1, 10, 0), 2,
                        new BigDecimal("1000.00"), BookingStatus.EXPIRED, null, null, false)));

        mockMvc.perform(get("/turista/reservas").principal(TOURIST))
                .andExpect(content().string(containsString("Cancelada")))
                .andExpect(content().string(containsString("Vencida")));
    }
}
