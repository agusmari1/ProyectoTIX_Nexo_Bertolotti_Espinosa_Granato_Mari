package uy.edu.um.nexo.mnatural.exception;

// Una acción sobre reseñas que no se puede hacer. El mensaje se muestra tal cual en pantalla
public class ReviewException extends RuntimeException {

    public ReviewException(String message) {
        super(message);
    }
}
