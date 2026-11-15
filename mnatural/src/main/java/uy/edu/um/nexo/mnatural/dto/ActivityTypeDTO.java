package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Formulario para crear un tipo de actividad
public class ActivityTypeDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
    private String name;

    public ActivityTypeDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
