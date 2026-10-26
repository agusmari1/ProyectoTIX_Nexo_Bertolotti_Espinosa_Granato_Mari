package uy.edu.um.nexo.mnatural.payment;

import uy.edu.um.nexo.mnatural.entities.Booking;

import java.math.BigDecimal;

// Conexión con el servicio externo de pagos. PaymentService solo conoce esta interfaz,
// así se puede usar la simulada (tests y desarrollo) o Mercado Pago sin cambiar nada más.
// Se elige con la propiedad payment.gateway (por defecto, la simulada).
// Las dos operaciones tiran PaymentGatewayException si el servicio falla o no responde
public interface PaymentGateway {

    // Pide al servicio que cree un pago por ese monto (en pesos uruguayos) y devuelve
    // la referencia para consultarlo después y la URL a la que se manda al turista para pagar
    PaymentCreation createPayment(Booking booking, BigDecimal amount);

    // Consulta cómo quedó el pago con esa referencia
    GatewayPaymentStatus getPaymentStatus(String externalReference);
}
