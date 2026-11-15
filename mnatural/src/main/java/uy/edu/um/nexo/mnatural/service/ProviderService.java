package uy.edu.um.nexo.mnatural.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.ProviderRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;
import uy.edu.um.nexo.mnatural.repository.ProviderRepository;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

@Service
public class ProviderService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    // Este es el que usa Spring. Los correos (al prestador y a los administradores) salen recién cuando se confirma la
    // transacción (EmailNotifier)
    @Autowired
    public ProviderService(UserRepository userRepository,
                           ProviderRepository providerRepository,
                           BusinessRepository businessRepository,
                           PasswordEncoder passwordEncoder,
                           ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    // Sin avisos por correo (para los tests que no lo necesitan)
    public ProviderService(UserRepository userRepository,
                           ProviderRepository providerRepository,
                           BusinessRepository businessRepository,
                           PasswordEncoder passwordEncoder) {
        this(userRepository, providerRepository, businessRepository, passwordEncoder, event -> { });
    }

    @Transactional
    public Provider register(ProviderRegistrationDTO dto) {
        String email = dto.getEmail().trim().toLowerCase();
        // Se valida contra todos los usuarios (turistas, prestadores y admins),
        // porque el email es único en app_user
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Long rut = dto.getRut();
        if (dto.getLegalType() == LegalType.COMPANY && rut == null) {
            throw new RutRequiredException();
        }

        Provider provider = new Provider();
        provider.setName(dto.getName().trim());
        provider.setLastName(dto.getLastName().trim());
        provider.setDateOfBirth(dto.getDateOfBirth());
        provider.setDocumentType(dto.getDocumentType());
        provider.setDocumentNumber(dto.getDocumentNumber().trim());
        provider.setCountry(dto.getCountry().trim());
        provider.setEmail(email);
        provider.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        provider.setPhoneNumber(blankToNull(dto.getPhoneNumber()));
        provider.setApprovalStatus(ApprovalStatus.PENDING);
        providerRepository.save(provider);

        Business business = new Business();
        business.setName(dto.getBusinessName().trim());
        business.setLegalType(dto.getLegalType());
        business.setRut(rut);
        business.setAddress(dto.getAddress().trim());
        business.setPhoneNumber(dto.getBusinessPhone().trim());
        business.setEmail(dto.getBusinessEmail().trim().toLowerCase());
        business.setProvider(provider);
        provider.getBusinesses().add(business);
        businessRepository.save(business);
        eventPublisher.publishEvent(new EmailEvents.ProviderRegistered(provider.getName(), provider.getEmail(),
                business.getName()));

        return provider;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
