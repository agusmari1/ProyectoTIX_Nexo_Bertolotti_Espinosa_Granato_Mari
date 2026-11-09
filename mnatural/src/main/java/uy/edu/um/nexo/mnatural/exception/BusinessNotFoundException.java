package uy.edu.um.nexo.mnatural.exception;

// El emprendimiento no existe o es de otro prestador. Se usa el mismo mensaje en los dos casos para
// no revelar que existe un emprendimiento ajeno
public class BusinessNotFoundException extends RuntimeException {

    public BusinessNotFoundException() {
        super("Ese emprendimiento no existe o no es tuyo.");
    }
}
