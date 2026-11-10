package uy.edu.um.nexo.mnatural.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.TouristRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.text.Normalizer;
import java.util.Set;

@Service
public class TouristService {

    // En minúsculas y sin tildes. Incluye los nombres en inglés de los que se escriben distinto
    private static final Set<String> SPANISH_SPEAKING_COUNTRIES = Set.of(
            "argentina", "bolivia", "chile", "colombia", "costa rica", "cuba", "ecuador", "el salvador",
            "espana", "spain", "guatemala", "guinea ecuatorial", "equatorial guinea", "honduras", "mexico",
            "nicaragua", "panama", "paraguay", "peru", "puerto rico", "republica dominicana",
            "dominican republic", "uruguay", "venezuela");
    private static final Set<String> PORTUGUESE_SPEAKING_COUNTRIES = Set.of("brasil", "brazil", "portugal");

    private final UserRepository userRepository;
    private final TouristRepository touristRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    // Este es el que usa Spring. El correo de bienvenida sale recién cuando se confirma la transacción (EmailNotifier)
    @Autowired
    public TouristService(UserRepository userRepository,
                          TouristRepository touristRepository,
                          PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.touristRepository = touristRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // Sin avisos por correo (para los tests que no lo necesitan)
    public TouristService(UserRepository userRepository,
                          TouristRepository touristRepository,
                          PasswordEncoder passwordEncoder) {
        this(userRepository, touristRepository, passwordEncoder, event -> { });
    }

    @Transactional
    public Tourist register(TouristRegistrationDTO dto) {
        String email = dto.getEmail().trim().toLowerCase();
        // Se valida contra todos los usuarios (turistas, prestadores y admins),
        // porque el email es único en app_user
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Tourist tourist = new Tourist();
        tourist.setName(dto.getName().trim());
        tourist.setLastName(dto.getLastName().trim());
        tourist.setDateOfBirth(dto.getDateOfBirth());
        tourist.setDocumentType(dto.getDocumentType());
        tourist.setDocumentNumber(dto.getDocumentNumber().trim());
        tourist.setCountry(dto.getCountry().trim());
        tourist.setEmail(email);
        tourist.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        tourist.setPhoneNumber(blankToNull(dto.getPhoneNumber()));
        tourist.setExceptionalCondition(blankToNull(dto.getExceptionalCondition()));
        tourist.setLanguage(languageFor(tourist.getCountry()));
        Tourist saved = touristRepository.save(tourist);
        eventPublisher.publishEvent(new EmailEvents.TouristRegistered(saved.getName(), saved.getEmail()));
        return saved;
    }

    // El turista logueado. Solo los turistas pueden reservar
    @Transactional(readOnly = true)
    public Tourist getByEmail(String email) {
        return userRepository.findByEmail(email)
                .filter(Tourist.class::isInstance)
                .map(Tourist.class::cast)
                .orElseThrow(() -> new BookingException("Solo los turistas pueden hacer reservas."));
    }

    // Idioma según el país (ESPECIFICACION.md, sección 1). El país se escribe a mano,
    // así que se compara sin mayúsculas ni tildes. El idioma por defecto es el inglés
    private String languageFor(String country) {
        String normalized = Normalizer.normalize(country.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (SPANISH_SPEAKING_COUNTRIES.contains(normalized)) {
            return "es";
        }
        return PORTUGUESE_SPEAKING_COUNTRIES.contains(normalized) ? "pt" : "en";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
