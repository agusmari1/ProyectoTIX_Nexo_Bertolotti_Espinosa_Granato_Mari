package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;
import uy.edu.um.nexo.mnatural.service.PaymentWebhookService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sin filtros de seguridad: se prueba el controller (las reglas de acceso las prueba PaymentWebhookSecurityTest).
// El service está simulado: no se toca la base de datos ni internet
@WebMvcTest(PaymentWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentWebhookService webhookService;

    @Test
    void aNotificationWithTheIdInTheQueryStringIsProcessed() throws Exception {
        mockMvc.perform(post("/payments/webhook").param("type", "payment").param("data.id", "123456"))
                .andExpect(status().isOk());

        verify(webhookService).handlePaymentNotification("123456");
    }

    @Test
    void aNotificationWithTheIdInTheJsonBodyIsProcessed() throws Exception {
        mockMvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"777\"}}"))
                .andExpect(status().isOk());

        verify(webhookService).handlePaymentNotification("777");
    }

    @Test
    void aNumericIdInTheJsonBodyIsAlsoAccepted() throws Exception {
        mockMvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"data\":{\"id\":888}}"))
                .andExpect(status().isOk());

        verify(webhookService).handlePaymentNotification("888");
    }

    @Test
    void theOldTopicFormatIsAlsoUnderstood() throws Exception {
        mockMvc.perform(post("/payments/webhook").param("topic", "payment").param("id", "555"))
                .andExpect(status().isOk());

        verify(webhookService).handlePaymentNotification("555");
    }

    @Test
    void notificationsThatAreNotAboutPaymentsAreAcknowledgedAndIgnored() throws Exception {
        mockMvc.perform(post("/payments/webhook").param("topic", "merchant_order").param("id", "999"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/payments/webhook").param("type", "plan").param("data.id", "1"))
                .andExpect(status().isOk());

        verify(webhookService, never()).handlePaymentNotification(anyString());
    }

    @Test
    void aNotificationWithoutAnIdIsAcknowledgedAndIgnored() throws Exception {
        mockMvc.perform(post("/payments/webhook").param("type", "payment")).andExpect(status().isOk());
        mockMvc.perform(post("/payments/webhook")).andExpect(status().isOk());

        verify(webhookService, never()).handlePaymentNotification(anyString());
    }

    @Test
    void ifTheProcessingFailsTheAnswerIsAnErrorSoMercadoPagoSendsItAgain() throws Exception {
        when(webhookService.handlePaymentNotification("123")).thenThrow(new PaymentGatewayException("timeout"));

        mockMvc.perform(post("/payments/webhook").param("type", "payment").param("data.id", "123"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void aNotificationThatIsNotOursStillGetsAnOkSoItIsNotRetried() throws Exception {
        when(webhookService.handlePaymentNotification("404")).thenReturn(false);

        mockMvc.perform(post("/payments/webhook").param("type", "payment").param("data.id", "404"))
                .andExpect(status().isOk());
    }
}
