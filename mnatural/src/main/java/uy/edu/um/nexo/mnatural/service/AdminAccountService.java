package uy.edu.um.nexo.mnatural.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.AdminRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.exception.AdminNotFoundException;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.List;

// Cuentas de administrador: un admin con sesión iniciada crea a otros.
// El usuario del nuevo admin es su correo electrónico. Su contraseña la genera el sistema al azar
// y se la manda SOLO por correo: quien lo crea no la elige ni la ve.
@Service
public class AdminAccountService {

    // User exige tipo y número de documento, pero a un admin no se los pedimos: se usan los
    // mismos valores de relleno que el administrador inicial (AdminSeeder)
    private static final String PLACEHOLDER_DOCUMENT_NUMBER = "00000000";

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AdminAccountService(UserRepository userRepository,
                               AdminRepository adminRepository,
                               PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // En el orden en que se crearon
    @Transactional(readOnly = true)
    public List<Admin> findAll() {
        return adminRepository.findAll(Sort.by("id"));
    }

    @Transactional
    public Admin create(AdminRegistrationDTO dto) {
        String email = dto.getEmail().trim().toLowerCase();
        // Se valida contra todos los usuarios (turistas, prestadores y admins),
        // porque el email es único en app_user
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        String password = PasswordGenerator.generate();

        Admin admin = new Admin();
        admin.setName(dto.getName().trim());
        admin.setLastName(dto.getLastName().trim());
        admin.setDateOfBirth(dto.getDateOfBirth());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDocumentType(DocumentType.OTHER);
        admin.setDocumentNumber(PLACEHOLDER_DOCUMENT_NUMBER);
        Admin saved = adminRepository.save(admin);

        // El correo con los datos de acceso lo manda AdminCreatedNotifier recién cuando esta
        // transacción se confirma
        eventPublisher.publishEvent(new AdminCreatedEvent(saved.getName(), saved.getEmail(), password));
        return saved;
    }

    // Genera una contraseña nueva al azar y se la manda por correo. La anterior deja de servir.
    // Es la salida cuando el correo de la cuenta nueva no llegó (nadie conoce la contraseña)
    @Transactional
    public Admin resetPassword(Long adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminNotFoundException(adminId));

        String password = PasswordGenerator.generate();
        admin.setPasswordHash(passwordEncoder.encode(password));
        Admin saved = adminRepository.save(admin);

        eventPublisher.publishEvent(new AdminPasswordResetEvent(saved.getName(), saved.getEmail(), password));
        return saved;
    }
}
