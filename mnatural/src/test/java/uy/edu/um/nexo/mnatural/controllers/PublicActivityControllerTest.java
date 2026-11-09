package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.security.Role;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;
import uy.edu.um.nexo.mnatural.weather.WeatherService;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// El encabezado del portal según la sesión, en el listado y en el detalle
@WebMvcTest(PublicActivityController.class)
class PublicActivityControllerTest {

    private static final String[] PAGES = {"/actividades", "/actividades/1"};

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityCatalogService catalogService;
    @MockitoBean
    private ActivityTypeRepository activityTypeRepository;
    // El nombre importa: la plantilla del clima lo busca como @weatherService
    @MockitoBean(name = "weatherService")
    private WeatherService weatherService;

    @BeforeEach
    void setUp() {
        Business business = new Business();
        business.setName("Emprendimiento");

        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("Cabalgata");
        activity.setDescription("Descripción");
        activity.setDepartment(Department.ROCHA);
        activity.setLocality("Castillos");
        activity.setAddress("Punto de encuentro");
        activity.setPricePerPerson(new BigDecimal("500.00"));
        activity.setBusiness(business);

        when(catalogService.search(any())).thenReturn(List.of());
        when(catalogService.findPublished(1L)).thenReturn(Optional.of(activity));
        when(catalogService.upcomingSchedules(activity)).thenReturn(List.of());
        when(activityTypeRepository.findAll(any(Sort.class))).thenReturn(List.of());
    }

    @Test
    void visitorSeesTheLoginButton() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Iniciar sesión")))
                    .andExpect(content().string(not(containsString("Cerrar sesión"))))
                    .andExpect(content().string(not(containsString("Mis reservas"))));
        }
    }

    @Test
    void touristSeesItsBookingsAndLogoutInsteadOfLogin() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page).principal(userWithRole(Role.TOURIST)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Iniciar sesión"))))
                    .andExpect(content().string(containsString("Mis reservas")))
                    .andExpect(content().string(containsString("Cerrar sesión")));
        }
    }

    @Test
    void providerSeesItsPanelAndLogoutInsteadOfLogin() throws Exception {
        for (String page : PAGES) {
            mockMvc.perform(get(page).principal(userWithRole(Role.PROVIDER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Iniciar sesión"))))
                    .andExpect(content().string(not(containsString("Mis reservas"))))
                    .andExpect(content().string(containsString("Mis actividades")))
                    .andExpect(content().string(containsString("Cerrar sesión")));
        }
    }

    private Principal userWithRole(Role role) {
        return UsernamePasswordAuthenticationToken.authenticated("usuario@ejemplo.test", null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
