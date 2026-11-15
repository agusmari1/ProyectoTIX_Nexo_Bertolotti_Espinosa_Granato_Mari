package uy.edu.um.nexo.mnatural.exception;

public class ActivityTypeNotFoundException extends ActivityTypeException {

    public ActivityTypeNotFoundException(Long typeId) {
        super("No existe un tipo de actividad con id " + typeId + ".");
    }
}
