package uy.edu.um.nexo.mnatural.entities;

// PENDING: todavía no hay resultado (el turista está pagando o el pago está en proceso).
// APPROVED y REJECTED los informa el servicio de pagos.
// ERROR: el servicio de pagos falló o respondió algo inesperado, no se cobró nada
public enum PaymentStatus {
    PENDING, APPROVED, REJECTED, ERROR
}
