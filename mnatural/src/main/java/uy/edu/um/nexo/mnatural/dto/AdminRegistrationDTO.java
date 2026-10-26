package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Formulario para que un administrador cree a otro. El usuario con el que va a ingresar es su
// correo electrónico. No se pide contraseña: la genera el sistema y se manda por correo
public class AdminRegistrationDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    @NotBlank(message = "El apellido es obligatorio")
    private String lastName;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    // El @Pattern pide el mismo formato que el login (algo@algo.algo): con solo @Email se
    // podría crear un correo como "a@b" con el que después no se puede iniciar sesión
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Size(max = 254, message = "El correo electrónico es demasiado largo")
    @Email(message = "El correo electrónico no es válido")
    @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "El correo electrónico no es válido")
    private String email;

    public AdminRegistrationDTO() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
