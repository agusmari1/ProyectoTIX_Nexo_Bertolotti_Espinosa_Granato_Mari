package uy.edu.um.nexo.mnatural.dto;

import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.Participant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// Una reserva tal como la ve el prestador. Según la especificación (secciones 4 y 6), los datos del turista
// (nombre, correo y teléfono) y de los participantes solo se muestran cuando la reserva está confirmada:
// en una reserva pendiente, cancelada o vencida esos campos quedan vacíos
public record ProviderBookingDTO(Long id,
                                 String code,
                                 BookingStatus status,
                                 Long activityId,
                                 String activityName,
                                 Long scheduleId,
                                 LocalDateTime startDateTime,
                                 int participantCount,
                                 boolean personalDataVisible,
                                 String holderName,
                                 String holderEmail,
                                 String holderPhone,
                                 String generalNotes,
                                 List<ParticipantRow> participants) {

    public record ParticipantRow(String name, String lastName, String documentType, String documentNumber,
                                 LocalDate dateOfBirth, String notes) {
    }

    public static ProviderBookingDTO from(Booking booking) {
        boolean confirmed = booking.getStatus() == BookingStatus.CONFIRMED;
        var schedule = booking.getActivitySchedule();
        var activity = schedule.getActivity();

        String holderName = null;
        String holderEmail = null;
        String holderPhone = null;
        String notes = null;
        List<ParticipantRow> participants = List.of();
        if (confirmed) {
            var tourist = booking.getTourist();
            holderName = tourist.getName() + " " + tourist.getLastName();
            holderEmail = tourist.getEmail();
            holderPhone = tourist.getPhoneNumber();
            notes = booking.getGeneralNotes();
            participants = booking.getParticipants().stream()
                    .sorted(Comparator.comparing(Participant::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(p -> new ParticipantRow(p.getName(), p.getLastName(), documentLabel(p.getDocumentType()),
                            p.getDocumentNumber(), p.getDateOfBirth(), p.getNotes()))
                    .toList();
        }
        return new ProviderBookingDTO(booking.getId(), booking.getBookingCode(), booking.getStatus(),
                activity.getId(), activity.getName(), schedule.getId(), schedule.getStartDateTime(),
                booking.getParticipantCount(), confirmed, holderName, holderEmail, holderPhone, notes, participants);
    }

    public String statusLabel() {
        return statusLabel(status);
    }

    public static String statusLabel(BookingStatus status) {
        return switch (status) {
            case PENDING -> "Pendiente";
            case CONFIRMED -> "Confirmada";
            case CANCELLED -> "Cancelada";
            case EXPIRED -> "Vencida";
        };
    }

    private static String documentLabel(DocumentType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case CI -> "CI";
            case PASSPORT -> "Pasaporte";
            case DNI -> "DNI";
            case OTHER -> "Otro";
        };
    }
}
