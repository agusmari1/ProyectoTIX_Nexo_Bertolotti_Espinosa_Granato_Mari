package uy.edu.um.nexo.mnatural.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

// Dado el correo del usuario con sesión iniciada, devuelve el prestador SOLO si está aprobado.
// Lo usan los servicios del espacio del prestador (emprendimientos, reservas, verificación de
// códigos): así la regla "solo un prestador aprobado" está en un único lugar. El login ya no deja
// entrar a los pendientes ni a los rechazados, pero se controla igual por si cambió de estado
@Component
public class ApprovedProviderResolver {

    private final UserRepository userRepository;

    public ApprovedProviderResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Provider require(String email) {
        if (userRepository.findByEmail(email).orElse(null) instanceof Provider provider
                && provider.getApprovalStatus() == ApprovalStatus.APPROVED) {
            return provider;
        }
        throw new AccessDeniedException("Solo un prestador aprobado puede usar el espacio del prestador.");
    }
}
