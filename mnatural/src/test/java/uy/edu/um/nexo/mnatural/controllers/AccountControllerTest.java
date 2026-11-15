package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.PasswordChangeDTO;
import uy.edu.um.nexo.mnatural.exception.InvalidPasswordChangeException;
import uy.edu.um.nexo.mnatural.service.AccountService;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Sin filtros de seguridad: se prueba el controller. El service está simulado, no se toca la base de datos
@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    private static final Principal USER = new UsernamePasswordAuthenticationToken("admin@example.com", "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void theFormIsShown() throws Exception {
        mockMvc.perform(get("/cuenta/contrasena").principal(USER))
                .andExpect(status().isOk())
                .andExpect(view().name("cuenta/contrasena"));
    }

    @Test
    void aValidChangeIsSavedForTheLoggedInUserAndRedirects() throws Exception {
        mockMvc.perform(post("/cuenta/contrasena").principal(USER)
                        .param("currentPassword", "actual1234")
                        .param("newPassword", "nueva12345")
                        .param("confirmPassword", "nueva12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cuenta/contrasena"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(accountService).changePassword(eq("admin@example.com"), any(PasswordChangeDTO.class));
    }

    @Test
    void passwordsThatDoNotMatchAreRejected() throws Exception {
        mockMvc.perform(post("/cuenta/contrasena").principal(USER)
                        .param("currentPassword", "actual1234")
                        .param("newPassword", "nueva12345")
                        .param("confirmPassword", "otra123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("cuenta/contrasena"))
                .andExpect(model().attributeHasFieldErrors("passwordChange", "confirmPassword"));

        verify(accountService, never()).changePassword(any(), any());
    }

    @Test
    void aShortNewPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/cuenta/contrasena").principal(USER)
                        .param("currentPassword", "actual1234")
                        .param("newPassword", "corta")
                        .param("confirmPassword", "corta"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordChange", "newPassword"));

        verify(accountService, never()).changePassword(any(), any());
    }

    @Test
    void missingFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/cuenta/contrasena").principal(USER))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordChange", "currentPassword", "newPassword",
                        "confirmPassword"));

        verify(accountService, never()).changePassword(any(), any());
    }

    @Test
    void aWrongCurrentPasswordIsShownNextToThatField() throws Exception {
        doThrow(new InvalidPasswordChangeException("currentPassword", "La contraseña actual no es correcta."))
                .when(accountService).changePassword(eq("admin@example.com"), any(PasswordChangeDTO.class));

        mockMvc.perform(post("/cuenta/contrasena").principal(USER)
                        .param("currentPassword", "equivocada")
                        .param("newPassword", "nueva12345")
                        .param("confirmPassword", "nueva12345"))
                .andExpect(status().isOk())
                .andExpect(view().name("cuenta/contrasena"))
                .andExpect(model().attributeHasFieldErrors("passwordChange", "currentPassword"));
    }
}
