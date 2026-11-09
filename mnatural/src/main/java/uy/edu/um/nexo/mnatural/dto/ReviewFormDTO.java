package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Formulario con el que el turista califica una reserva
public class ReviewFormDTO {

    @NotNull(message = "Elegí una calificación de 1 a 5")
    @Min(value = 1, message = "La calificación va de 1 a 5")
    @Max(value = 5, message = "La calificación va de 1 a 5")
    private Integer rating;

    @NotBlank(message = "Escribí un comentario")
    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    private String comment;

    public ReviewFormDTO() {
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
