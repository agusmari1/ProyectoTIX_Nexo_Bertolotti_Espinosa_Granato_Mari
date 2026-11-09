package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.security.Role;

// Lo que necesita el encabezado sobre el usuario con la sesión iniciada: su nombre, la inicial
// para el ícono redondo y su rol, que decide qué opciones aparecen en el menú
public record CurrentUserDTO(String displayName, String initial, Role role) {

    public boolean isTourist() {
        return role == Role.TOURIST;
    }

    public boolean isProvider() {
        return role == Role.PROVIDER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
