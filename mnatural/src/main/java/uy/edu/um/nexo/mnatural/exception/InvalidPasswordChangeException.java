package uy.edu.um.nexo.mnatural.exception;

// El cambio de contraseña no se puede hacer (la actual no coincide, o la nueva es igual a la actual).
// "field" es el campo del formulario al que corresponde el error
public class InvalidPasswordChangeException extends RuntimeException {

    private final String field;

    public InvalidPasswordChangeException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
