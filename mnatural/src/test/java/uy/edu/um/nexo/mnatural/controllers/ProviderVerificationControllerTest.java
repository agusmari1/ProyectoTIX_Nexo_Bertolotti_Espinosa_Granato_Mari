package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.BookingVerificationDTO;
import uy.edu.um.nexo.mnatural.service.BookingVerificationService;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Sin filtros de seguridad: se prueba el controller. El service está simulado, no se toca la base de datos
@WebMvcTest(ProviderVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProviderVerificationControllerTest {

    private static final String EMAIL = "prestador@example.com";
    private static final Principal PROVIDER = new UsernamePasswordAuthenticationToken(EMAIL, "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingVerificationService verificationService;

    @Test
    void withoutCodeOnlyTheFormIsShown() throws Exception {
        mockMvc.perform(get("/provider/verificar").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(view().name("provider/verificar"))
                .andExpect(model().attributeDoesNotExist("result"))
                .andExpect(content().string(containsString("Código de reserva")));

        verify(verificationService, never()).verify(anyString(), any());
    }

    @Test
    void aValidCodeShowsTheReservationData() throws Exception {
        when(verificationService.verify(EMAIL, "MN-ABCD2345")).thenReturn(new BookingVerificationDTO(true,
                "Reserva válida.", "MN-ABCD2345", "Trekking", LocalDateTime.of(2027, 1, 15, 9, 30),
                "Ana Pérez", 2, "Confirmada"));

        mockMvc.perform(get("/provider/verificar").param("code", "MN-ABCD2345").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reserva válida")))
                .andExpect(content().string(containsString("Trekking")))
                .andExpect(content().string(containsString("15/01/2027 09:30")))
                .andExpect(content().string(containsString("Ana Pérez")))
                .andExpect(content().string(not(containsString("Código no válido"))));
    }

    @Test
    void anInvalidCodeShowsTheReason() throws Exception {
        when(verificationService.verify(EMAIL, "MN-ZZZZ9999"))
                .thenReturn(BookingVerificationDTO.invalid("El código no corresponde a ninguna reserva de tus actividades."));

        mockMvc.perform(get("/provider/verificar").param("code", "MN-ZZZZ9999").principal(PROVIDER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Código no válido")))
                .andExpect(content().string(containsString("ninguna reserva de tus actividades")))
                .andExpect(content().string(not(containsString("Reserva válida"))));
    }
}
