package uy.edu.um.nexo.mnatural.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.exception.PaymentGatewayException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Servicio de pagos de mentira, para desarrollo y tests: no se conecta a nada ni cobra nada.
// Es el que se usa si no se configura payment.gateway.
// El resultado se fuerza con payment.simulated.result (APPROVED por defecto):
//   APPROVED, PENDING o REJECTED: es lo que devuelve la consulta del pago;
//   ERROR: simula que el servicio no responde (todas las operaciones fallan, también el reembolso).
// Con el formulario de tarjeta propio (checkoutMode CARD_FORM) el resultado lo elige el NOMBRE DEL TITULAR, igual que
// en el sandbox de Mercado Pago: APRO aprueba, OTHE rechaza, FUND rechaza por fondos insuficientes y CONT deja el pago
// pendiente (cualquier otro nombre aprueba). Es para tarjetas de prueba: no se guarda el número ni el código de
// seguridad, solo la marca y los últimos 4 dígitos
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final PaymentStatus result;
    private final String baseUrl;
    // Cómo quedó cada pago hecho con el formulario de tarjeta, para que consultarlo después dé lo mismo
    private final Map<String, GatewayPaymentStatus> cardPayments = new ConcurrentHashMap<>();

    public SimulatedPaymentGateway(@Value("${payment.simulated.result:APPROVED}") PaymentStatus result,
                                   @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.result = result;
        this.baseUrl = baseUrl;
    }

    @Override
    public PaymentCreation createPayment(Booking booking, BigDecimal amount) {
        if (result == PaymentStatus.ERROR) {
            throw new PaymentGatewayException("Servicio de pagos simulado no disponible (payment.simulated.result=ERROR)");
        }
        String reference = "SIM-" + UUID.randomUUID();
        log.info("Pago simulado creado: reserva {}, {} UYU, referencia {}, resultado forzado {}",
                booking.getId(), amount, reference, result);
        // No hay página de pago: se vuelve directo a la ruta de retorno, como si el turista ya hubiera pagado
        return new PaymentCreation(reference, baseUrl + "/payments/return?external_reference=" + reference);
    }

    @Override
    public GatewayPaymentStatus getPaymentStatus(String externalReference) {
        GatewayPaymentStatus remembered = cardPayments.get(externalReference);
        if (remembered != null) {
            return remembered;
        }
        return switch (result) {
            // El simulado no informa monto ni moneda (null): no hay nada real que comparar
            case APPROVED -> new GatewayPaymentStatus(PaymentStatus.APPROVED, "visa", "4242", null, null, null);
            case PENDING -> new GatewayPaymentStatus(PaymentStatus.PENDING, null, null,
                    "El pago está en proceso.", null, null);
            case REJECTED -> new GatewayPaymentStatus(PaymentStatus.REJECTED, "visa", "4242",
                    "La tarjeta fue rechazada (simulado).", null, null);
            case ERROR -> throw new PaymentGatewayException(
                    "Servicio de pagos simulado no disponible (payment.simulated.result=ERROR)");
        };
    }

    @Override
    public CheckoutMode checkoutMode() {
        return CheckoutMode.CARD_FORM;
    }

    @Override
    public GatewayPaymentStatus payWithCard(Booking booking, String externalReference, BigDecimal amount,
                                            CardPaymentRequest request) {
        if (result == PaymentStatus.ERROR) {
            throw new PaymentGatewayException("Servicio de pagos simulado no disponible (payment.simulated.result=ERROR)");
        }
        String number = request.cardNumber() == null ? "" : request.cardNumber().replaceAll("[\\s-]", "");
        GatewayPaymentStatus payment;
        if (!number.matches("\\d{13,19}") || !validExpiry(request.expiry())
                || request.securityCode() == null || !request.securityCode().matches("\\d{3,4}")) {
            payment = new GatewayPaymentStatus(PaymentStatus.REJECTED, null, null,
                    "Revisá los datos de la tarjeta: número, vencimiento y código de seguridad.", amount, "UYU");
        } else {
            String brand = number.startsWith("4") ? "visa" : (number.startsWith("5") || number.startsWith("2"))
                    ? "master" : "tarjeta";
            String lastFour = number.substring(number.length() - 4);
            String holder = request.holderName() == null ? "" : request.holderName().trim().toUpperCase();
            payment = switch (holder) {
                case "OTHE" -> new GatewayPaymentStatus(PaymentStatus.REJECTED, brand, lastFour,
                        "La tarjeta fue rechazada (simulado).", amount, "UYU");
                case "FUND" -> new GatewayPaymentStatus(PaymentStatus.REJECTED, brand, lastFour,
                        "La tarjeta no tiene fondos suficientes (simulado).", amount, "UYU");
                case "CONT" -> new GatewayPaymentStatus(PaymentStatus.PENDING, brand, lastFour,
                        "El pago está en proceso.", amount, "UYU");
                default -> new GatewayPaymentStatus(PaymentStatus.APPROVED, brand, lastFour, null, amount, "UYU");
            };
        }
        cardPayments.put(externalReference, payment);
        // Nunca se escribe el número ni el código de seguridad
        log.info("Pago simulado con tarjeta: reserva {}, {} UYU, referencia {}, resultado {}",
                booking.getId(), amount, externalReference, payment.status());
        return payment;
    }

    // "MM/AA" o "MM/AAAA", todavía vigente (una tarjeta vence el último día del mes)
    static boolean validExpiry(String expiry) {
        if (expiry == null || !expiry.trim().matches("\\d{1,2}\\s*/\\s*(\\d{2}|\\d{4})")) {
            return false;
        }
        String[] parts = expiry.trim().split("\\s*/\\s*");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]);
        if (year < 100) {
            year += 2000;
        }
        if (month < 1 || month > 12) {
            return false;
        }
        LocalDate lastDay = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
        return !lastDay.isBefore(LocalDate.now());
    }

    // Con ERROR el servicio "no responde"; con cualquier otro resultado el reembolso se aprueba
    @Override
    public GatewayRefundResult refund(String chargeExternalReference, BigDecimal amount, boolean fullRefund) {
        if (result == PaymentStatus.ERROR) {
            throw new PaymentGatewayException("Servicio de pagos simulado no disponible (payment.simulated.result=ERROR)");
        }
        String refundId = "SIM-REF-" + UUID.randomUUID();
        log.info("Reembolso simulado del pago {}: {} UYU ({})", chargeExternalReference, amount,
                fullRefund ? "total" : "parcial");
        return new GatewayRefundResult(PaymentStatus.APPROVED, refundId, null);
    }
}
