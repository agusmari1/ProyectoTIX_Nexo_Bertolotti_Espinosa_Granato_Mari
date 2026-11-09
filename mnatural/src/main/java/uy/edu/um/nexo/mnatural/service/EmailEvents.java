package uy.edu.um.nexo.mnatural.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Eventos que disparan un correo (ver EmailNotifier). Se publican DENTRO de la transacción que hace el cambio y el
// correo sale recién cuando esa transacción se confirma (AFTER_COMMIT). Traen copiados los datos del mensaje, así quien
// los recibe no necesita volver a leer la base. Ninguno lleva contraseñas ni datos de otros usuarios
public final class EmailEvents {

    private EmailEvents() {
    }

    // Se creó una cuenta de turista
    public record TouristRegistered(String name, String email) {
    }

    // Se registró un prestador: queda pendiente de aprobación
    public record ProviderRegistered(String name, String email, String businessName) {
    }

    // Se creó una reserva, pendiente de pago. payBefore es hasta cuándo quedan retenidos los lugares
    public record BookingPending(Long bookingId, String touristEmail, String touristName, String bookingCode,
                                 String activityName, LocalDateTime startDateTime, int participantCount,
                                 BigDecimal totalAmount, LocalDateTime payBefore) {
    }

    // Un intento de pago quedó rechazado o con error. error=true si falló el servicio de pagos o hubo un problema
    // que no es de la tarjeta. payBefore: hasta cuándo se puede reintentar (puede ser null si ya no)
    public record PaymentFailed(Long bookingId, String touristEmail, String touristName, String activityName,
                                boolean error, String reason, LocalDateTime payBefore) {
    }

    // Una reserva se canceló: por el turista (según la política de la actividad) o porque el prestador suspendió la
    // fecha y horario o la actividad (reembolso del 100 %). refundAmount es null si no hubo cobro o no corresponde
    // reembolso. participantCount sirve para el aviso al prestador cuando cancela un turista
    public record BookingCancelled(Long bookingId, String touristEmail, String touristName, String bookingCode,
                                   String activityName, LocalDateTime startDateTime, int participantCount, Cause cause,
                                   BigDecimal refundAmount, RefundState refundState) {

        public enum Cause {
            BY_TOURIST,
            SCHEDULE_SUSPENDED
        }

        public enum RefundState {
            // No hay reembolso: el turista canceló fuera de lo que cubre la política, o la reserva no se había pagado
            NONE,
            // El servicio de pagos ya lo aprobó
            COMPLETED,
            // Está en proceso o falló y se reintenta (RefundRetryTask): se avisa de nuevo UNA vez cuando se complete
            IN_PROGRESS
        }
    }

    // Un reembolso que estaba en proceso o falló terminó de hacerse. Se publica una sola vez por reembolso.
    // reason: por qué se devuelve el dinero (null si el motivo ya se contó en el aviso de cancelación)
    public record RefundCompleted(Long bookingId, String touristEmail, String touristName, String bookingCode,
                                  String activityName, BigDecimal amount, String reason) {
    }

    // Una reserva venció por falta de pago
    public record BookingExpired(Long bookingId, String touristEmail, String touristName, String bookingCode,
                                 String activityName, LocalDateTime startDateTime) {
    }
}
