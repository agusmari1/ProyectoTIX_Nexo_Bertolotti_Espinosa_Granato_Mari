package uy.edu.um.nexo.mnatural.service;

// Se publica cuando un admin crea a otro. Lleva la contraseña generada en texto plano solo para
// mandársela por correo al nuevo admin, así que toString la oculta (por si alguien lo imprime)
public record AdminCreatedEvent(String name, String email, String password) {

    @Override
    public String toString() {
        return "AdminCreatedEvent[email=" + email + ", password=********]";
    }
}
