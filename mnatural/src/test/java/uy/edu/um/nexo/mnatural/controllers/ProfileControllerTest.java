package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ProfileBusinessDTO;
import uy.edu.um.nexo.mnatural.dto.ProfileDTO;
import uy.edu.um.nexo.mnatural.dto.TouristBookingDTO;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.security.Role;
import uy.edu.um.nexo.mnatural.service.ProfileService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Quién puede entrar a /perfil se prueba en SecurityConfigTest
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    private static final String EMAIL = "ana@ejemplo.test";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProfileService profileService;

    @Test
    void touristSeesItsDataAndBookingHistory() throws Exception {
        TouristBookingDTO booking = new TouristBookingDTO(1L, "Cabalgata", LocalDateTime.of(2030, 1, 1, 10, 0), 2,
                new BigDecimal("1000.00"), BookingStatus.CONFIRMED, null, "MN-ABC", false);
        givenProfile(profile(Role.TOURIST, null, List.of(), List.of(booking)));

        mockMvc.perform(get("/perfil").principal(user(Role.TOURIST)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Mis datos"), containsString(EMAIL),
                        containsString("Cédula de identidad"), containsString("Cabalgata"),
                        containsString("Confirmada"), containsString("/payments/bookings/1"))))
                .andExpect(content().string(not(containsString("Estado de aprobación"))));
    }

    @Test
    void providerSeesItsBusinessAndApproval() throws Exception {
        ProfileBusinessDTO business = new ProfileBusinessDTO("Mi Emprendimiento", LegalType.COMPANY, 123456789012L,
                "Calle 1", "099000000", "contacto@ejemplo.test", "Cabalgatas");
        givenProfile(profile(Role.PROVIDER, ApprovalStatus.APPROVED, List.of(business), List.of()));

        mockMvc.perform(get("/perfil").principal(user(Role.PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Estado de aprobación"), containsString("Aprobado"),
                        containsString("Mi Emprendimiento"), containsString("Empresa"), containsString("123456789012"))))
                .andExpect(content().string(not(containsString("Mis reservas"))));
    }

    @Test
    void adminSeesOnlyItsData() throws Exception {
        givenProfile(profile(Role.ADMIN, null, List.of(), List.of()));

        mockMvc.perform(get("/perfil").principal(user(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mis datos")))
                .andExpect(content().string(not(containsString("Estado de aprobación"))))
                .andExpect(content().string(not(containsString("Mis reservas"))));
    }

    @Test
    void headerShowsTheOptionsOfEachRole() throws Exception {
        givenProfile(profile(Role.TOURIST, null, List.of(), List.of()));
        mockMvc.perform(get("/perfil").principal(user(Role.TOURIST)))
                .andExpect(content().string(allOf(containsString("Mi perfil"), containsString("/turista/reservas"),
                        containsString("Cerrar sesión"))))
                .andExpect(content().string(not(containsString("/provider/businesses"))))
                .andExpect(content().string(not(containsString("/admin/prestadores"))))
                .andExpect(content().string(not(containsString("Iniciar sesión"))));

        givenProfile(profile(Role.PROVIDER, ApprovalStatus.APPROVED, List.of(), List.of()));
        mockMvc.perform(get("/perfil").principal(user(Role.PROVIDER)))
                .andExpect(content().string(allOf(containsString("Mi emprendimiento"), containsString("Mis actividades"),
                        containsString("Reservas recibidas"), containsString("/provider/verificar"),
                        containsString("/cuenta/contrasena"))))
                .andExpect(content().string(not(containsString("/turista/reservas"))))
                .andExpect(content().string(not(containsString("/admin/prestadores"))));

        givenProfile(profile(Role.ADMIN, null, List.of(), List.of()));
        mockMvc.perform(get("/perfil").principal(user(Role.ADMIN)))
                .andExpect(content().string(allOf(containsString("Prestadores pendientes"),
                        containsString("Tipos de actividad"), containsString("/admin/administradores"))))
                .andExpect(content().string(not(containsString("/turista/reservas"))))
                .andExpect(content().string(not(containsString("/provider/businesses"))));
    }

    @Test
    void userThatNoLongerExistsGoesToTheHome() throws Exception {
        when(profileService.findProfile(EMAIL)).thenReturn(Optional.empty());

        mockMvc.perform(get("/perfil").principal(user(Role.TOURIST))).andExpect(redirectedUrl("/"));
    }

    private void givenProfile(ProfileDTO profile) {
        when(profileService.findProfile(EMAIL)).thenReturn(Optional.of(profile));
    }

    private ProfileDTO profile(Role role, ApprovalStatus approval, List<ProfileBusinessDTO> businesses,
                               List<TouristBookingDTO> bookings) {
        return new ProfileDTO(role, "Ana", "Prueba", EMAIL, LocalDate.of(1995, 5, 10), DocumentType.CI, "1234567",
                "Uruguay", "099111222", null, approval, businesses, bookings);
    }

    private Principal user(Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(EMAIL, null,
                AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
    }
}
