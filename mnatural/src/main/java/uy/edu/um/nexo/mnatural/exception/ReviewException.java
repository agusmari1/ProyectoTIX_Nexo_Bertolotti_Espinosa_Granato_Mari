package uy.edu.um.nexo.mnatural.exception;

// No se puede hacer lo que se pidió con una reseña (la reserva no es del turista, todavía no terminó la actividad,
// ya tiene una reseña, etc.). El mensaje es claro y se le muestra a la persona
public class ReviewException extends RuntimeException {

    public ReviewException(String message) {
        super(message);
    }
}
