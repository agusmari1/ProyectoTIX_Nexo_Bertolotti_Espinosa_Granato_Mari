package uy.edu.um.nexo.mnatural.payment;

// Lo que devuelve el servicio de pagos al crear un pago
public record PaymentCreation(String externalReference, String paymentUrl) {
}
