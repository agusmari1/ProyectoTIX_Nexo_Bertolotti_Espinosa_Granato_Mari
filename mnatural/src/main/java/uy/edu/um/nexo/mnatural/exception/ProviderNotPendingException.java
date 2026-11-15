package uy.edu.um.nexo.mnatural.exception;

public class ProviderNotPendingException extends ProviderReviewException {

    public ProviderNotPendingException() {
        super("La solicitud ya fue resuelta: el prestador no está pendiente de aprobación.");
    }
}
