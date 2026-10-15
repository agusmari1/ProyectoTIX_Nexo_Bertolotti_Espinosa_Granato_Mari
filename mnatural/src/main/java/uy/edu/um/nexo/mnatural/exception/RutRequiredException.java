package uy.edu.um.nexo.mnatural.exception;

public class RutRequiredException extends RegistrationException {

    public RutRequiredException() {
        super("El RUT es obligatorio para emprendimientos de tipo empresa.");
    }
}
