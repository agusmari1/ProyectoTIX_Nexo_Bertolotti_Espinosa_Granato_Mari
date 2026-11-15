package uy.edu.um.nexo.mnatural.payment;

import uy.edu.um.nexo.mnatural.entities.PaymentStatus;

// Resultado de pedir un reembolso. refundId es el identificador que le da el servicio de pagos
// (null si no lo informa); failureReason solo cuando el reembolso no se hizo.
// APPROVED: se devolvió el dinero. PENDING: el servicio lo aceptó y lo está procesando.
// REJECTED: el servicio no lo hizo
public record GatewayRefundResult(PaymentStatus status, String refundId, String failureReason) {
}
