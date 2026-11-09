package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.ReviewFormDTO;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.ReviewService;

// Reseña de una reserva del turista logueado ("Calificar" desde "Mis reservas"):
//   GET  /reviews/bookings/{id}         formulario (o la reseña que ya dejó)
//   POST /reviews/bookings/{id}         guarda la calificación y el comentario
//   POST /reviews/bookings/{id}/delete  elimina su reseña
// ReviewService verifica que la reserva sea del turista logueado
@Controller
@RequestMapping("/reviews/bookings/{bookingId}")
public class TouristReviewController {

    private static final String VIEW = "reviews/booking";
    private static final String ERROR_VIEW = "reviews/error";

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public TouristReviewController(ReviewService reviewService, UserRepository userRepository) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String show(@PathVariable("bookingId") Long bookingId, Authentication authentication, Model model) {
        model.addAttribute("form", new ReviewFormDTO());
        model.addAttribute("data", reviewService.getBookingReview(bookingId, touristId(authentication)));
        return VIEW;
    }

    @PostMapping
    public String create(@PathVariable("bookingId") Long bookingId,
                         @Valid @ModelAttribute("form") ReviewFormDTO form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Long touristId = touristId(authentication);
        if (!bindingResult.hasErrors()) {
            try {
                reviewService.create(bookingId, touristId, form);
                redirectAttributes.addFlashAttribute("successMessage", "¡Gracias! Publicamos tu reseña.");
                // Post/Redirect/Get: evita que recargar la página reenvíe el formulario
                return "redirect:/reviews/bookings/" + bookingId;
            } catch (ReviewException e) {
                model.addAttribute("errorMessage", e.getMessage());
            }
        }
        model.addAttribute("data", reviewService.getBookingReview(bookingId, touristId));
        return VIEW;
    }

    @PostMapping("/delete")
    public String delete(@PathVariable("bookingId") Long bookingId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            reviewService.delete(bookingId, touristId(authentication));
            redirectAttributes.addFlashAttribute("successMessage", "Eliminaste tu reseña.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reviews/bookings/" + bookingId;
    }

    @ExceptionHandler(ReviewException.class)
    public String handleReviewException(ReviewException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return ERROR_VIEW;
    }

    // Solo los turistas tienen reservas. Para cualquier otro usuario, la reserva "no existe"
    private Long touristId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .filter(Tourist.class::isInstance)
                .map(User::getId)
                .orElseThrow(() -> new ReviewException("La reserva no existe."));
    }
}
