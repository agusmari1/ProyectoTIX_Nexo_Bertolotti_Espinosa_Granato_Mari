package uy.edu.um.nexo.mnatural.service;

// Se publica cuando se le genera una contraseña nueva a un admin. Lleva la contraseña en texto plano
// solo para mandársela por correo, así que toString la oculta (por si alguien lo imprime)
public record AdminPasswordResetEvent(String name, String email, String password) {

    @Override
    public String toString() {
        return "AdminPasswordResetEvent[email=" + email + ", password=********]";
    }
}
