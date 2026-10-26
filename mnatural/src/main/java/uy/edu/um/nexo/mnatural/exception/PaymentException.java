package uy.edu.um.nexo.mnatural.exception;

// Errores al iniciar o consultar un pago. El mensaje se le puede mostrar al turista
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
