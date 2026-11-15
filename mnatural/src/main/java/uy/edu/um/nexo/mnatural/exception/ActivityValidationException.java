package uy.edu.um.nexo.mnatural.exception;

// Un dato de la actividad o del horario no cumple una regla de negocio.
// field es el campo del formulario donde mostrar el error (null si es un error general)
public class ActivityValidationException extends RuntimeException {

    private final String field;

    public ActivityValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
