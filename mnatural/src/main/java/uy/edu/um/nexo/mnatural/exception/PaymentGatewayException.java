package uy.edu.um.nexo.mnatural.exception;

// El servicio de pagos no respondió, tardó demasiado o devolvió algo que no se entiende.
// El mensaje es técnico (va al log), no se le muestra al turista
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
