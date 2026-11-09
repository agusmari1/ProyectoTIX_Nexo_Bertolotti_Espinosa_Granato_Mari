package uy.edu.um.nexo.mnatural.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Avisos (webhook) de Mercado Pago. MockRestServiceServer hace de Mercado Pago: no se usa internet ni ningún token
// real (el token es un texto inventado)
class MercadoPagoWebhookTest {

    private static final String FAKE_TOKEN = "TEST-token-inventado";

    private MockRestServiceServer server;

    private MercadoPagoPaymentGateway gateway(String baseUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new MercadoPagoPaymentGateway(builder, FAKE_TOKEN, baseUrl);
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

    // ===== notification_url =====

    @Test
    void withAPublicHttpsAddressThePaymentAsksForNotifications() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.notification_url").value("https://mnatural.ngrok-free.app/payments/webhook"))
                .andExpect(jsonPath("$.back_urls.success").value("https://mnatural.ngrok-free.app/payments/return"))
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.uy/pay\"}",
                        MediaType.APPLICATION_JSON));

        gateway.createPayment(booking(), new BigDecimal("3000"));

        server.verify();
    }

    @Test
    void withALocalAddressNoNotificationUrlIsSentBecauseMercadoPagoCouldNotReachIt() {
        MercadoPagoPaymentGateway gateway = gateway("http://mnatural.localtest.me:8080");
        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"pref-1\",\"init_point\":\"https://www.mercadopago.com.uy/pay\"}",
                        MediaType.APPLICATION_JSON));

        gateway.createPayment(booking(), new BigDecimal("3000"));

        server.verify();
    }

    // ===== findExternalReference =====

    @Test
    void theReferenceIsTakenFromQueryingThePaymentById() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123456"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + FAKE_TOKEN))
                .andRespond(withSuccess("{\"id\":123456,\"status\":\"approved\",\"external_reference\":\"MN-46-abc\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.findExternalReference("123456")).contains("MN-46-abc");
        server.verify();
    }

    @Test
    void aPaymentThatDoesNotBelongToThisApplicationGivesNothing() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/1"))
                .andRespond(withSuccess("{\"id\":1,\"external_reference\":\"OTRA-APP-9\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/2"))
                .andRespond(withSuccess("{\"id\":2}", MediaType.APPLICATION_JSON));

        assertThat(gateway.findExternalReference("1")).isEmpty();
        assertThat(gateway.findExternalReference("2")).isEmpty();
    }

    @Test
    void anUnknownPaymentIdIsNotAnError() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/99999"))
                .andRespond(withResourceNotFound());

        assertThat(gateway.findExternalReference("99999")).isEqualTo(Optional.empty());
    }

    @Test
    void ifMercadoPagoFailsTheErrorGoesUpSoTheNotificationIsRetried() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");
        server.expect(requestTo("https://api.mercadopago.com/v1/payments/123"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.findExternalReference("123")).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void anIdThatIsNotANumberIsRejectedWithoutCallingMercadoPago() {
        MercadoPagoPaymentGateway gateway = gateway("https://mnatural.ngrok-free.app");

        // Evita armar una URL con lo que mande cualquiera (por ejemplo, "../merchant_orders/1")
        assertThat(gateway.findExternalReference("../merchant_orders/1")).isEmpty();
        assertThat(gateway.findExternalReference("12 34")).isEmpty();
        assertThat(gateway.findExternalReference("")).isEmpty();
        assertThat(gateway.findExternalReference(null)).isEmpty();
        server.verify(); // sin ningún pedido esperado: si se hubiera llamado, fallaría
    }
}
