package uy.edu.um.nexo.mnatural.security;

// Roles de la plataforma. Spring Security les agrega el prefijo "ROLE_"
// (por ejemplo ROLE_ADMIN), que es lo que chequea hasRole("ADMIN")
public enum Role {
    ADMIN, PROVIDER, TOURIST
}
