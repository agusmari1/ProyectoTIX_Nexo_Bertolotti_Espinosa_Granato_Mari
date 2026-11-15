package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.Department;

import java.math.BigDecimal;
import java.util.List;

// Actividad en la API pública: la misma información que ve un visitante en el detalle
public record ApiActivityDTO(Long id, String name, String description, Department department, String locality,
                             String address, int durationMinutes, BigDecimal pricePerPerson, Integer minAge,
                             String requirements, String equipment, String businessName,
                             List<String> activityTypes) {

    public static ApiActivityDTO from(Activity activity) {
        List<String> types = activity.getActivityTypes().stream()
                .map(ActivityType::getName)
                .sorted()
                .toList();
        return new ApiActivityDTO(activity.getId(), activity.getName(), activity.getDescription(),
                activity.getDepartment(), activity.getLocality(), activity.getAddress(),
                activity.getDurationMinutes(), activity.getPricePerPerson(), activity.getMinAge(),
                activity.getRequirements(), activity.getEquipment(), activity.getBusiness().getName(), types);
    }
}
