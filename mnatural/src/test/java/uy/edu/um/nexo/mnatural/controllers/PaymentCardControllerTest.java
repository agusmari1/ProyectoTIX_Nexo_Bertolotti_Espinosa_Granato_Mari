package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.payment.CardPaymentRequest;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.BookingCancellationService;
import uy.edu.um.nexo.mnatural.service.BookingQrService;
import uy.edu.um.nexo.mnatural.service.PaymentConfirmationService;
import uy.edu.um.nexo.mnatural.service.PaymentService;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Pantalla de pago con formulario de tarjeta y su endpoint de cobro. Sin filtros de seguridad; los servicios están
// simulados: no se toca la base de datos ni internet
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentCardControllerTest {

    private static final Principal TOURIST = new UsernamePasswordAuthenticationToken("ana@example.com", "x");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private PaymentConfirmationService paymentConfirmationService;
    @MockitoBean
    private BookingCancellationService bookingCancellationService;
    @MockitoBean
    private BookingQrService qrService;
    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(7L);
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(tourist));
    }

    private BookingPaymentDTO payableBooking(boolean payable) {
        BookingPaymentDTO dto = mock(BookingPaymentDTO.class);
        when(dto.getBookingId()).thenReturn(30L);
        when(dto.getActivityName()).thenReturn("Cabalgata");
        when(dto.getStartDateTime()).thenReturn(LocalDateTime.of(2027, 1, 15, 9, 30));
        when(dto.getParticipantCount()).thenReturn(2);
        when(dto.getTotalAmount()).thenReturn(new BigDecimal("3000.00"));
        when(dto.getStatus()).thenReturn(BookingStatus.PENDING);
        when(dto.isPayable()).thenReturn(payable);
        when(dto.getAttempts()).thenReturn(List.of());
        when(dto.getRefunds()).thenReturn(List.of());
        return dto;
    }

    private static PaymentTransaction transaction(PaymentStatus status, String reason) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setStatus(status);
        transaction.setFailureReason(reason);
        return transaction;
    }

    private static final String SIMULATED_BODY =
            "{\"holderName\":\"APRO\",\"cardNumber\":\"4509 9535 6623 3704\",\"expiry\":\"11/30\","
                    + "\"securityCode\":\"123\",\"documentNumber\":\"12345678\"}";

    // ===== Pantalla de pago =====

    @Test
    void withoutAPublicKeyTheScreenShowsTheSimulatedTestForm() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(true);
        when(paymentService.checkoutPublicKey()).thenReturn(null);
        BookingPaymentDTO booking = payableBooking(true);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30/pay").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/pay"))
                .andExpect(content().string(containsString("simulated-card-form")))
                .andExpect(content().string(containsString("Pago simulado")))
                .andExpect(content().string(containsString("Cabalgata")))
                .andExpect(content().string(not(containsString("cardPaymentBrick_container"))))
                .andExpect(content().string(not(containsString("sdk.mercadopago.com"))))
                .andExpect(content().string(containsString("/js/checkout.js")));
    }

    @Test
    void withAPublicKeyTheScreenShowsTheMercadoPagoCardForm() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(true);
        when(paymentService.checkoutPublicKey()).thenReturn("TEST-public-key-inventada");
        BookingPaymentDTO booking = payableBooking(true);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30/pay").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cardPaymentBrick_container")))
                .andExpect(content().string(containsString("https://sdk.mercadopago.com/js/v2")))
                .andExpect(content().string(containsString("data-public-key=\"TEST-public-key-inventada\"")))
                .andExpect(content().string(containsString("data-amount=\"3000.00\"")))
                .andExpect(content().string(not(containsString("simulated-card-form"))));
    }

    @Test
    void aBookingThatCannotBePaidGoesBackToTheBookingScreen() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(true);
        BookingPaymentDTO booking = payableBooking(false);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30/pay").principal(TOURIST))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments/bookings/30"));
    }

    @Test
    void whenTheServiceUsesItsOwnPageThePayScreenGoesBackToTheBookingScreen() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(false);
        BookingPaymentDTO booking = payableBooking(true);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30/pay").principal(TOURIST))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/payments/bookings/30"));
    }

    // ===== Botón de pagar en la pantalla de la reserva =====

    @Test
    void theBookingScreenLinksToThePayScreenWhenThereIsACardForm() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(true);
        BookingPaymentDTO booking = payableBooking(true);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(model().attribute("cardForm", true))
                .andExpect(content().string(containsString("/payments/bookings/30/pay")))
                .andExpect(content().string(not(containsString("/payments/bookings/30/start"))));
    }

    @Test
    void theBookingScreenKeepsTheRedirectButtonWhenThereIsNoCardForm() throws Exception {
        when(paymentService.usesCardForm()).thenReturn(false);
        BookingPaymentDTO booking = payableBooking(true);
        when(paymentService.getBookingPayment(30L, 7L)).thenReturn(booking);

        mockMvc.perform(get("/payments/bookings/30").principal(TOURIST))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/payments/bookings/30/start")))
                .andExpect(content().string(not(containsString("/payments/bookings/30/pay"))));
    }

    // ===== Cobro =====

    @Test
    void anApprovedPaymentConfirmsTheBookingAndSaysSo() throws Exception {
        when(paymentService.payWithCard(eq(30L), eq(7L), any(CardPaymentRequest.class)))
                .thenReturn(transaction(PaymentStatus.APPROVED, null));
        when(paymentConfirmationService.confirmOrRefund(30L)).thenReturn(PaymentConfirmationService.Outcome.CONFIRMED);

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULATED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value(containsString("Tu reserva está confirmada")))
                .andExpect(jsonPath("$.redirectUrl").value("/payments/bookings/30"));
    }

    @Test
    void thePaymentFormDataIsPassedToTheServiceWithoutBeingTrusted() throws Exception {
        when(paymentService.payWithCard(anyLong(), anyLong(), any(CardPaymentRequest.class)))
                .thenReturn(transaction(PaymentStatus.APPROVED, null));
        when(paymentConfirmationService.confirmOrRefund(30L)).thenReturn(PaymentConfirmationService.Outcome.CONFIRMED);

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok_1\",\"payment_method_id\":\"master\",\"issuer_id\":\"24\","
                                + "\"installments\":1,\"transaction_amount\":1,"
                                + "\"payer\":{\"email\":\"a@b.com\",\"identification\":{\"type\":\"CI\",\"number\":\"1\"}}}"))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(CardPaymentRequest.class);
        verify(paymentService).payWithCard(eq(30L), eq(7L), captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("tok_1");
        assertThat(captor.getValue().payerEmail()).isEqualTo("a@b.com");
    }

    @Test
    void anApprovedPaymentForABookingThatAlreadyExpiredPromisesTheRefund() throws Exception {
        when(paymentService.payWithCard(anyLong(), anyLong(), any(CardPaymentRequest.class)))
                .thenReturn(transaction(PaymentStatus.APPROVED, null));
        when(paymentConfirmationService.confirmOrRefund(30L)).thenReturn(PaymentConfirmationService.Outcome.REFUND);

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULATED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.message").value(containsString("devolver el dinero")));
    }

    @Test
    void aRejectedPaymentExplainsWhyAndDoesNotConfirmAnything() throws Exception {
        when(paymentService.payWithCard(anyLong(), anyLong(), any(CardPaymentRequest.class)))
                .thenReturn(transaction(PaymentStatus.REJECTED, "La tarjeta fue rechazada."));

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULATED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.message").value(containsString("La tarjeta fue rechazada.")))
                .andExpect(jsonPath("$.message").value(containsString("intentar de nuevo")));

        verify(paymentConfirmationService, never()).confirmOrRefund(anyLong());
    }

    @Test
    void anInProcessPaymentIsReportedAsPendingAndNotConfirmed() throws Exception {
        when(paymentService.payWithCard(anyLong(), anyLong(), any(CardPaymentRequest.class)))
                .thenReturn(transaction(PaymentStatus.PENDING, null));

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULATED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value(containsString("en proceso")));

        verify(paymentConfirmationService, never()).confirmOrRefund(anyLong());
    }

    @Test
    void aPaymentThatCannotBeMadeAnswersWithAClearErrorAndNoServerDetails() throws Exception {
        when(paymentService.payWithCard(anyLong(), anyLong(), any(CardPaymentRequest.class)))
                .thenThrow(new PaymentException("Esta reserva ya está confirmada."));

        mockMvc.perform(post("/payments/bookings/30/card").principal(TOURIST)
                        .contentType(MediaType.APPLICATION_JSON).content(SIMULATED_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.message").value("Esta reserva ya está confirmada."));
    }
}
