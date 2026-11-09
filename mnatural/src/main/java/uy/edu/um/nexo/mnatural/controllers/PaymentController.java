package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.BookingPaymentDTO;
import uy.edu.um.nexo.mnatural.entities.BookingStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentStatus;
import uy.edu.um.nexo.mnatural.entities.PaymentTransaction;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.exception.PaymentException;
import uy.edu.um.nexo.mnatural.payment.PaymentStart;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.BookingCancellationService;
import uy.edu.um.nexo.mnatural.service.CancellationResult;
import uy.edu.um.nexo.mnatural.service.BookingQrService;
import uy.edu.um.nexo.mnatural.service.PaymentConfirmationService;
import uy.edu.um.nexo.mnatural.service.PaymentService;

// Pago de una reserva del turista logueado:
//   GET  /payments/bookings/{id}        resumen de la reserva, sus intentos y el botón de pagar
//   GET  /payments/bookings/{id}/qr.png QR de la reserva confirmada
//   POST /payments/bookings/{id}/start  abre un intento y manda al turista a la página de pago
//   GET  /payments/return               adonde vuelve el turista después de pagar: consulta el resultado
//   POST /payments/bookings/{id}/cancel cancela la reserva confirmada según la política de la actividad
// PaymentService verifica en cada pedido que la reserva sea del turista logueado
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private static final String BOOKING_VIEW = "payments/booking";
    private static final String ERROR_VIEW = "payments/error";

    private final PaymentService paymentService;
    private final PaymentConfirmationService paymentConfirmationService;
    private final BookingCancellationService bookingCancellationService;
    private final BookingQrService qrService;
    private final UserRepository userRepository;

    public PaymentController(PaymentService paymentService,
                             PaymentConfirmationService paymentConfirmationService,
                             BookingCancellationService bookingCancellationService,
                             BookingQrService qrService,
                             UserRepository userRepository) {
        this.paymentService = paymentService;
        this.paymentConfirmationService = paymentConfirmationService;
        this.bookingCancellationService = bookingCancellationService;
        this.qrService = qrService;
        this.userRepository = userRepository;
    }

    @GetMapping("/bookings/{bookingId}")
    public String showBooking(@PathVariable("bookingId") Long bookingId, Authentication authentication, Model model) {
        Long touristId = touristId(authentication);
        // Se consultan los pagos en proceso, por si el turista pagó y la vuelta desde la página de pago no funcionó
        paymentService.syncPendingPayments(bookingId, touristId);
        BookingPaymentDTO payment = paymentService.getBookingPayment(bookingId, touristId);
        // Si el pago se aprobó pero la confirmación no llegó a hacerse (por ejemplo, se cortó la conexión
        // al volver de la página de pago), se completa ahora. Si la reserva ya venció, se devuelve el dinero
        boolean paid = payment.getAttempts().stream().anyMatch(attempt -> attempt.getStatus() == PaymentStatus.APPROVED);
        boolean unresolved = payment.getStatus() == BookingStatus.PENDING || payment.getStatus() == BookingStatus.EXPIRED;
        if (paid && unresolved && paymentConfirmationService.confirmOrRefund(bookingId) != PaymentConfirmationService.Outcome.NOT_PAID) {
            payment = paymentService.getBookingPayment(bookingId, touristId);
        }
        model.addAttribute("payment", payment);
        return BOOKING_VIEW;
    }

    // Imagen del QR de una reserva confirmada (la misma que llega por mail). Solo para el turista dueño
    @GetMapping(value = "/bookings/{bookingId}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> bookingQr(@PathVariable("bookingId") Long bookingId, Authentication authentication) {
        BookingPaymentDTO payment = paymentService.getBookingPayment(bookingId, touristId(authentication));
        // El código solo viene cuando la reserva está confirmada
        if (payment.getBookingCode() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qrService.generatePng(payment.getBookingCode()));
    }

    @PostMapping("/bookings/{bookingId}/start")
    public String startPayment(@PathVariable("bookingId") Long bookingId, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            PaymentStart start = paymentService.startPayment(bookingId, touristId(authentication));
            if (start.started()) {
                return "redirect:" + start.paymentUrl();
            }
            redirectAttributes.addFlashAttribute("errorMessage", start.transaction().getFailureReason());
        } catch (PaymentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/payments/bookings/" + bookingId;
    }

    // Si la política no lo permite (no admite cancelación o ya pasó el plazo), se muestra el motivo y no cambia nada
    @PostMapping("/bookings/{bookingId}/cancel")
    public String cancelBooking(@PathVariable("bookingId") Long bookingId, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            CancellationResult result = bookingCancellationService.cancelByTourist(bookingId, touristId(authentication));
            redirectAttributes.addFlashAttribute(result.cancelled() ? "successMessage" : "errorMessage", result.message());
        } catch (PaymentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/payments/bookings/" + bookingId;
    }

    // El servicio de pagos manda de vuelta al turista con la referencia del pago.
    // No se confía en lo que diga la URL sobre el resultado: siempre se consulta al servicio
    @GetMapping("/return")
    public String paymentReturn(@RequestParam(name = "external_reference", required = false) String externalReference,
                                Authentication authentication, RedirectAttributes redirectAttributes) {
        PaymentTransaction transaction = paymentService.syncPaymentByReference(externalReference,
                touristId(authentication));
        Long bookingId = transaction.getBooking().getId();

        if (transaction.getStatus() == PaymentStatus.APPROVED) {
            if (paymentConfirmationService.confirmOrRefund(bookingId) == PaymentConfirmationService.Outcome.CONFIRMED) {
                redirectAttributes.addFlashAttribute("successMessage", "¡Pago aprobado! Tu reserva está confirmada.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Recibimos tu pago, pero la reserva ya había "
                        + "vencido y no se pudo confirmar. Te vamos a devolver el dinero.");
            }
        } else if (transaction.getStatus() == PaymentStatus.PENDING) {
            redirectAttributes.addFlashAttribute("infoMessage", transaction.getFailureReason() != null
                    ? transaction.getFailureReason()
                    : "Tu pago está en proceso. Volvé a esta página en unos minutos para ver si se acreditó.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", (transaction.getFailureReason() != null
                    ? transaction.getFailureReason() : "El pago no se pudo completar.")
                    + " Tu reserva sigue pendiente: podés intentar de nuevo.");
        }
        return "redirect:/payments/bookings/" + bookingId;
    }

    @ExceptionHandler(PaymentException.class)
    public String handlePaymentException(PaymentException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return ERROR_VIEW;
    }

    // Solo los turistas tienen reservas. Para cualquier otro usuario, la reserva "no existe"
    private Long touristId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .filter(Tourist.class::isInstance)
                .map(User::getId)
                .orElseThrow(() -> new PaymentException("La reserva no existe."));
    }
}
