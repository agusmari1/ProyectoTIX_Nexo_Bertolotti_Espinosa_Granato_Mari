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
import java.util.UUID;

// Servicio de pagos de mentira, para desarrollo y tests: no se conecta a nada ni cobra nada.
// Es el que se usa si no se configura payment.gateway.
// El resultado se fuerza con payment.simulated.result (APPROVED por defecto):
//   APPROVED, PENDING o REJECTED: es lo que devuelve la consulta del pago;
//   ERROR: simula que el servicio no responde (las dos operaciones fallan)
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final PaymentStatus result;
    private final String baseUrl;

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
}
