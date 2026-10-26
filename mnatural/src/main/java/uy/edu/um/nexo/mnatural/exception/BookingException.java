package uy.edu.um.nexo.mnatural.exception;

// Base de los errores al crear una reserva. El mensaje se le puede mostrar al turista
public class BookingException extends RuntimeException {

    public BookingException(String message) {
        super(message);
    }
}
