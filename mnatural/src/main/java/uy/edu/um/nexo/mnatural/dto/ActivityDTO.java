package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

// Datos del formulario de alta de actividad. Acá solo se controla que los campos
// obligatorios estén completos; las reglas de negocio (precio > 0, política de
// cancelación, etc.) las valida ActivityService
public class ActivityDTO {

    // Null si el prestador tiene un solo emprendimiento: el service usa ese
    private Long businessId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotBlank(message = "El punto de encuentro es obligatorio")
    @Size(max = 255, message = "El punto de encuentro no puede superar los 255 caracteres")
    private String address;

    @NotNull(message = "El departamento es obligatorio")
    private Department department;

    @NotBlank(message = "La localidad es obligatoria")
    @Size(max = 255, message = "La localidad no puede superar los 255 caracteres")
    private String locality;

    @NotNull(message = "La duración es obligatoria")
    private Integer durationMinutes;

    @NotNull(message = "El precio es obligatorio")
    @Digits(integer = 8, fraction = 2, message = "El precio puede tener hasta 8 cifras y 2 decimales")
    private BigDecimal pricePerPerson;

    private Integer minAge;

    private String requirements;

    private String equipment;

    @NotNull(message = "El límite de reserva es obligatorio (puede ser 0)")
    private Integer bookingCutoffMinutes = 0;

    private boolean cancellationAllowed;

    private Integer cancellationCutoffHours;

    @Digits(integer = 3, fraction = 2, message = "El porcentaje puede tener hasta 2 decimales")
    private BigDecimal refundPercentage;

    private Set<Long> activityTypeIds = new HashSet<>();

    public ActivityDTO() {
    }

    public Long getBusinessId() {
        return businessId;
    }

    public void setBusinessId(Long businessId) {
        this.businessId = businessId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPricePerPerson() {
        return pricePerPerson;
    }

    public void setPricePerPerson(BigDecimal pricePerPerson) {
        this.pricePerPerson = pricePerPerson;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public Integer getBookingCutoffMinutes() {
        return bookingCutoffMinutes;
    }

    public void setBookingCutoffMinutes(Integer bookingCutoffMinutes) {
        this.bookingCutoffMinutes = bookingCutoffMinutes;
    }

    public boolean isCancellationAllowed() {
        return cancellationAllowed;
    }

    public void setCancellationAllowed(boolean cancellationAllowed) {
        this.cancellationAllowed = cancellationAllowed;
    }

    public Integer getCancellationCutoffHours() {
        return cancellationCutoffHours;
    }

    public void setCancellationCutoffHours(Integer cancellationCutoffHours) {
        this.cancellationCutoffHours = cancellationCutoffHours;
    }

    public BigDecimal getRefundPercentage() {
        return refundPercentage;
    }

    public void setRefundPercentage(BigDecimal refundPercentage) {
        this.refundPercentage = refundPercentage;
    }

    public Set<Long> getActivityTypeIds() {
        return activityTypeIds;
    }

    public void setActivityTypeIds(Set<Long> activityTypeIds) {
        this.activityTypeIds = activityTypeIds;
    }
}
