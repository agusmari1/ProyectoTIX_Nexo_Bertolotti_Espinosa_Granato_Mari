package uy.edu.um.nexo.mnatural.service;

// Resultado de suspender un horario: cuántas reservas se cancelaron, cuántas se reembolsaron al 100 %
// y cuántos reembolsos fallaron (esos los reintenta RefundRetryTask)
public record SuspensionResult(int cancelledBookings, int refunded, int failedRefunds) {
}
