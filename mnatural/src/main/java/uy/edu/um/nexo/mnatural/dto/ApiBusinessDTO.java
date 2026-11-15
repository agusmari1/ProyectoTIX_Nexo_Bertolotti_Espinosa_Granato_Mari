package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;

// Emprendimiento en la API pública. El correo y el teléfono no se exponen: la sección 4 los
// muestra recién con una reserva confirmada. El RUT y la dirección tampoco, porque la
// dirección de una persona particular puede ser su casa
public record ApiBusinessDTO(Long id, String name, LegalType legalType, String servicesDescription) {

    public static ApiBusinessDTO from(Business business) {
        return new ApiBusinessDTO(business.getId(), business.getName(), business.getLegalType(),
                business.getServicesDescription());
    }
}
