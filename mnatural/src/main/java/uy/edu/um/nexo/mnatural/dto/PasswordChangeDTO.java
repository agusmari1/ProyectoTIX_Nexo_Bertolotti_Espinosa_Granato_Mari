package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Formulario para que cualquier usuario cambie su propia contraseña
public class PasswordChangeDTO {

    @NotBlank(message = "Ingresá tu contraseña actual")
    private String currentPassword;

    @NotBlank(message = "La contraseña nueva es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String newPassword;

    // Solo sirve para validar el formulario: no se guarda en ningún lado
    @NotBlank(message = "Repetí la contraseña nueva")
    private String confirmPassword;

    public PasswordChangeDTO() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
