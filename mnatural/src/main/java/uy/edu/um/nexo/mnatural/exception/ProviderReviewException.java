package uy.edu.um.nexo.mnatural.exception;

// Base de los errores al aprobar o rechazar un prestador
public class ProviderReviewException extends RuntimeException {

    public ProviderReviewException(String message) {
        super(message);
    }
}
