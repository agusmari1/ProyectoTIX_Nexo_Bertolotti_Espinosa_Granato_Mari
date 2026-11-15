package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.BusinessDTO;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.exception.BusinessNotFoundException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.service.BusinessService;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

// Sin filtros de seguridad: acá se prueba el controller; las reglas de acceso son de SecurityConfig.
// El service está simulado, así que no se toca la base de datos
@WebMvcTest(ProviderBusinessController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderBusinessControllerTest {

    private static final Principal PROVIDER = new UsernamePasswordAuthenticationToken("prestador@example.com", "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessService businessService;

    private Business business() {
        Business business = new Business();
        business.setId(5L);
        business.setName("Cabalgatas del Sur");
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Ruta 9");
        business.setPhoneNumber("099123456");
        business.setEmail("contacto@sur.com");
        return business;
    }

    @Test
    void listShowsTheBusinessesOfTheProvider() throws Exception {
        when(businessService.findOwn("prestador@example.com")).thenReturn(List.of(business()));

        mockMvc.perform(get("/provider/businesses").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/businesses/list"))
                .andExpect(model().attributeExists("businesses"));
    }

    @Test
    void newFormIsShown() throws Exception {
        mockMvc.perform(get("/provider/businesses/new").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/businesses/form"))
                .andExpect(model().attribute("editing", false));
    }

    @Test
    void createWithValidDataSavesAndRedirectsToTheList() throws Exception {
        mockMvc.perform(post("/provider/businesses").principal(PROVIDER)
                        .param("name", "Cabalgatas del Sur")
                        .param("legalType", "INDIVIDUAL")
                        .param("address", "Ruta 9")
                        .param("phoneNumber", "099123456")
                        .param("email", "contacto@sur.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/businesses"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(businessService).create(eq("prestador@example.com"), any(BusinessDTO.class));
    }

    @Test
    void createWithMissingDataShowsTheFormAgainAndDoesNotSave() throws Exception {
        mockMvc.perform(post("/provider/businesses").principal(PROVIDER)
                        .param("name", "")
                        .param("legalType", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/businesses/form"))
                .andExpect(model().attributeHasFieldErrors("business", "name", "address", "phoneNumber", "email"));

        verify(businessService, never()).create(any(), any());
    }

    @Test
    void rutWithLettersGetsTheClearMessageInsteadOfTheTechnicalOne() throws Exception {
        mockMvc.perform(post("/provider/businesses").principal(PROVIDER)
                        .param("name", "Mi emprendimiento")
                        .param("legalType", "COMPANY")
                        .param("rut", "abc")
                        .param("address", "Ruta 9")
                        .param("phoneNumber", "099123456")
                        .param("email", "contacto@sur.com"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("business", "rut"));

        verify(businessService, never()).create(any(), any());
    }

    @Test
    void companyWithoutRutShowsTheErrorNextToTheRutField() throws Exception {
        doThrow(new RutRequiredException()).when(businessService).create(any(), any(BusinessDTO.class));

        mockMvc.perform(post("/provider/businesses").principal(PROVIDER)
                        .param("name", "Mi empresa")
                        .param("legalType", "COMPANY")
                        .param("address", "Ruta 9")
                        .param("phoneNumber", "099123456")
                        .param("email", "contacto@sur.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/businesses/form"))
                .andExpect(model().attributeHasFieldErrors("business", "rut"));
    }

    @Test
    void editFormIsFilledWithTheCurrentData() throws Exception {
        when(businessService.findOwn("prestador@example.com", 5L)).thenReturn(business());

        mockMvc.perform(get("/provider/businesses/5/edit").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/businesses/form"))
                .andExpect(model().attribute("editing", true))
                .andExpect(model().attributeExists("business"));
    }

    @Test
    void editingSomeoneElsesBusinessGoesBackToTheListWithAnError() throws Exception {
        when(businessService.findOwn("prestador@example.com", 99L)).thenThrow(new BusinessNotFoundException());

        mockMvc.perform(get("/provider/businesses/99/edit").principal(PROVIDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/businesses"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void updateWithValidDataSavesAndRedirectsToTheList() throws Exception {
        mockMvc.perform(post("/provider/businesses/5").principal(PROVIDER)
                        .param("name", "Cabalgatas del Sur")
                        .param("legalType", "INDIVIDUAL")
                        .param("address", "Ruta 9")
                        .param("phoneNumber", "099123456")
                        .param("email", "contacto@sur.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/businesses"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(businessService).update(eq("prestador@example.com"), eq(5L), any(BusinessDTO.class));
    }

    @Test
    void updateWithInvalidEmailShowsTheFormAgain() throws Exception {
        mockMvc.perform(post("/provider/businesses/5").principal(PROVIDER)
                        .param("name", "Cabalgatas del Sur")
                        .param("legalType", "INDIVIDUAL")
                        .param("address", "Ruta 9")
                        .param("phoneNumber", "099123456")
                        .param("email", "no-es-un-correo"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("business", "email"));

        verify(businessService, never()).update(any(), any(), any());
    }
}
