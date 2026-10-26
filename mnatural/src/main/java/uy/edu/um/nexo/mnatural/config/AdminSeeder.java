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
import uy.edu.um.nexo.mnatural.service.PasswordGenerator;

import java.time.LocalDate;

// Crea al arrancar el PRIMER administrador, solo si todavía no existe ninguno. Los demás
// los crea un admin desde /admin/administradores.
// La contraseña inicial no está en el código: se lee de la variable de entorno ADMIN_PASSWORD
// (y el correo, opcional, de ADMIN_EMAIL). Si ADMIN_PASSWORD no está definida se genera una
// contraseña al azar y se muestra UNA sola vez en el log, así el proyecto arranca y se puede
// usar en cualquier máquina sin configurar nada.
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String configuredPassword;

    public AdminSeeder(UserRepository userRepository,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${ADMIN_EMAIL:admin@mnatural.uy}") String email,
                       @Value("${ADMIN_PASSWORD:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase();
        this.configuredPassword = configuredPassword;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() > 0) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("No se creó el administrador inicial: el correo {} ya lo usa otro usuario. "
                    + "Definí ADMIN_EMAIL con otro correo.", email);
            return;
        }

        boolean generated = configuredPassword.isBlank();
        String password = generated ? PasswordGenerator.generate() : configuredPassword;

        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setName("Administrador");
        admin.setLastName("Mnatural");
        admin.setDateOfBirth(LocalDate.of(1990, 1, 1));
        admin.setDocumentType(DocumentType.OTHER);
        admin.setDocumentNumber("00000000");
        adminRepository.save(admin);

        if (generated) {
            log.info("Se creó el administrador inicial {} con una contraseña generada: {} "
                    + "(se muestra solo esta vez; definí ADMIN_PASSWORD para elegir la tuya)", email, password);
        } else {
            log.info("Se creó el administrador inicial {} con la contraseña de ADMIN_PASSWORD", email);
        }
    }
}
