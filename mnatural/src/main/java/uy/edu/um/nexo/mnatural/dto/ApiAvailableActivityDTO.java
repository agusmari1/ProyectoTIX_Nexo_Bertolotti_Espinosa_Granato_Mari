package uy.edu.um.nexo.mnatural.dto;

import java.util.List;

// Una actividad con los horarios que tiene disponibles en el día consultado
public record ApiAvailableActivityDTO(ApiActivityDTO activity, List<ApiScheduleDTO> schedules) {
}
