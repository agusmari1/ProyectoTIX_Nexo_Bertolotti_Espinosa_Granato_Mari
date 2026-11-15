package uy.edu.um.nexo.mnatural.weather;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.controllers.PublicActivityController;
import uy.edu.um.nexo.mnatural.dto.ScheduleAvailabilityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Se renderiza el detalle público de una actividad con el fragmento del clima. WeatherService está simulado:
// no se llama a internet
@WebMvcTest(PublicActivityController.class)
@AutoConfigureMockMvc(addFilters = false)
class WeatherFragmentTest {

    private static final LocalDateTime START = LocalDateTime.of(2027, 1, 12, 9, 30);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityCatalogService catalogService;

    @MockitoBean
    private ActivityTypeRepository activityTypeRepository;

    @MockitoBean(name = "weatherService")
    private WeatherService weatherService;

    private void publishedActivity() {
        Business business = new Business();
        business.setName("Cabalgatas del Sur");
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setName("Trekking en las sierras");
        activity.setDescription("Una caminata");
        activity.setAddress("Plaza");
        activity.setDepartment(Department.LAVALLEJA);
        activity.setLocality("Minas");
        activity.setDurationMinutes(120);
        activity.setPricePerPerson(new BigDecimal("500"));
        activity.setBusiness(business);

        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setId(20L);
        schedule.setActivity(activity);
        schedule.setStartDateTime(START);

        when(catalogService.findPublished(10L)).thenReturn(Optional.of(activity));
        when(catalogService.upcomingSchedules(activity)).thenReturn(List.of(new ScheduleAvailabilityDTO(schedule, 5, true)));
    }

    @Test
    void theDetailShowsTheForecastAndPassesTheScheduleDatesToTheService() throws Exception {
        publishedActivity();
        when(weatherService.forecastsFor(eq(Department.LAVALLEJA), any())).thenReturn(List.of(
                new WeatherForecast(LocalDate.of(2027, 1, 12), 17, 25, 80, "Lluvia")));

        mockMvc.perform(get("/actividades/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pronóstico del tiempo")))
                .andExpect(content().string(containsString("12/01/2027")))
                .andExpect(content().string(containsString("Lluvia")))
                .andExpect(content().string(containsString("17° / 25°")))
                .andExpect(content().string(containsString("Lluvia: 80 %")))
                .andExpect(content().string(containsString("Open-Meteo.com")));

        verify(weatherService).forecastsFor(Department.LAVALLEJA, List.of(START));
    }

    @Test
    void withoutForecastTheBlockIsNotShownButTheRestOfThePageIs() throws Exception {
        publishedActivity();
        when(weatherService.forecastsFor(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/actividades/10"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Pronóstico del tiempo"))))
                .andExpect(content().string(containsString("Trekking en las sierras")))
                .andExpect(content().string(containsString("Datos útiles")))
                .andExpect(content().string(containsString("Próximas fechas")))
                .andExpect(content().string(containsString("Reservar")));
    }
}
