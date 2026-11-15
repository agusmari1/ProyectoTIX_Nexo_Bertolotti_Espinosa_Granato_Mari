package uy.edu.um.nexo.mnatural.service;

import org.springframework.stereotype.Service;

// Qué hacer con una reserva cuando su pago se aprobó, venga el resultado de donde venga (el turista que vuelve de la
// página de pago, la pantalla de la reserva o el aviso del servicio de pagos): confirmarla, o, si ya no se puede
// confirmar porque venció o se canceló mientras se pagaba, devolver el dinero. Es idempotente: repetirlo no
// confirma dos veces ni reembolsa dos veces
@Service
public class PaymentConfirmationService {

    public enum Outcome {
        // No hay un pago aprobado: no se hace nada
        NOT_PAID,
        // La reserva está confirmada (ahora o desde antes)
        CONFIRMED,
        // Había un pago aprobado pero la reserva ya no se puede confirmar: el dinero se devuelve (ahora o en
        // unos minutos, cuando la reserva figure como vencida)
        REFUND
    }

    private final PaymentService paymentService;
    private final BookingConfirmationService bookingConfirmationService;

    public PaymentConfirmationService(PaymentService paymentService,
                                      BookingConfirmationService bookingConfirmationService) {
        this.paymentService = paymentService;
        this.bookingConfirmationService = bookingConfirmationService;
    }

    public Outcome confirmOrRefund(Long bookingId) {
        if (!paymentService.hasApprovedCharge(bookingId)) {
            return Outcome.NOT_PAID;
        }
        if (bookingConfirmationService.confirmIfPaid(bookingId)) {
            return Outcome.CONFIRMED;
        }
        // No se pudo confirmar (venció la retención o la reserva ya no está pendiente): no se queda con el dinero
        paymentService.refundIfPaidButNotConfirmed(bookingId);
        return Outcome.REFUND;
    }
}
