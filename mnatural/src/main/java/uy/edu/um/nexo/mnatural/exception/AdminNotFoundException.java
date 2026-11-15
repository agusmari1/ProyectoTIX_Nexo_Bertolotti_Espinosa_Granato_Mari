package uy.edu.um.nexo.mnatural.exception;

public class AdminNotFoundException extends RuntimeException {

    public AdminNotFoundException(Long adminId) {
        super("No existe un administrador con id " + adminId + ".");
    }
}
