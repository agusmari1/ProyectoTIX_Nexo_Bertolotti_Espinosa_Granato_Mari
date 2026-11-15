package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.BookingRequestDTO;
import uy.edu.um.nexo.mnatural.dto.BookingSummaryDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.exception.NotEnoughSpotsException;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Quién puede entrar a estas rutas se prueba en SecurityConfigTest
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final Principal TOURIST = new UsernamePasswordAuthenticationToken("turista@ejemplo.test", null, java.util.List.of());

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private TouristService touristService;

    private final Tourist tourist = new Tourist();

    @BeforeEach
    void setUp() {
        when(touristService.getByEmail("turista@ejemplo.test")).thenReturn(tourist);
        when(bookingService.getBookingSummary(5L)).thenReturn(summary(null));
    }

    @Test
    void showFormDisplaysTheSummaryAndTheHold() throws Exception {
        tourist.setExceptionalCondition("Silla de ruedas");

        mockMvc.perform(get("/reservar/5").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/form"))
                .andExpect(content().string(containsString("Cabalgata")))
                .andExpect(content().string(containsString("1.500,00")))
                .andExpect(content().string(containsString("retenidos durante")))
                .andExpect(content().string(containsString("Continuar al pago")))
                .andExpect(content().string(containsString("Silla de ruedas")));
    }

    @Test
    void showFormHidesTheFormWhenItCannotBeBooked() throws Exception {
        when(bookingService.getBookingSummary(5L)).thenReturn(summary("No quedan lugares disponibles para esta fecha."));

        mockMvc.perform(get("/reservar/5").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No quedan lugares disponibles para esta fecha.")))
                .andExpect(content().string(not(containsString("Continuar al pago"))));
    }

    @Test
    void missingScheduleGoesBackToTheListWithTheMessage() throws Exception {
        when(bookingService.getBookingSummary(99L)).thenThrow(new BookingException("La fecha elegida no existe."));

        mockMvc.perform(get("/reservar/99").principal(TOURIST))
                .andExpect(redirectedUrl("/actividades"))
                .andExpect(flash().attribute("errorMessage", "La fecha elegida no existe."));
    }

    @Test
    void createBookingRedirectsToThePaymentAndShowsTheHold() throws Exception {
        Booking created = new Booking();
        created.setId(42L);
        created.setHeldUntil(LocalDateTime.of(2030, 1, 1, 14, 35));
        when(bookingService.createBooking(any(), any())).thenReturn(created);

        mockMvc.perform(post("/reservar/5").principal(TOURIST)
                        .param("holderParticipates", "true")
                        .param("generalNotes", "Llegamos justo"))
                .andExpect(redirectedUrl("/payments/bookings/42"))
                .andExpect(flash().attribute("successMessage",
                        containsString("retenidos hasta las 14:35")));
    }

    @Test
    void theScheduleAlwaysComesFromTheUrl() throws Exception {
        Booking created = new Booking();
        created.setId(42L);
        created.setHeldUntil(LocalDateTime.now());
        when(bookingService.createBooking(any(), any())).thenReturn(created);

        mockMvc.perform(post("/reservar/5").principal(TOURIST).param("activityScheduleId", "999"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<BookingRequestDTO> request = ArgumentCaptor.forClass(BookingRequestDTO.class);
        verify(bookingService).createBooking(any(), request.capture());
        assertThat(request.getValue().getActivityScheduleId()).isEqualTo(5L);
    }

    @Test
    void companionsAreBoundFromTheForm() throws Exception {
        Booking created = new Booking();
        created.setId(42L);
        created.setHeldUntil(LocalDateTime.now());
        when(bookingService.createBooking(any(), any())).thenReturn(created);

        mockMvc.perform(post("/reservar/5").principal(TOURIST)
                        .param("participants[0].name", "Luz")
                        .param("participants[0].lastName", "Prueba")
                        .param("participants[0].documentType", "CI")
                        .param("participants[0].documentNumber", "1234567")
                        .param("participants[0].dateOfBirth", "2000-05-10"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<BookingRequestDTO> request = ArgumentCaptor.forClass(BookingRequestDTO.class);
        verify(bookingService).createBooking(any(), request.capture());
        assertThat(request.getValue().getParticipants()).hasSize(1);
        assertThat(request.getValue().getParticipants().get(0).getName()).isEqualTo("Luz");
    }

    @Test
    void incompleteCompanionShowsTheErrorAndKeepsTheForm() throws Exception {
        mockMvc.perform(post("/reservar/5").principal(TOURIST)
                        .param("participants[0].name", "Luz"))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/form"))
                .andExpect(content().string(containsString("El apellido es obligatorio")))
                .andExpect(content().string(containsString("value=\"Luz\"")));
        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    void bookingErrorsAreShownOnTheForm() throws Exception {
        when(bookingService.createBooking(any(), any())).thenThrow(new NotEnoughSpotsException(1, 2));

        mockMvc.perform(post("/reservar/5").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/form"))
                .andExpect(content().string(containsString("Solo quedan 1 lugares disponibles")));
    }

    private BookingSummaryDTO summary(String blockedReason) {
        return new BookingSummaryDTO(5L, 10L, "Cabalgata", "Emprendimiento", "Minas",
                LocalDateTime.of(2030, 1, 1, 10, 0), 60, new BigDecimal("1500.00"), 12, 4, 10, blockedReason);
    }
}
