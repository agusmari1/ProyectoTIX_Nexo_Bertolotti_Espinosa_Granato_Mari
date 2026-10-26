package uy.edu.um.nexo.mnatural.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// Mercado Pago Checkout Pro (documentación de Uruguay, www.mercadopago.com.uy/developers):
//   createPayment:     POST /checkout/preferences  -> devuelve init_point, la página donde paga el turista
//   getPaymentStatus:  GET  /v1/payments/search?external_reference=...
// Se activa con payment.gateway=mercadopago (variable de entorno PAYMENT_GATEWAY=mercadopago).
// El Access Token se lee de la variable de entorno MP_ACCESS_TOKEN: nunca va en el código ni en el repo,
// y nunca se escribe en el log.
// No usa webhooks (no llegan a una compu local): el resultado se consulta cuando el turista vuelve
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mercadopago")
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentGateway.class);

    private static final String API_URL = "https://api.mercadopago.com";
    private static final String CURRENCY = "UYU";
    // Si Mercado Pago tarda más que esto, se corta y se trata como servicio caído
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter MP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestClient restClient;
    private final String returnUrl;
    private final boolean autoReturn;

    // @Autowired: hay dos constructores y este es el que usa Spring (el otro es para los tests)
    @Autowired
    public MercadoPagoPaymentGateway(@Value("${MP_ACCESS_TOKEN:}") String accessToken,
                                     @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this(RestClient.builder().requestFactory(requestFactory()), accessToken, baseUrl);
    }

    // Para los tests: permite conectar un servidor de mentira al builder
    MercadoPagoPaymentGateway(RestClient.Builder builder, String accessToken, String baseUrl) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("payment.gateway=mercadopago pero falta la variable de entorno "
                    + "MP_ACCESS_TOKEN. Configurala o sacá PAYMENT_GATEWAY para usar el servicio simulado.");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (base.contains("localhost") || base.contains("127.0.0.1")) {
            // Mercado Pago no acepta dominios locales en las URLs de retorno (ver "Configurar URLs de retorno")
            log.warn("APP_BASE_URL es {}: Mercado Pago no acepta localhost en las URLs de retorno. "
                    + "Usá un nombre como http://mnatural.localtest.me:8080", base);
        }
        this.returnUrl = base + "/payments/return";
        // Con una dirección que no es https (la compu local), Mercado Pago rechaza auto_return:
        // "auto_return invalid. back_url.success must be defined" (probado con http://mnatural.localtest.me:8080).
        // En ese caso no se manda: al terminar de pagar el turista vuelve con el botón "Volver al sitio".
        // Con la app publicada en https se manda y la vuelta es automática
        this.autoReturn = base.startsWith("https://");
        if (!autoReturn) {
            log.info("APP_BASE_URL no es https: los pagos se crean sin auto_return, el turista vuelve con "
                    + "\"Volver al sitio\"");
        }
        this.restClient = builder
                .baseUrl(API_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Override
    public PaymentCreation createPayment(Booking booking, BigDecimal amount) {
        // Referencia propia: con ella se busca el pago al volver. Lleva el id de la reserva para reconocerla
        String reference = "MN-" + booking.getId() + "-" + UUID.randomUUID();

        // Un solo ítem por el total: es un único pago por la reserva (ESPECIFICACION.md, sección 3)
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "booking-" + booking.getId());
        item.put("title", "Reserva: " + booking.getActivitySchedule().getActivity().getName()
                + " (" + booking.getParticipantCount() + " participantes)");
        item.put("quantity", 1);
        item.put("unit_price", amount);
        item.put("currency_id", CURRENCY);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", reference);
        // Para los tres resultados se vuelve a la misma ruta, que consulta el estado real
        body.put("back_urls", Map.of("success", returnUrl, "pending", returnUrl, "failure", returnUrl));
        if (autoReturn) {
            body.put("auto_return", "approved");
        }
        // El link de pago deja de servir cuando vence la retención de la reserva
        if (booking.getHeldUntil() != null) {
            body.put("expires", true);
            body.put("expiration_date_to", booking.getHeldUntil().atZone(ZoneId.systemDefault()).format(MP_DATE_FORMAT));
        }

        Map<?, ?> response = call("crear el pago", () -> restClient.post()
                .uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class));
        String paymentUrl = text(response.get("init_point"));
        if (paymentUrl == null) {
            throw new PaymentGatewayException("Mercado Pago no devolvió init_point al crear el pago");
        }
        log.info("Pago de Mercado Pago creado: reserva {}, {} {}, referencia {}",
                booking.getId(), amount, CURRENCY, reference);
        return new PaymentCreation(reference, paymentUrl);
    }

    @Override
    public GatewayPaymentStatus getPaymentStatus(String externalReference) {
        Map<?, ?> response = call("consultar el pago", () -> restClient.get()
                .uri(uri -> uri.path("/v1/payments/search")
                        .queryParam("external_reference", externalReference)
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .queryParam("range", "date_created")
                        .queryParam("begin_date", "NOW-30DAYS")
                        .queryParam("end_date", "NOW")
                        .build())
                .retrieve()
                .body(Map.class));
        if (response == null || !(response.get("results") instanceof List<?> results)) {
            throw new PaymentGatewayException("Respuesta inesperada de Mercado Pago al consultar el pago");
        }
        // Sin resultados: el turista todavía no pagó (por ejemplo, cerró la página de pago)
        if (results.isEmpty()) {
            return new GatewayPaymentStatus(PaymentStatus.PENDING, null, null,
                    "Todavía no se registró ningún pago.", null, null);
        }
        // Con varios intentos dentro de la misma página de pago, alcanza con que uno esté aprobado.
        // Si no, vale el más reciente (vienen ordenados del más nuevo al más viejo)
        Map<?, ?> chosen = null;
        for (Object result : results) {
            if (result instanceof Map<?, ?> payment) {
                if (chosen == null) {
                    chosen = payment;
                }
                if ("approved".equals(payment.get("status"))) {
                    chosen = payment;
                    break;
                }
            }
        }
        if (chosen == null) {
            throw new PaymentGatewayException("Respuesta inesperada de Mercado Pago al consultar el pago");
        }
        return toStatus(chosen);
    }

    // Traduce un pago de Mercado Pago a nuestros estados. Valores de status según la documentación
    // ("Resultados de la consulta"). Cualquier otro (refunded, charged_back, in_mediation o uno desconocido)
    // es inesperado para un cobro recién hecho: queda ERROR y la reserva no se confirma
    static GatewayPaymentStatus toStatus(Map<?, ?> payment) {
        String status = text(payment.get("status"));
        String detail = text(payment.get("status_detail"));
        String brand = text(payment.get("payment_method_id"));
        String lastFour = payment.get("card") instanceof Map<?, ?> card ? text(card.get("last_four_digits")) : null;
        BigDecimal amount = payment.get("transaction_amount") == null ? null
                : new BigDecimal(String.valueOf(payment.get("transaction_amount")));
        String currency = text(payment.get("currency_id"));

        PaymentStatus mapped;
        String reason;
        switch (status == null ? "" : status) {
            case "approved" -> {
                mapped = PaymentStatus.APPROVED;
                reason = null;
            }
            case "pending", "in_process", "authorized" -> {
                mapped = PaymentStatus.PENDING;
                reason = "El pago está en proceso.";
            }
            case "rejected" -> {
                mapped = PaymentStatus.REJECTED;
                reason = rejectionReason(detail);
            }
            case "cancelled" -> {
                mapped = PaymentStatus.REJECTED;
                reason = "El pago se canceló.";
            }
            default -> {
                mapped = PaymentStatus.ERROR;
                reason = "Mercado Pago informó un estado inesperado (" + status + "). Contactanos para revisarlo.";
            }
        }
        return new GatewayPaymentStatus(mapped, brand, lastFour, reason, amount, currency);
    }

    private static String rejectionReason(String detail) {
        if (detail == null) {
            return "El pago fue rechazado.";
        }
        return switch (detail) {
            case "cc_rejected_insufficient_amount" -> "La tarjeta no tiene fondos suficientes.";
            case "cc_rejected_bad_filled_security_code" -> "El código de seguridad de la tarjeta es incorrecto.";
            case "cc_rejected_bad_filled_card_number" -> "El número de tarjeta es incorrecto.";
            case "cc_rejected_duplicated_payment" -> "Ya hiciste un pago por el mismo monto. Si necesitás pagar de nuevo, usá otra tarjeta.";
            case "rejected_by_bank" -> "El banco rechazó el pago.";
            case "rejected_high_risk" -> "El pago fue rechazado por seguridad. Probá con otro medio de pago.";
            default -> "El pago fue rechazado.";
        };
    }

    // Cualquier falla de conexión, timeout, respuesta con error o respuesta que no se entiende
    // se convierte en PaymentGatewayException. El mensaje no incluye el token
    private <T> T call(String action, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            // La respuesta de Mercado Pago explica el error (por ejemplo, qué campo es inválido).
            // No incluye el token, que solo viaja en el encabezado de nuestro pedido
            String body = e.getResponseBodyAsString();
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            throw new PaymentGatewayException("Mercado Pago respondió " + e.getStatusCode().value()
                    + " al " + action + ": " + body, e);
        } catch (RuntimeException e) {
            throw new PaymentGatewayException("No se pudo " + action + " en Mercado Pago: " + e.getMessage(), e);
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
