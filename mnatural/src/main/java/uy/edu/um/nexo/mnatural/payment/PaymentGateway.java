package uy.edu.um.nexo.mnatural.payment;

import uy.edu.um.nexo.mnatural.entities.Booking;

import java.math.BigDecimal;
import java.util.Optional;

// Conexión con el servicio externo de pagos. PaymentService solo conoce esta interfaz,
// así se puede usar la simulada (tests y desarrollo) o Mercado Pago sin cambiar nada más.
// Se elige con la propiedad payment.gateway (por defecto, la simulada).
// Las operaciones tiran PaymentGatewayException si el servicio falla o no responde
public interface PaymentGateway {

    // Pide al servicio que cree un pago por ese monto (en pesos uruguayos) y devuelve
    // la referencia para consultarlo después y la URL a la que se manda al turista para pagar
    PaymentCreation createPayment(Booking booking, BigDecimal amount);

    // Consulta cómo quedó el pago con esa referencia
    GatewayPaymentStatus getPaymentStatus(String externalReference);

    // Devuelve al turista parte o todo lo cobrado en el pago con esa referencia (en pesos uruguayos).
    // fullRefund en true devuelve el total del cobro; en false, solo amount
    GatewayRefundResult refund(String chargeExternalReference, BigDecimal amount, boolean fullRefund);

    // Para los avisos (webhook) del servicio de pagos: con el id que el servicio le asignó al pago devuelve
    // nuestra referencia, o vacío si ese pago no existe o no es de esta aplicación. El aviso solo trae el id y
    // no se le cree: la referencia sale de consultar el pago. Por defecto no hay avisos (servicio simulado)
    default Optional<String> findExternalReference(String paymentId) {
        return Optional.empty();
    }
}
