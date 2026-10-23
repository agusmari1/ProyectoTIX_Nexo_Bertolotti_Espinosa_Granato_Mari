package uy.edu.um.nexo.mnatural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDate;

// Crea un administrador de prueba al arrancar la app, solo si todavía no existe,
// para poder entrar a /admin/prestadores en desarrollo.
// Los datos se pueden cambiar con app.admin.email y app.admin.password (por ejemplo
// con variables de entorno), sin tocar el código.
// TODO: antes de entregar o desplegar, sacar este usuario de prueba o cambiarle la contraseña
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepository,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.email:admin@mnatural.uy}") String email,
                       @Value("${app.admin.password:Admin-Mnatural-2026}") String password) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setName("Administrador");
        admin.setLastName("Mnatural");
        admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
        admin.setDocumentType(DocumentType.OTHER);
        admin.setDocumentNumber("00000000");
        adminRepository.save(admin);

        log.info("Se creó el administrador de prueba {}", email);
    }
}
