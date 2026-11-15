package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uy.edu.um.nexo.mnatural.payment.PaymentGateway;

import java.util.Optional;

// Procesa un aviso (webhook) del servicio de pagos, sin que el turista tenga que volver a la aplicación.
// El aviso solo trae el id del pago y NO se le cree nada más: con ese id se consulta el pago al servicio, de ahí sale
// la referencia, y el estado y el monto reales se vuelven a consultar antes de confirmar nada.
// Es idempotente: el servicio puede mandar el mismo aviso varias veces, y repetirlo no confirma dos veces ni
// reembolsa dos veces (un intento aprobado no se vuelve a tocar y una reserva confirmada se deja como está).
// Si el servicio de pagos no responde, la excepción sube y el aviso se contesta con error para que lo reintente
@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentGateway paymentGateway;
    private final PaymentService paymentService;
    private final PaymentConfirmationService paymentConfirmationService;

    public PaymentWebhookService(PaymentGateway paymentGateway,
                                 PaymentService paymentService,
                                 PaymentConfirmationService paymentConfirmationService) {
        this.paymentGateway = paymentGateway;
        this.paymentService = paymentService;
        this.paymentConfirmationService = paymentConfirmationService;
    }

    // Devuelve true si el aviso correspondía a un pago de esta aplicación (y se procesó)
    public boolean handlePaymentNotification(String paymentId) {
        Optional<String> reference = paymentGateway.findExternalReference(paymentId);
        if (reference.isEmpty()) {
            log.info("Aviso de pago {} ignorado: no es un pago de esta aplicación", paymentId);
            return false;
        }
        Optional<Long> bookingId = paymentService.syncByNotification(reference.get());
        if (bookingId.isEmpty()) {
            return false;
        }
        PaymentConfirmationService.Outcome outcome = paymentConfirmationService.confirmOrRefund(bookingId.get());
        log.info("Aviso de pago {} procesado: reserva {}, resultado {}", paymentId, bookingId.get(), outcome);
        return true;
    }
}
