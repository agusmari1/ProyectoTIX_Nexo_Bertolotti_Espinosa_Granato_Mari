package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;

// Un horario del detalle público con sus lugares libres (calculados con BookingService)
// y si todavía se puede reservar según el límite de minutos del prestador
public class ScheduleAvailabilityDTO {

    private final ActivitySchedule schedule;
    private final int availableSpots;
    private final boolean bookingOpen;

    public ScheduleAvailabilityDTO(ActivitySchedule schedule, int availableSpots, boolean bookingOpen) {
        this.schedule = schedule;
        this.availableSpots = availableSpots;
        this.bookingOpen = bookingOpen;
    }

    public boolean isBookable() {
        return bookingOpen && availableSpots > 0;
    }

    public ActivitySchedule getSchedule() {
        return schedule;
    }

    public int getAvailableSpots() {
        return availableSpots;
    }

    public boolean isBookingOpen() {
        return bookingOpen;
    }
}
