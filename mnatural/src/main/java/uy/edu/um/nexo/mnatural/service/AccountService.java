package uy.edu.um.nexo.mnatural.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.PasswordChangeDTO;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.exception.InvalidPasswordChangeException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

// Cuenta propia de cualquier usuario con sesión iniciada (turista, prestador o admin).
// Por ahora: cambiar la contraseña. Útil, por ejemplo, para el admin que recibió una contraseña generada por correo
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Solo se cambia la contraseña del usuario que pide el cambio (se identifica por su sesión, no por un parámetro).
    // Se pide la actual para que alguien que encuentre la sesión abierta no pueda cambiarla. Se guarda solo el hash
    @Transactional
    public void changePassword(String email, PasswordChangeDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("No se encontró tu cuenta."));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordChangeException("currentPassword", "La contraseña actual no es correcta.");
        }
        if (dto.getNewPassword().equals(dto.getCurrentPassword())) {
            throw new InvalidPasswordChangeException("newPassword",
                    "La contraseña nueva tiene que ser distinta de la actual.");
        }

        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }
}
