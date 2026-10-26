package uy.edu.um.nexo.mnatural.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

// Test unitario: MockRestServiceServer hace de Mercado Pago, así no se usa internet ni ninguna credencial real.
// El token es un texto inventado
class MercadoPagoPaymentGatewayTest {

    private static final String FAKE_TOKEN = "TEST-token-inventado";
    private static final String BASE_URL = "http://192.168.1.20:8080";

    private MockRestServiceServer server;
    private MercadoPagoPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new MercadoPagoPaymentGateway(builder, FAKE_TOKEN, BASE_URL);
    }

    private Booking booking() {
        Activity activity = new Activity();
        activity.setName("Cabalgata");
        ActivitySchedule schedule = new ActivitySchedule();
        schedule.setActivity(activity);
        schedule.setStartDateTime(LocalDateTime.now().plusDays(10));
        Booking booking = new Booking();
        booking.setId(46L);
        booking.setActivitySchedule(schedule);
        booking.setParticipantCount(2);
        booking.setHeldUntil(LocalDateTime.now().plusHours(2));
        return booking;
    }

    private void respondToSearch(String results) {
        server.expect(requestTo(startsWith("https://api.mercadopago.com/v1/payments/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("external_reference", "MN-46-abc"))
                .andExpect(header("Authorization", "Bearer " + FAKE_TOKEN))
                .andRespond(withSuccess("{\"paging\":{\"total\":1},\"results\":" + results + "}",
                        MediaType.APPLICATION_JSON));
    }

    // ===== createPayment =====

    @Test
    void createPaymentCreatesAPreferenceInPesosAndReturnsItsInitPoint() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FAKE_TOKEN))
                .andExpect(jsonPath("$.items[0].currency_id").value("UYU"))
                .andExpect(jsonPath("$.items[0].unit_price").value(3000.0))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.external_reference").value(startsWith("MN-46-")))
                .andExpect(jsonPath("$.back_urls.success").value(BASE_URL + "/payments/return"))
                .andExpect(jsonPath("$.back_urls.failure").value(BASE_URL + "/payments/return"))
                // La compu local no es https: Mercado Pago rechazaría auto_return
                .andExpect(jsonPath("$.auto_return").doesNotExist())
                .andExpect(jsonPath("$.expires").value(true))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.uy/checkout/v1/redirect?pref_id=pref-1\"}",
                        MediaType.APPLICATION_JSON));

        PaymentCreation creation = gateway.createPayment(booking(), new BigDecimal("3000.00"));

        assertThat(creation.paymentUrl()).isEqualTo("https://www.mercadopago.com.uy/checkout/v1/redirect?pref_id=pref-1");
        assertThat(creation.externalReference()).startsWith("MN-46-");
        server.verify();
    }

    @Test
    void withAnHttpsAddressTheReturnIsAutomatic() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer httpsServer = MockRestServiceServer.bindTo(builder).build();
        MercadoPagoPaymentGateway httpsGateway = new MercadoPagoPaymentGateway(builder, FAKE_TOKEN,
                "https://mnatural.ejemplo.test");
        httpsServer.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andExpect(jsonPath("$.back_urls.success").value("https://mnatural.ejemplo.test/payments/return"))
                .andRespond(withSuccess("{\"init_point\":\"https://www.mercadopago.com.uy/checkout/v1/redirect?pref_id=pref-2\"}",
                        MediaType.APPLICATION_JSON));

        httpsGateway.createPayment(booking(), new BigDecimal("3000.00"));

        httpsServer.verify();
    }

    @Test
    void ifMercadoPagoFailsCreatingThePaymentItIsAGatewayError() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createPayment(booking(), new BigDecimal("3000.00")))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageNotContaining(FAKE_TOKEN);
    }

    @Test
    void theErrorMessageIncludesWhatMercadoPagoExplainedButNotTheToken() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"campo inválido de prueba\",\"status\":400}"));

        assertThatThrownBy(() -> gateway.createPayment(booking(), new BigDecimal("3000.00")))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("campo inválido de prueba")
                .hasMessageNotContaining(FAKE_TOKEN);
    }

    @Test
    void aResponseWithoutInitPointIsAGatewayError() {
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"pref-1\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createPayment(booking(), new BigDecimal("3000.00")))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void withoutAccessTokenItDoesNotStart() {
        assertThatThrownBy(() -> new MercadoPagoPaymentGateway(RestClient.builder(), "", BASE_URL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MP_ACCESS_TOKEN");
    }

    // ===== getPaymentStatus =====

    @Test
    void anApprovedPaymentBringsCardAmountAndCurrency() {
        respondToSearch("[{\"id\":1,\"status\":\"approved\",\"status_detail\":\"accredited\","
                + "\"payment_method_id\":\"master\",\"card\":{\"last_four_digits\":\"0604\"},"
                + "\"transaction_amount\":3000,\"currency_id\":\"UYU\",\"external_reference\":\"MN-46-abc\"}]");

        GatewayPaymentStatus status = gateway.getPaymentStatus("MN-46-abc");

        assertThat(status.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(status.cardBrand()).isEqualTo("master");
        assertThat(status.cardLastFour()).isEqualTo("0604");
        assertThat(status.amount()).isEqualByComparingTo("3000.00");
        assertThat(status.currency()).isEqualTo("UYU");
        assertThat(status.failureReason()).isNull();
    }

    @Test
    void ifAnyAttemptIsApprovedThePaymentIsApproved() {
        // Vienen del más nuevo al más viejo: un rechazo después de un aprobado no anula el aprobado
        respondToSearch("[{\"status\":\"rejected\",\"status_detail\":\"cc_rejected_insufficient_amount\"},"
                + "{\"status\":\"approved\",\"transaction_amount\":3000,\"currency_id\":\"UYU\"}]");

        assertThat(gateway.getPaymentStatus("MN-46-abc").status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void withoutPaymentsYetItIsStillPending() {
        respondToSearch("[]");

        GatewayPaymentStatus status = gateway.getPaymentStatus("MN-46-abc");

        assertThat(status.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(status.failureReason()).isNotBlank();
    }

    @Test
    void ifMercadoPagoFailsQueryingItIsAGatewayError() {
        server.expect(requestTo(startsWith("https://api.mercadopago.com/v1/payments/search")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.getPaymentStatus("MN-46-abc")).isInstanceOf(PaymentGatewayException.class);
    }

    // ===== traducción de estados =====

    @Test
    void theStatusesOfMercadoPagoAreTranslatedToOurs() {
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "in_process")).status())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "pending")).status())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "cancelled")).status())
                .isEqualTo(PaymentStatus.REJECTED);
        // Inesperados para un cobro recién hecho: no se confirma nada
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "refunded")).status())
                .isEqualTo(PaymentStatus.ERROR);
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "charged_back")).status())
                .isEqualTo(PaymentStatus.ERROR);
        assertThat(MercadoPagoPaymentGateway.toStatus(Map.of("status", "otro_estado")).status())
                .isEqualTo(PaymentStatus.ERROR);
    }

    @Test
    void aRejectionExplainsTheReason() {
        GatewayPaymentStatus status = MercadoPagoPaymentGateway.toStatus(
                Map.of("status", "rejected", "status_detail", "cc_rejected_insufficient_amount"));

        assertThat(status.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(status.failureReason()).contains("fondos");
    }
}
