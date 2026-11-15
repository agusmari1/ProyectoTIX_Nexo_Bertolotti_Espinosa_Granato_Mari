package uy.edu.um.nexo.mnatural.payment;

import uy.edu.um.nexo.mnatural.entities.PaymentStatus;

import java.math.BigDecimal;

// Resultado de consultar un pago. cardBrand y cardLastFour vienen solo si el servicio los informa,
// failureReason solo cuando el pago no se aprobó.
// amount y currency son lo que el servicio dice que cobró (null si no lo informa): PaymentService
// los compara con el monto de la reserva antes de dar el pago por aprobado
public record GatewayPaymentStatus(PaymentStatus status, String cardBrand, String cardLastFour,
                                   String failureReason, BigDecimal amount, String currency) {
}
