package uy.edu.um.nexo.mnatural.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Cada 10 minutos reintenta los reembolsos que fallaron en reservas ya canceladas (por ejemplo, el prestador
// suspendió un horario mientras el servicio de pagos no respondía). La especificación pide reintentarlo
// hasta que se complete (ESPECIFICACION.md, sección 3)
@Component
public class RefundRetryTask {

    private static final Logger log = LoggerFactory.getLogger(RefundRetryTask.class);

    private final PaymentService paymentService;

    public RefundRetryTask(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 60_000)
    public void retryFailedRefunds() {
        int done = paymentService.retryFailedRefunds();
        if (done > 0) {
            log.info("Se completaron {} reembolsos que habían fallado", done);
        }
    }
}
