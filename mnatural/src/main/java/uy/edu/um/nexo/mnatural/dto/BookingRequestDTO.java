package uy.edu.um.nexo.mnatural.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

// Lo que pide el turista al reservar. Los lugares, la edad mínima y el plazo
// los valida BookingService
public class BookingRequestDTO {

    @NotNull(message = "Elegí una fecha y horario")
    private Long activityScheduleId;

    // false cuando el titular reserva solo en representación de terceros
    private boolean holderParticipates = true;

    // Observación particular del titular, si participa
    private String holderNotes;

    private String generalNotes;

    // Las demás personas, sin contar al titular
    @Valid
    private List<ParticipantDTO> participants = new ArrayList<>();

    public BookingRequestDTO() {
    }

    public Long getActivityScheduleId() {
        return activityScheduleId;
    }

    public void setActivityScheduleId(Long activityScheduleId) {
        this.activityScheduleId = activityScheduleId;
    }

    public boolean isHolderParticipates() {
        return holderParticipates;
    }

    public void setHolderParticipates(boolean holderParticipates) {
        this.holderParticipates = holderParticipates;
    }

    public String getHolderNotes() {
        return holderNotes;
    }

    public void setHolderNotes(String holderNotes) {
        this.holderNotes = holderNotes;
    }

    public String getGeneralNotes() {
        return generalNotes;
    }

    public void setGeneralNotes(String generalNotes) {
        this.generalNotes = generalNotes;
    }

    public List<ParticipantDTO> getParticipants() {
        return participants;
    }

    public void setParticipants(List<ParticipantDTO> participants) {
        this.participants = participants;
    }
}
