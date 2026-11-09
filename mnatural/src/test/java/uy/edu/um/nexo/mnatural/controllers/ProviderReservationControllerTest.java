package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ProviderBookingDTO;
import uy.edu.um.nexo.mnatural.dto.ProviderScheduleDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.ProviderReservationService;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Sin filtros de seguridad: se prueba el controller. Los services están simulados, no se toca la base de datos
@WebMvcTest(ProviderReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderReservationControllerTest {

    private static final String EMAIL = "prestador@example.com";
    private static final Principal PROVIDER = new UsernamePasswordAuthenticationToken(EMAIL, "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderReservationService reservationService;

    @MockitoBean
    private ActivityService activityService;

    private ProviderBookingDTO confirmedBooking() {
        return new ProviderBookingDTO(5L, "MN-ABCD2345", BookingStatus.CONFIRMED, 10L, "Trekking", 20L,
                LocalDateTime.of(2027, 1, 15, 9, 30), 1, true, "Ana Pérez", "ana@ejemplo.com", "099111222",
                null, List.of(new ProviderBookingDTO.ParticipantRow("Luis", "Gómez", "CI", "12345678",
                java.time.LocalDate.of(1990, 5, 1), "Alérgico al maní")));
    }

    private ProviderBookingDTO pendingBooking() {
        return new ProviderBookingDTO(6L, null, BookingStatus.PENDING, 10L, "Trekking", 20L,
                LocalDateTime.of(2027, 1, 15, 9, 30), 2, false, null, null, null, null, List.of());
    }

    @Test
    void listWithoutFiltersShowsAllTheReservations() throws Exception {
        when(activityService.findActivitiesOf(EMAIL)).thenReturn(List.of());
        when(reservationService.findReservations(EMAIL, null, null)).thenReturn(List.of(confirmedBooking()));

        mockMvc.perform(get("/provider/reservations").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/reservations"))
                .andExpect(model().attributeExists("reservations"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MN-ABCD2345")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ana@ejemplo.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alérgico al maní")));
    }

    @Test
    void aPendingReservationDoesNotShowThePersonalData() throws Exception {
        when(activityService.findActivitiesOf(EMAIL)).thenReturn(List.of());
        when(reservationService.findReservations(EMAIL, null, null)).thenReturn(List.of(pendingBooking()));

        mockMvc.perform(get("/provider/reservations").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("solo cuando la reserva está confirmada")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Correo"))));
    }

    @Test
    void filteringByActivityAndScheduleIsPassedToTheServiceAndOffersTheSchedules() throws Exception {
        Activity activity = new Activity();
        activity.setId(10L);
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(20L);
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.of(2027, 1, 15, 9, 30));
        when(activityService.findActivitiesOf(EMAIL)).thenReturn(List.of(activity));
        when(activityService.findSchedules(EMAIL, 10L)).thenReturn(List.of(new ProviderScheduleDTO(schedule, 5)));
        when(reservationService.findReservations(EMAIL, 10L, 20L)).thenReturn(List.of(confirmedBooking()));

        mockMvc.perform(get("/provider/reservations").param("activityId", "10").param("scheduleId", "20")
                        .principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("schedules"))
                .andExpect(model().attribute("selectedActivityId", 10L))
                .andExpect(model().attribute("selectedScheduleId", 20L));

        verify(reservationService).findReservations(EMAIL, 10L, 20L);
    }

    @Test
    void anActivityThatIsNotYoursGoesBackToTheFullListWithAnError() throws Exception {
        when(activityService.findActivitiesOf(EMAIL)).thenReturn(List.of());
        when(activityService.findSchedules(EMAIL, 99L)).thenThrow(new ActivityNotFoundException(99L));

        mockMvc.perform(get("/provider/reservations").param("activityId", "99").principal(PROVIDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/reservations"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
