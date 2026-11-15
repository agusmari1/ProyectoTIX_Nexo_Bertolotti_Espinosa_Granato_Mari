package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.ActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.SuspensionResult;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

// Sin filtros de seguridad: se prueba el controller. El service está simulado, no se toca la base de datos
@WebMvcTest(ProviderActivityManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderActivityManagementControllerTest {

    private static final Principal PROVIDER = new UsernamePasswordAuthenticationToken("prestador@example.com", "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActivityService activityService;

    private Activity activity() {
        Business business = new Business();
        business.setId(2L);
        Activity activity = new Activity();
        activity.setId(10L);
        activity.setBusiness(business);
        activity.setName("Trekking");
        activity.setDescription("Descripción");
        activity.setAddress("Plaza");
        activity.setDepartment(Department.LAVALLEJA);
        activity.setLocality("Minas");
        activity.setDurationMinutes(60);
        activity.setPricePerPerson(new BigDecimal("500"));
        activity.setBookingCutoffMinutes(0);
        activity.setStatus(ActivityStatus.PUBLISHED);
        return activity;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost(String price) {
        return post("/provider/activities/10/edit").principal(PROVIDER)
                .param("businessId", "2")
                .param("name", "Trekking")
                .param("description", "Descripción nueva")
                .param("address", "Plaza")
                .param("department", "LAVALLEJA")
                .param("locality", "Minas")
                .param("durationMinutes", "60")
                .param("pricePerPerson", price)
                .param("bookingCutoffMinutes", "0")
                .param("activityTypeIds", "1");
    }

    @Test
    void editFormIsFilledWithTheCurrentData() throws Exception {
        when(activityService.findOwnActivity("prestador@example.com", 10L)).thenReturn(activity());
        when(activityService.findEnabledTypes()).thenReturn(List.of());

        mockMvc.perform(get("/provider/activities/10/edit").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/activity-edit"))
                .andExpect(model().attributeExists("activity"))
                .andExpect(model().attribute("activityName", "Trekking"))
                .andExpect(model().attribute("suspended", false));
    }

    @Test
    void editingAnActivityThatIsNotYoursGoesBackToTheListWithAnError() throws Exception {
        when(activityService.findOwnActivity("prestador@example.com", 99L)).thenThrow(new ActivityNotFoundException(99L));

        mockMvc.perform(get("/provider/activities/99/edit").principal(PROVIDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/activities"))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void savingValidChangesRedirectsToTheList() throws Exception {
        Activity current = activity();
        when(activityService.findOwnActivity("prestador@example.com", 10L)).thenReturn(current);
        when(activityService.update(eq("prestador@example.com"), eq(10L), any(ActivityDTO.class))).thenReturn(current);

        mockMvc.perform(validPost("500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/activities"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void whenThePriceChangesTheMessageSaysExistingBookingsKeepTheirPrice() throws Exception {
        Activity current = activity();
        Activity updated = activity();
        updated.setPricePerPerson(new BigDecimal("900"));
        when(activityService.findOwnActivity("prestador@example.com", 10L)).thenReturn(current);
        when(activityService.update(eq("prestador@example.com"), eq(10L), any(ActivityDTO.class))).thenReturn(updated);

        var result = mockMvc.perform(validPost("900"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Object message = result.getFlashMap().get("successMessage");
        assertThat(message).asString().contains("las que ya se hicieron").contains("mantienen el precio");
    }

    @Test
    void missingRequiredFieldsShowTheFormAgainAndNothingIsSaved() throws Exception {
        when(activityService.findOwnActivity("prestador@example.com", 10L)).thenReturn(activity());

        mockMvc.perform(post("/provider/activities/10/edit").principal(PROVIDER)
                        .param("name", "Trekking")
                        .param("description", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/activity-edit"))
                .andExpect(model().attributeHasFieldErrors("activity", "description"));

        verify(activityService, never()).update(any(), any(), any());
    }

    @Test
    void aBusinessRuleErrorIsShownNextToItsField() throws Exception {
        when(activityService.findOwnActivity("prestador@example.com", 10L)).thenReturn(activity());
        when(activityService.update(eq("prestador@example.com"), eq(10L), any(ActivityDTO.class)))
                .thenThrow(new ActivityValidationException("pricePerPerson", "El precio tiene que ser mayor a 0."));

        mockMvc.perform(validPost("0"))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/activity-edit"))
                .andExpect(model().attributeHasFieldErrors("activity", "pricePerPerson"));
    }

    @Test
    void suspendingTheActivityReportsCancellationsAndRefunds() throws Exception {
        when(activityService.suspendActivity("prestador@example.com", 10L)).thenReturn(new SuspensionResult(3, 2, 1));

        var result = mockMvc.perform(post("/provider/activities/10/suspend").principal(PROVIDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/activities"))
                .andReturn();

        assertThat(result.getFlashMap().get("successMessage")).asString()
                .contains("3 reservas").contains("100 %").contains("1 reembolsos");
    }

    @Test
    void suspendingAnActivityThatIsNotYoursGoesBackToTheListWithAnError() throws Exception {
        when(activityService.suspendActivity("prestador@example.com", 99L)).thenThrow(new ActivityNotFoundException(99L));

        mockMvc.perform(post("/provider/activities/99/suspend").principal(PROVIDER))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/activities"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
