package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.BookingRequestDTO;
import uy.edu.um.nexo.mnatural.entities.Booking;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.exception.BookingException;
import uy.edu.um.nexo.mnatural.service.BookingService;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// Reservar una fecha y horario. Solo para turistas ("/reservar/**" en SecurityConfig):
//   GET  /reservar/{scheduleId}  resumen de la fecha y formulario
//   POST /reservar/{scheduleId}  crea la reserva y manda al turista a pagar
@Controller
@RequestMapping("/reservar/{scheduleId}")
public class BookingController {

    private static final String FORM_VIEW = "reservas/form";
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingService bookingService;
    private final TouristService touristService;

    public BookingController(BookingService bookingService, TouristService touristService) {
        this.bookingService = bookingService;
        this.touristService = touristService;
    }

    // Opciones del <select> de tipo de documento (valor del enum -> texto visible)
    @ModelAttribute("documentTypes")
    public Map<DocumentType, String> documentTypes() {
        Map<DocumentType, String> options = new LinkedHashMap<>();
        options.put(DocumentType.CI, "Cédula de identidad");
        options.put(DocumentType.PASSPORT, "Pasaporte");
        options.put(DocumentType.DNI, "DNI");
        options.put(DocumentType.OTHER, "Otro");
        return options;
    }

    // La fecha que se reserva sale siempre de la URL, nunca de lo que mande el formulario
    @ModelAttribute("booking")
    public BookingRequestDTO booking(@PathVariable Long scheduleId) {
        BookingRequestDTO booking = new BookingRequestDTO();
        booking.setActivityScheduleId(scheduleId);
        return booking;
    }

    @InitBinder("booking")
    public void protectSchedule(WebDataBinder binder) {
        binder.setDisallowedFields("activityScheduleId");
    }

    @GetMapping
    public String showForm(@PathVariable Long scheduleId, @ModelAttribute("booking") BookingRequestDTO booking,
                           Authentication authentication, Model model) {
        // Su observación del registro (por ejemplo, silla de ruedas) ya aparece cargada y la puede cambiar
        booking.setHolderNotes(touristService.getByEmail(authentication.getName()).getExceptionalCondition());
        model.addAttribute("summary", bookingService.getBookingSummary(scheduleId));
        return FORM_VIEW;
    }

    @PostMapping
    public String createBooking(@PathVariable Long scheduleId,
                                @Valid @ModelAttribute("booking") BookingRequestDTO booking,
                                BindingResult bindingResult, Authentication authentication, Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("summary", bookingService.getBookingSummary(scheduleId));
            return FORM_VIEW;
        }

        try {
            Booking created = bookingService.createBooking(touristService.getByEmail(authentication.getName()), booking);
            redirectAttributes.addFlashAttribute("successMessage", "Reserva creada. Tus lugares quedan retenidos hasta las "
                    + created.getHeldUntil().format(HOUR_FORMAT) + ": pagá antes de esa hora para confirmarla.");
            return "redirect:/payments/bookings/" + created.getId();
        } catch (BookingException e) {
            // Sin lugares, fuera de plazo, edad mínima u horario no disponible: el mensaje ya es claro
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("summary", bookingService.getBookingSummary(scheduleId));
            return FORM_VIEW;
        }
    }

    // La fecha no existe: no hay nada que mostrar en el formulario
    @ExceptionHandler(BookingException.class)
    public String scheduleNotFound(BookingException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        return "redirect:/actividades";
    }
}
