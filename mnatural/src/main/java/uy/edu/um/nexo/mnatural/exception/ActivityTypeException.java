package uy.edu.um.nexo.mnatural.exception;

// Error al administrar los tipos de actividad (nombre repetido, tipo en uso, etc.).
// El mensaje está pensado para mostrárselo tal cual al administrador
public class ActivityTypeException extends RuntimeException {

    public ActivityTypeException(String message) {
        super(message);
    }
}
