package uy.edu.um.nexo.mnatural.service;

import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;

// Resultado de BookingCancellationService.cancelByTourist.
// cancelled en false: el reembolso falló y la reserva sigue confirmada (el turista puede volver a intentar).
// refund es null cuando la política no devuelve nada (0 %)
public record CancellationResult(boolean cancelled, PaymentTransaction refund, String message) {
}
