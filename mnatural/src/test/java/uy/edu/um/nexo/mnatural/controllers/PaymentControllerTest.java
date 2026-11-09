package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uy.edu.um.nexo.mnatural.config.SecurityConfig;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.payment.PaymentStart;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.service.BookingCancellationService;
import uy.edu.um.nexo.mnatural.service.BookingConfirmationService;
import uy.edu.um.nexo.mnatural.service.BookingQrService;
import uy.edu.um.nexo.mnatural.service.CancellationResult;
import uy.edu.um.nexo.mnatural.service.PaymentService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Solo la capa web, con la seguridad real (SecurityConfig). Los services son mocks: no se usa la base
// ni ningún servicio de pagos
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentControllerTest {

    private static final String TOURIST_EMAIL = "ana@ejemplo.test";
    private static final Long TOURIST_ID = 7L;
    private static final Long BOOKING_ID = 30L;

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private BookingConfirmationService bookingConfirmationService;
    @MockitoBean
    private BookingCancellationService bookingCancellationService;
    @MockitoBean
    private BookingQrService qrService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private AppUserDetailsService userDetailsService;

    private Booking booking;

    @BeforeEach
    void setUp() {
        Tourist tourist = new Tourist();
        tourist.setId(TOURIST_ID);
        tourist.setEmail(TOURIST_EMAIL);
        when(userRepository.findByEmail(TOURIST_EMAIL)).thenReturn(Optional.of(tourist));

        Activity activity = new Activity();
        activity.setName("Kayak");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));
        booking = new Booking();
        booking.setId(BOOKING_ID);
        booking.setTourist(tourist);
        booking.setActivitySchedule(schedule);
        booking.setParticipantCount(2);
        booking.setTotalAmount(new BigDecimal("3000.00"));
        booking.setStatus(BookingStatus.PENDING);
        booking.setBookingCode("MN-7KQ2XH4P");
        booking.setHeldUntil(LocalDateTime.now().plusMinutes(10));
    }

    // ===== Permisos =====

    @Test
    void withoutLoginGoesToTheLoginPage() throws Exception {
        mockMvc.perform(get("/payments/bookings/30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "prestador@ejemplo.test", roles = "PROVIDER")
    void aUserThatIsNotATouristSeesTheBookingAsNonexistent() throws Exception {
        when(userRepository.findByEmail("prestador@ejemplo.test")).thenReturn(Optional.of(new Provider()));

        mockMvc.perform(get("/payments/bookings/30"))
                .andExpect(view().name("payments/error"))
                .andExpect(model().attribute("errorMessage", "La reserva no existe."));
        verify(paymentService, never()).getBookingPayment(any(), any());
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void anotherTouristsBookingShowsTheErrorPage() throws Exception {
        when(paymentService.getBookingPayment(BOOKING_ID, TOURIST_ID)).thenThrow(new PaymentException("La reserva no existe."));

        mockMvc.perform(get("/payments/bookings/30"))
                .andExpect(view().name("payments/error"))
                .andExpect(model().attribute("errorMessage", "La reserva no existe."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void postingWithoutTheCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/payments/bookings/30/start"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/payments/bookings/30/cancel"))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).startPayment(any(), any());
        verify(bookingCancellationService, never()).cancelByTourist(any(), any());
    }

    // ===== Pantalla de la reserva =====

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void showsThePendingBookingWithThePayButtonAndNoCodeYet() throws Exception {
        when(paymentService.getBookingPayment(BOOKING_ID, TOURIST_ID)).thenReturn(payment(List.of(), true));

        mockMvc.perform(get("/payments/bookings/30"))
                .andExpect(status().isOk())
                .andExpect(view().name("payments/booking"))
                .andExpect(content().string(containsString("Kayak")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("MN-7KQ2XH4P"))));
        // Antes de mostrarla consulta los pagos que quedaron en proceso
        verify(paymentService).syncPendingPayments(BOOKING_ID, TOURIST_ID);
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aPaidButPendingBookingIsConfirmedWhenTheScreenOpens() throws Exception {
        PaymentTransaction approved = transaction(PaymentStatus.APPROVED);
        BookingPaymentDTO before = payment(List.of(approved), false);
        booking.setStatus(BookingStatus.CONFIRMED);
        BookingPaymentDTO after = payment(List.of(approved), false);
        when(paymentService.getBookingPayment(BOOKING_ID, TOURIST_ID)).thenReturn(before, after);
        when(bookingConfirmationService.confirmIfPaid(BOOKING_ID)).thenReturn(true);

        mockMvc.perform(get("/payments/bookings/30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MN-7KQ2XH4P")));
        verify(bookingConfirmationService).confirmIfPaid(BOOKING_ID);
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void theQrIsOnlyAvailableForAConfirmedBooking() throws Exception {
        when(paymentService.getBookingPayment(BOOKING_ID, TOURIST_ID)).thenReturn(payment(List.of(), true));
        mockMvc.perform(get("/payments/bookings/30/qr.png"))
                .andExpect(status().isNotFound());

        booking.setStatus(BookingStatus.CONFIRMED);
        when(paymentService.getBookingPayment(BOOKING_ID, TOURIST_ID)).thenReturn(payment(List.of(), false));
        when(qrService.generatePng("MN-7KQ2XH4P")).thenReturn(new byte[]{1, 2, 3});
        mockMvc.perform(get("/payments/bookings/30/qr.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    // ===== Iniciar el pago =====

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void startingThePaymentRedirectsToThePaymentPage() throws Exception {
        when(paymentService.startPayment(BOOKING_ID, TOURIST_ID))
                .thenReturn(new PaymentStart(transaction(PaymentStatus.PENDING), "http://localhost:8080/payments/return?external_reference=SIM-1"));

        mockMvc.perform(post("/payments/bookings/30/start").with(csrf()))
                .andExpect(redirectedUrl("http://localhost:8080/payments/return?external_reference=SIM-1"));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void ifTheGatewayIsDownTheReasonIsShownAndTheBookingStaysPending() throws Exception {
        PaymentTransaction failed = transaction(PaymentStatus.ERROR);
        failed.setFailureReason("No se pudo conectar con el servicio de pagos.");
        when(paymentService.startPayment(BOOKING_ID, TOURIST_ID)).thenReturn(new PaymentStart(failed, null));

        mockMvc.perform(post("/payments/bookings/30/start").with(csrf()))
                .andExpect(redirectedUrl("/payments/bookings/30"))
                .andExpect(flash().attribute("errorMessage", "No se pudo conectar con el servicio de pagos."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aBookingThatCannotBePaidShowsTheReason() throws Exception {
        when(paymentService.startPayment(BOOKING_ID, TOURIST_ID))
                .thenThrow(new PaymentException("Venció el plazo para pagar esta reserva."));

        mockMvc.perform(post("/payments/bookings/30/start").with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Venció el plazo para pagar esta reserva."));
    }

    // ===== Vuelta desde la página de pago =====

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void anApprovedPaymentConfirmsTheBooking() throws Exception {
        when(paymentService.syncPaymentByReference("SIM-1", TOURIST_ID)).thenReturn(transaction(PaymentStatus.APPROVED));
        when(bookingConfirmationService.confirmIfPaid(BOOKING_ID)).thenReturn(true);

        mockMvc.perform(get("/payments/return").param("external_reference", "SIM-1"))
                .andExpect(redirectedUrl("/payments/bookings/30"))
                .andExpect(flash().attribute("successMessage", "¡Pago aprobado! Tu reserva está confirmada."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void anApprovedPaymentOnAnExpiredBookingSaysTheMoneyIsReturned() throws Exception {
        when(paymentService.syncPaymentByReference("SIM-1", TOURIST_ID)).thenReturn(transaction(PaymentStatus.APPROVED));
        when(bookingConfirmationService.confirmIfPaid(BOOKING_ID)).thenReturn(false);

        mockMvc.perform(get("/payments/return").param("external_reference", "SIM-1"))
                .andExpect(flash().attribute("errorMessage", containsString("devolver el dinero")));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aRejectedPaymentLetsTheTouristTryAgain() throws Exception {
        PaymentTransaction rejected = transaction(PaymentStatus.REJECTED);
        rejected.setFailureReason("Fondos insuficientes.");
        when(paymentService.syncPaymentByReference("SIM-1", TOURIST_ID)).thenReturn(rejected);

        mockMvc.perform(get("/payments/return").param("external_reference", "SIM-1"))
                .andExpect(flash().attribute("errorMessage", containsString("Fondos insuficientes.")))
                .andExpect(flash().attribute("errorMessage", containsString("podés intentar de nuevo")));
        verify(bookingConfirmationService, never()).confirmIfPaid(any());
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aPaymentInProcessShowsAnInfoMessage() throws Exception {
        when(paymentService.syncPaymentByReference("SIM-1", TOURIST_ID)).thenReturn(transaction(PaymentStatus.PENDING));

        mockMvc.perform(get("/payments/return").param("external_reference", "SIM-1"))
                .andExpect(flash().attribute("infoMessage", containsString("en proceso")));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void returningWithAnUnknownReferenceShowsTheErrorPage() throws Exception {
        when(paymentService.syncPaymentByReference(any(), any())).thenThrow(new PaymentException("No se encontró el pago."));

        mockMvc.perform(get("/payments/return"))
                .andExpect(view().name("payments/error"))
                .andExpect(model().attribute("errorMessage", "No se encontró el pago."));
    }

    // ===== Cancelar =====

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void cancellingShowsTheRefund() throws Exception {
        when(bookingCancellationService.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .thenReturn(new CancellationResult(true, null, "Cancelaste tu reserva."));

        mockMvc.perform(post("/payments/bookings/30/cancel").with(csrf()))
                .andExpect(redirectedUrl("/payments/bookings/30"))
                .andExpect(flash().attribute("successMessage", "Cancelaste tu reserva."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aFailedRefundKeepsTheBookingAndShowsTheReason() throws Exception {
        when(bookingCancellationService.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .thenReturn(new CancellationResult(false, null, "Tu reserva sigue confirmada."));

        mockMvc.perform(post("/payments/bookings/30/cancel").with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Tu reserva sigue confirmada."));
    }

    @Test
    @WithMockUser(username = TOURIST_EMAIL, roles = "TOURIST")
    void aPolicyThatDoesNotAllowCancellingShowsTheReason() throws Exception {
        when(bookingCancellationService.cancelByTourist(BOOKING_ID, TOURIST_ID))
                .thenThrow(new PaymentException("Esta actividad no admite cancelaciones."));

        mockMvc.perform(post("/payments/bookings/30/cancel").with(csrf()))
                .andExpect(flash().attribute("errorMessage", "Esta actividad no admite cancelaciones."));
    }

    private BookingPaymentDTO payment(List<PaymentTransaction> attempts, boolean payable) {
        return new BookingPaymentDTO(booking, attempts, payable, List.of(), false, null, null);
    }

    private PaymentTransaction transaction(PaymentStatus status) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBooking(booking);
        transaction.setAmount(booking.getTotalAmount());
        transaction.setStatus(status);
        transaction.setAttemptNumber(1);
        transaction.setExternalReference("SIM-1");
        transaction.setCreatedAt(LocalDateTime.now());
        return transaction;
    }
}
