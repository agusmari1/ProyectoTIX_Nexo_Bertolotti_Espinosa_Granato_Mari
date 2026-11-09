package uy.edu.um.nexo.mnatural.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.um.nexo.mnatural.service.PaymentWebhookService;

import java.util.Map;

// POST /payments/webhook: adonde Mercado Pago avisa que cambió el estado de un pago (notification_url).
// No tiene sesión (lo llama Mercado Pago, no un turista): SecurityConfig lo deja pasar sin sesión y sin CSRF.
// Por eso no se le cree al aviso: solo se toma el id del pago y lo demás se consulta al servicio.
// Respuestas: 200 si se entendió el aviso (aunque no sea de esta aplicación o no haya nada que hacer), para que no se
// reintente; 500 si algo falló de nuestro lado o Mercado Pago no respondió, para que Mercado Pago lo vuelva a mandar.
// Mercado Pago manda el id de dos formas: ?type=payment&data.id=123 (con un JSON) o, en el formato viejo,
// ?topic=payment&id=123
@RestController
@RequestMapping("/payments/webhook")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentWebhookService webhookService;

    public PaymentWebhookController(PaymentWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestParam(name = "type", required = false) String type,
                                        @RequestParam(name = "topic", required = false) String topic,
                                        @RequestParam(name = "data.id", required = false) String dataId,
                                        @RequestParam(name = "id", required = false) String id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String kind = firstNonBlank(type, topic, text(body == null ? null : body.get("type")));
        // Otros avisos (por ejemplo merchant_order) no interesan
        if (!"payment".equals(kind)) {
            return ResponseEntity.ok().build();
        }
        String paymentId = firstNonBlank(dataId, id, bodyDataId(body));
        if (paymentId == null) {
            return ResponseEntity.ok().build();
        }
        try {
            webhookService.handlePaymentNotification(paymentId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            // Sin respuesta de Mercado Pago o un error propio: no se confirma nada y se pide que reenvíen el aviso
            log.warn("No se pudo procesar el aviso del pago {}: {}", paymentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private static String bodyDataId(Map<String, Object> body) {
        if (body != null && body.get("data") instanceof Map<?, ?> data) {
            return text(data.get("id"));
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
