package uy.edu.um.nexo.mnatural.dto;

import java.time.LocalDateTime;

// Resultado de verificar un código de reserva en el espacio del prestador.
// Solo trae datos cuando la reserva es válida (de ese prestador y confirmada); si no, solo el motivo
public record BookingVerificationDTO(boolean valid,
                                     String message,
                                     String code,
                                     String activityName,
                                     LocalDateTime startDateTime,
                                     String holderName,
                                     int participantCount,
                                     String statusLabel) {

    public static BookingVerificationDTO invalid(String message) {
        return new BookingVerificationDTO(false, message, null, null, null, null, 0, null);
    }

    public static BookingVerificationDTO invalid(String message, String code, String activityName,
                                                 LocalDateTime startDateTime, String statusLabel) {
        return new BookingVerificationDTO(false, message, code, activityName, startDateTime, null, 0, statusLabel);
    }
}
