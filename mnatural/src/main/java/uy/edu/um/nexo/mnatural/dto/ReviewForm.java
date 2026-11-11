package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Formulario para dejar una reseña: puntaje de 1 a 5 y un comentario opcional
public class ReviewForm {

    @NotNull(message = "Elegí un puntaje de 1 a 5")
    @Min(value = 1, message = "El puntaje tiene que ser de 1 a 5")
    @Max(value = 5, message = "El puntaje tiene que ser de 1 a 5")
    private Integer rating;

    @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
    private String comment;

    public ReviewForm() {
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
