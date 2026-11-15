package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.support.RequestContextUtils;
import uy.edu.um.nexo.mnatural.dto.ReviewForm;
import uy.edu.um.nexo.mnatural.entities.Review;
import uy.edu.um.nexo.mnatural.entities.ReviewStatus;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.service.ReviewService;

// Reseñas del turista: dejar la de una reserva, ver la que ya dejó y eliminarla (ESPECIFICACION.md, sección 5).
// /reviews/** ya está en SecurityConfig para el rol TURISTA. ReviewService verifica en cada pedido que la reserva sea
// del turista, esté confirmada y la actividad haya terminado
@Controller
@RequestMapping("/reviews")
public class TouristReviewController {

    private static final String FORM_VIEW = "reviews/form";
    private static final String BOOKINGS_URL = "redirect:/turista/reservas";

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public TouristReviewController(ReviewService reviewService, UserRepository userRepository) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    // Si ya dejó su reseña se la muestra (con el botón de eliminarla); si no, el formulario para dejarla
    @GetMapping("/bookings/{bookingId}")
    public String show(@PathVariable("bookingId") Long bookingId, Authentication authentication, Model model) {
        Long touristId = touristId(authentication);
        Review existing = reviewService.findOwn(bookingId, touristId).orElse(null);
        if (existing != null) {
            model.addAttribute("target", reviewService.describe(bookingId, touristId));
            model.addAttribute("existing", existing);
            model.addAttribute("deleted", existing.getStatus() == ReviewStatus.DELETED);
            return FORM_VIEW;
        }
        model.addAttribute("target", reviewService.targetFor(bookingId, touristId));
        model.addAttribute("review", new ReviewForm());
        return FORM_VIEW;
    }

    @PostMapping("/bookings/{bookingId}")
    public String create(@PathVariable("bookingId") Long bookingId,
                         @Valid @ModelAttribute("review") ReviewForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Long touristId = touristId(authentication);
        if (bindingResult.hasErrors()) {
            model.addAttribute("target", reviewService.targetFor(bookingId, touristId));
            return FORM_VIEW;
        }
        reviewService.create(bookingId, touristId, form.getRating(), form.getComment());
        redirectAttributes.addFlashAttribute("successMessage", "¡Gracias! Tu reseña ya está publicada en la actividad.");
        return BOOKINGS_URL;
    }

    // Eliminar no borra: la reseña deja de verse y de contar para el promedio
    @PostMapping("/{reviewId}/delete")
    public String delete(@PathVariable("reviewId") Long reviewId, Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        reviewService.delete(reviewId, touristId(authentication));
        redirectAttributes.addFlashAttribute("successMessage",
                "Eliminaste tu reseña. Ya no se muestra y no cuenta para el promedio de la actividad.");
        return BOOKINGS_URL;
    }

    // Una reseña que no se puede hacer (reserva ajena, actividad sin terminar, etc.): vuelve a "Mis reservas" con el motivo
    @ExceptionHandler(ReviewException.class)
    public String handleReviewException(ReviewException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return BOOKINGS_URL;
    }

    private Long touristId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .filter(Tourist.class::isInstance)
                .map(User::getId)
                .orElseThrow(() -> new ReviewException("La reserva no existe."));
    }
}
