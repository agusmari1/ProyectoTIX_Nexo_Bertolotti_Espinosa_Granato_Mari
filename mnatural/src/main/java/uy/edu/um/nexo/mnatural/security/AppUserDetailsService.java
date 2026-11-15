package uy.edu.um.nexo.mnatural.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.regex.Pattern;

// Spring Security llama a este servicio cuando alguien envía el formulario de login:
// le pasa el email ingresado y espera los datos del usuario (hash de la contraseña,
// rol y estado de la cuenta). La comparación de la contraseña la hace Spring después.
@Service
public class AppUserDetailsService implements UserDetailsService {

    // Largo máximo de un email según el estándar (RFC 5321)
    private static final int MAX_EMAIL_LENGTH = 254;

    // Algo@algo.algo, sin espacios. La validación completa se hace al registrarse
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = normalizeEmail(username);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        // Un prestador solo puede ingresar si está APPROVED. Si está pendiente queda
        // "bloqueado"; con cualquier otro estado (rechazado) queda "deshabilitado".
        // Spring lanza LockedException o DisabledException y SecurityConfig
        // muestra un mensaje distinto para cada caso
        boolean pending = hasStatus(user, ApprovalStatus.PENDING);
        boolean notApproved = user instanceof Provider && !hasStatus(user, ApprovalStatus.APPROVED);

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(roleOf(user).name())
                .accountLocked(pending)
                .disabled(notApproved && !pending)
                .build();
    }

    // Se guarda en minúsculas y sin espacios al registrarse (ProviderService),
    // así que se busca igual. Si el formato no es válido ni se consulta la base
    private String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new UsernameNotFoundException("Email vacío");
        }
        String email = rawEmail.trim().toLowerCase();
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new UsernameNotFoundException("Email inválido");
        }
        return email;
    }

    // El rol sale del tipo de usuario (herencia en las entidades), sin guardar nada extra
    private Role roleOf(User user) {
        if (user instanceof Admin) {
            return Role.ADMIN;
        }
        if (user instanceof Provider) {
            return Role.PROVIDER;
        }
        if (user instanceof Tourist) {
            return Role.TOURIST;
        }
        throw new IllegalStateException("Tipo de usuario desconocido: " + user.getClass().getSimpleName());
    }

    private boolean hasStatus(User user, ApprovalStatus status) {
        return user instanceof Provider provider && provider.getApprovalStatus() == status;
    }
}
