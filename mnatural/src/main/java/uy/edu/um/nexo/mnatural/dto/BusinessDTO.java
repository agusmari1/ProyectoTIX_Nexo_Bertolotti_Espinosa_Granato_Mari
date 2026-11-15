package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.edu.um.nexo.mnatural.entities.Business;
import uy.edu.um.nexo.mnatural.entities.LegalType;

// Formulario de un emprendimiento (agregar uno nuevo o editar uno existente).
// Las validaciones son las mismas que las del registro de prestadores (ProviderRegistrationDTO)
public class BusinessDTO {

    @NotBlank(message = "El nombre del emprendimiento es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String name;

    @NotNull(message = "El tipo de emprendimiento es obligatorio")
    private LegalType legalType;

    // Opcional, salvo para empresas (lo valida el service). Si viene, 12 dígitos
    @Min(value = 100000000000L, message = ProviderRegistrationDTO.RUT_MESSAGE)
    @Max(value = 999999999999L, message = ProviderRegistrationDTO.RUT_MESSAGE)
    private Long rut;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
    private String address;

    @NotBlank(message = "El teléfono del emprendimiento es obligatorio")
    @Size(max = 255, message = "El teléfono no puede superar los 255 caracteres")
    private String phoneNumber;

    @NotBlank(message = "El correo del emprendimiento es obligatorio")
    @Email(message = "El correo del emprendimiento no es válido")
    @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
    private String email;

    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
    private String servicesDescription;

    public BusinessDTO() {
    }

    // Para llenar el formulario de edición con los datos actuales
    public static BusinessDTO from(Business business) {
        BusinessDTO dto = new BusinessDTO();
        dto.setName(business.getName());
        dto.setLegalType(business.getLegalType());
        dto.setRut(business.getRut());
        dto.setAddress(business.getAddress());
        dto.setPhoneNumber(business.getPhoneNumber());
        dto.setEmail(business.getEmail());
        dto.setServicesDescription(business.getServicesDescription());
        return dto;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LegalType getLegalType() {
        return legalType;
    }

    public void setLegalType(LegalType legalType) {
        this.legalType = legalType;
    }

    public Long getRut() {
        return rut;
    }

    public void setRut(Long rut) {
        this.rut = rut;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getServicesDescription() {
        return servicesDescription;
    }

    public void setServicesDescription(String servicesDescription) {
        this.servicesDescription = servicesDescription;
    }
}
