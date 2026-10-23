package uy.edu.um.nexo.mnatural.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.TouristRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.repository.TouristRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

@Service
public class TouristService {

    private final UserRepository userRepository;
    private final TouristRepository touristRepository;
    private final PasswordEncoder passwordEncoder;

    public TouristService(UserRepository userRepository,
                          TouristRepository touristRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.touristRepository = touristRepository;
        this.passwordEncoder = passwordEncoder;
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
        // TODO: asignar el idioma según el país (ESPECIFICACION.md, sección 1). Por ahora queda en null
        return touristRepository.save(tourist);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
