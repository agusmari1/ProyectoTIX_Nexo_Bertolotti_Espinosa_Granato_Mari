package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

// Datos del formulario para agregar un horario. Que la fecha sea futura
// y que haya cupos los valida ActivityService
public class ActivityScheduleDTO {

    // Formato que envía <input type="datetime-local">, por ejemplo 2026-11-05T09:30
    @NotNull(message = "La fecha y hora son obligatorias")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDateTime;

    @NotNull(message = "La cantidad de cupos es obligatoria")
    private Integer capacity;

    public ActivityScheduleDTO() {
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
