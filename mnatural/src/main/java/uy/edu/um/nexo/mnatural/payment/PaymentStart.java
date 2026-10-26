package uy.edu.um.nexo.mnatural.payment;

import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;

// Resultado de PaymentService.startPayment. paymentUrl es null si el servicio de pagos falló:
// en ese caso la transacción quedó en ERROR y el turista puede reintentar
public record PaymentStart(PaymentTransaction transaction, String paymentUrl) {

    public boolean started() {
        return paymentUrl != null;
    }
}
