package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;

// Un horario en la pantalla del prestador, con sus lugares libres.
// Los lugares libres no se guardan en la tabla: se calculan en el momento con BookingService.availableSpots
public class ProviderScheduleDTO {

    private final ActivitySchedule schedule;
    private final int availableSpots;

    public ProviderScheduleDTO(ActivitySchedule schedule, int availableSpots) {
        this.schedule = schedule;
        this.availableSpots = availableSpots;
    }

    public ActivitySchedule getSchedule() {
        return schedule;
    }

    public int getAvailableSpots() {
        return availableSpots;
    }
}
