package uy.edu.um.nexo.mnatural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.time.LocalDate;

// Crea al arrancar tres prestadores de prueba (uno aprobado, uno rechazado y uno pendiente),
// cada uno solo si todavía no existe, para probar el login y la pantalla del admin.
// TODO: sacar este seeder antes de entregar: crea usuarios con una contraseña conocida.
@Component
public class TestProviderSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestProviderSeeder.class);

    private static final String PASSWORD = "prueba1234";

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    public TestProviderSeeder(UserRepository userRepository,
                              ProviderRepository providerRepository,
                              BusinessRepository businessRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Se hashea una sola vez: los tres comparten la misma contraseña
        String passwordHash = passwordEncoder.encode(PASSWORD);

        createIfMissing("aprobadoprueba@gmail.com", "Aprobado", ApprovalStatus.APPROVED, null, passwordHash);
        createIfMissing("rechazadoprueba@gmail.com", "Rechazado", ApprovalStatus.REJECTED, "prueba de rechazo", passwordHash);
        createIfMissing("pendienteprueba@gmail.com", "Pendiente", ApprovalStatus.PENDING, null, passwordHash);
    }

    private void createIfMissing(String email, String name, ApprovalStatus status,
                                 String rejectionReason, String passwordHash) {
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Provider provider = new Provider();
        provider.setEmail(email);
        provider.setPasswordHash(passwordHash);
        provider.setName(name);
        provider.setLastName("Prueba");
        provider.setDateOfBirth(LocalDate.of(1990, 1, 1));
        provider.setDocumentType(DocumentType.OTHER);
        provider.setDocumentNumber("00000000");
        provider.setCountry("Uruguay");
        provider.setApprovalStatus(status);
        provider.setRejectionReason(rejectionReason);
        providerRepository.save(provider);

        Business business = new Business();
        business.setName("Emprendimiento " + name);
        business.setLegalType(LegalType.INDIVIDUAL);
        business.setAddress("Calle de Prueba 123");
        business.setPhoneNumber("099000000");
        business.setEmail(email);
        business.setProvider(provider);
        businessRepository.save(business);

        log.info("Se creó el prestador de prueba {} ({})", email, status);
    }
}
