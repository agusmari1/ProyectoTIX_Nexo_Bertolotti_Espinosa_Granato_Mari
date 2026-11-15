package uy.edu.um.nexo.mnatural.payment;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Cobro con el formulario de tarjeta de Mercado Pago. MockRestServiceServer hace de Mercado Pago: no se usa internet ni
// ninguna credencial real (token y clave pública son textos inventados)
class MercadoPagoCardPaymentTest {

    private static final String FAKE_TOKEN = "TEST-token-inventado";
    private static final String FAKE_PUBLIC_KEY = "TEST-public-key-inventada";
    private static final String REFERENCE = "MN-46-abc";

    private MockRestServiceServer server;

    private MercadoPagoPaymentGateway gateway(String baseUrl, String publicKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new MercadoPagoPaymentGateway(builder, FAKE_TOKEN, baseUrl, publicKey);
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
        return booking;
    }

    private static CardPaymentRequest request() {
        return new CardPaymentRequest("tok_123", "master", "24", 1, "ana@ejemplo.com", "CI", "12345678",
                null, null, null, null);
    }

    private static String approved() {
        return "{\"id\":999,\"status\":\"approved\",\"status_detail\":\"accredited\",\"payment_method_id\":\"master\","
                + "\"transaction_amount\":3000.0,\"currency_id\":\"UYU\",\"card\":{\"last_four_digits\":\"0604\"}}";
    }

    // ===== Modo de pago =====

    @Test
    void withAPublicKeyThePaymentUsesTheCardFormAndWithoutItTheMercadoPagoPage() {
        MercadoPagoPaymentGateway withKey = gateway("http://mnatural.localtest.me:8080", FAKE_PUBLIC_KEY);
        MercadoPagoPaymentGateway withoutKey = gateway("http://mnatural.localtest.me:8080", "  ");
        MercadoPagoPaymentGateway nullKey = gateway("http://mnatural.localtest.me:8080", null);

        assertThat(withKey.checkoutMode()).isEqualTo(CheckoutMode.CARD_FORM);
        assertThat(withKey.publicKey()).isEqualTo(FAKE_PUBLIC_KEY);
        assertThat(withoutKey.checkoutMode()).isEqualTo(CheckoutMode.REDIRECT);
        assertThat(withoutKey.publicKey()).isNull();
        assertThat(nullKey.checkoutMode()).isEqualTo(CheckoutMode.REDIRECT);
    }

    // ===== payWithCard =====

    @Test
    void itChargesWithTheTokenAndNeverSendsACardNumber() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", FAKE_PUBLIC_KEY);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FAKE_TOKEN))
                // Mercado Pago no cobra dos veces el mismo pedido si se reintenta
                .andExpect(header("X-Idempotency-Key", REFERENCE))
                .andExpect(jsonPath("$.token").value("tok_123"))
                .andExpect(jsonPath("$.transaction_amount").value(3000.0))
                .andExpect(jsonPath("$.payment_method_id").value("master"))
                .andExpect(jsonPath("$.issuer_id").value("24"))
                .andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.external_reference").value(REFERENCE))
                .andExpect(jsonPath("$.payer.email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$.payer.identification.type").value("CI"))
                .andExpect(jsonPath("$.payer.identification.number").value("12345678"))
                .andExpect(jsonPath("$.notification_url").value("https://mnatural.ngrok-free.app/payments/webhook"))
                .andExpect(jsonPath("$.card_number").doesNotExist())
                .andExpect(jsonPath("$.security_code").doesNotExist())
                .andRespond(withSuccess(approved(), MediaType.APPLICATION_JSON));

        GatewayPaymentStatus result = gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000.00"), request());

        server.verify();
        assertThat(result.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(result.cardBrand()).isEqualTo("master");
        assertThat(result.cardLastFour()).isEqualTo("0604");
        assertThat(result.amount()).isEqualByComparingTo("3000");
        assertThat(result.currency()).isEqualTo("UYU");
    }

    @Test
    void withALocalAddressNoNotificationUrlIsSent() {
        MercadoPagoPaymentGateway gateway = gateway("http://mnatural.localtest.me:8080", FAKE_PUBLIC_KEY);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(withSuccess(approved(), MediaType.APPLICATION_JSON));

        gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000.00"), request());

        server.verify();
    }

    @Test
    void ifTheFormDidNotAskForADocumentItIsLeftOutAndTheDefaultInstallmentsApply() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", FAKE_PUBLIC_KEY);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andExpect(jsonPath("$.payer.identification").doesNotExist())
                .andExpect(jsonPath("$.installments").value(1))
                .andExpect(jsonPath("$.issuer_id").doesNotExist())
                .andRespond(withSuccess(approved(), MediaType.APPLICATION_JSON));

        gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000.00"),
                new CardPaymentRequest("tok", "visa", null, null, "a@b.com", null, null, null, null, null, null));

        server.verify();
    }

    @Test
    void theStatusesOfMercadoPagoAreTranslatedToOurs() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", FAKE_PUBLIC_KEY);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andRespond(withSuccess("{\"status\":\"rejected\",\"status_detail\":\"cc_rejected_insufficient_amount\","
                        + "\"payment_method_id\":\"visa\",\"transaction_amount\":3000,\"currency_id\":\"UYU\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"), request()).status())
                .isEqualTo(PaymentStatus.REJECTED);

        server.reset();
        server.expect(requestTo("https://api.mercadopago.com/v1/payments"))
                .andRespond(withSuccess("{\"status\":\"in_process\",\"transaction_amount\":3000,\"currency_id\":\"UYU\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"), request()).status())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void anErrorFromMercadoPagoIsAGatewayFailureNotAnApproval() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", FAKE_PUBLIC_KEY);
        server.expect(requestTo("https://api.mercadopago.com/v1/payments")).andRespond(withBadRequest());
        assertThatThrownBy(() -> gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"), request()))
                .isInstanceOf(PaymentGatewayException.class);

        server.reset();
        server.expect(requestTo("https://api.mercadopago.com/v1/payments")).andRespond(withServerError());
        assertThatThrownBy(() -> gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"), request()))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void withoutATokenFromTheFormNothingIsSentToMercadoPago() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", FAKE_PUBLIC_KEY);

        assertThatThrownBy(() -> gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"),
                new CardPaymentRequest(null, "visa", null, 1, "a@b.com", null, null, null, null, null, null)))
                .isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(() -> gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"),
                new CardPaymentRequest("tok", null, null, 1, "a@b.com", null, null, null, null, null, null)))
                .isInstanceOf(PaymentGatewayException.class);
        server.verify(); // sin ningún pedido esperado: si se hubiera llamado, fallaría
    }

    @Test
    void withoutAPublicKeyTheCardFormCannotCharge() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app", "");

        assertThatThrownBy(() -> gateway.payWithCard(booking(), REFERENCE, new BigDecimal("3000"), request()))
                .isInstanceOf(PaymentGatewayException.class).hasMessageContaining("MP_PUBLIC_KEY");
    }
}
