package uy.edu.um.nexo.mnatural.exception;

public class EmailAlreadyRegisteredException extends RegistrationException {

    public EmailAlreadyRegisteredException(String email) {
        super("Ya existe una cuenta registrada con el correo " + email + ".");
    }
}
