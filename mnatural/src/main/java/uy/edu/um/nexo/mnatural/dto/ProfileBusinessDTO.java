package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;

// Los datos de un emprendimiento que el prestador ve en su propio perfil
public record ProfileBusinessDTO(String name, LegalType legalType, Long rut, String address, String phoneNumber,
                                 String email, String servicesDescription) {

    public static ProfileBusinessDTO from(Business business) {
        return new ProfileBusinessDTO(business.getName(), business.getLegalType(), business.getRut(),
                business.getAddress(), business.getPhoneNumber(), business.getEmail(),
                business.getServicesDescription());
    }
}
