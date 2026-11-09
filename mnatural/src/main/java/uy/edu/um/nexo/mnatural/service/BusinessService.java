package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.nexo.mnatural.dto.BusinessDTO;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.BusinessNotFoundException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.repository.BusinessRepository;

import java.util.List;

// Emprendimientos del prestador con sesión iniciada: listar, agregar uno nuevo y editar.
// Todo pasa por ApprovedProviderResolver, así que solo un prestador aprobado puede usarlo,
// y siempre se busca por prestador dueño, así nadie toca emprendimientos ajenos
@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final ApprovedProviderResolver providerResolver;

    public BusinessService(BusinessRepository businessRepository, ApprovedProviderResolver providerResolver) {
        this.businessRepository = businessRepository;
        this.providerResolver = providerResolver;
    }

    @Transactional(readOnly = true)
    public List<Business> findOwn(String providerEmail) {
        Provider provider = providerResolver.require(providerEmail);
        return businessRepository.findByProviderId(provider.getId());
    }

    @Transactional(readOnly = true)
    public Business findOwn(String providerEmail, Long businessId) {
        Provider provider = providerResolver.require(providerEmail);
        return businessRepository.findByIdAndProviderId(businessId, provider.getId())
                .orElseThrow(BusinessNotFoundException::new);
    }

    @Transactional
    public Business create(String providerEmail, BusinessDTO dto) {
        Provider provider = providerResolver.require(providerEmail);
        Business business = new Business();
        business.setProvider(provider);
        apply(business, dto);
        return businessRepository.save(business);
    }

    @Transactional
    public Business update(String providerEmail, Long businessId, BusinessDTO dto) {
        Provider provider = providerResolver.require(providerEmail);
        Business business = businessRepository.findByIdAndProviderId(businessId, provider.getId())
                .orElseThrow(BusinessNotFoundException::new);
        apply(business, dto);
        return businessRepository.save(business);
    }

    // Mismas reglas que el registro: una empresa necesita RUT; los textos se guardan sin espacios sobrantes
    private void apply(Business business, BusinessDTO dto) {
        Long rut = dto.getRut();
        if (dto.getLegalType() == LegalType.COMPANY && rut == null) {
            throw new RutRequiredException();
        }
        business.setName(dto.getName().trim());
        business.setLegalType(dto.getLegalType());
        business.setRut(rut);
        business.setAddress(dto.getAddress().trim());
        business.setPhoneNumber(dto.getPhoneNumber().trim());
        business.setEmail(dto.getEmail().trim().toLowerCase());
        business.setServicesDescription(blankToNull(dto.getServicesDescription()));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
