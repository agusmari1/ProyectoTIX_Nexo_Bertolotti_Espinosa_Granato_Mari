package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.servlet.support.RequestContextUtils;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.service.ReviewService;

// "Reseñas": el prestador ve las reseñas de sus actividades y responde públicamente (ESPECIFICACION.md, sección 5).
// Puede responder pero no eliminar: no hay ninguna ruta para eso. /provider/** ya está en SecurityConfig (rol PROVIDER)
@Controller
@RequestMapping("/provider/reviews")
public class ProviderReviewReplyController {

    private static final String LIST_URL = "redirect:/provider/reviews";

    private final ReviewService reviewService;

    public ProviderReviewReplyController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute("reviews", reviewService.findForProvider(authentication.getName()));
        return "provider/reviews";
    }

    @PostMapping("/{reviewId}/reply")
    public String reply(@PathVariable("reviewId") Long reviewId,
                        @RequestParam(name = "reply", required = false) String reply,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        reviewService.reply(reviewId, authentication.getName(), reply);
        redirectAttributes.addFlashAttribute("successMessage", "Tu respuesta ya se muestra en la actividad.");
        return LIST_URL;
    }

    @ExceptionHandler(ReviewException.class)
    public String handleReviewException(ReviewException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return LIST_URL;
    }
}
