package uy.edu.um.nexo.mnatural.exception;

public class ProviderNotFoundException extends ProviderReviewException {

    public ProviderNotFoundException(Long providerId) {
        super("No existe un prestador con id " + providerId + ".");
    }
}
