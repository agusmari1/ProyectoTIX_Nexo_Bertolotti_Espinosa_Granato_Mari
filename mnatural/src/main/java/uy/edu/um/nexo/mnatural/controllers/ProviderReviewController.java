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
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ReviewException;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.ReviewService;

// Reseñas de una actividad del prestador logueado, con un formulario para responder cada una.
// SecurityConfig ya exige el rol PROVIDER para /provider/**; ActivityService verifica que sea el dueño
@Controller
@RequestMapping("/provider/activities/{id}/reviews")
public class ProviderReviewController {

    private static final String VIEW = "provider/activities/reviews";

    private final ReviewService reviewService;
    private final ActivityService activityService;

    public ProviderReviewController(ReviewService reviewService, ActivityService activityService) {
        this.reviewService = reviewService;
        this.activityService = activityService;
    }

    @GetMapping
    public String list(@PathVariable("id") Long activityId, Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("activity", activityService.findOwnActivity(email, activityId));
        model.addAttribute("reviews", reviewService.findForProvider(email, activityId));
        return VIEW;
    }

    @PostMapping("/{reviewId}/reply")
    public String reply(@PathVariable("id") Long activityId,
                        @PathVariable("reviewId") Long reviewId,
                        @RequestParam(name = "reply", required = false) String reply,
                        Authentication authentication,
                        RedirectAttributes redirectAttributes) {
        try {
            reviewService.reply(authentication.getName(), activityId, reviewId, reply);
            redirectAttributes.addFlashAttribute("successMessage", "Publicaste tu respuesta.");
        } catch (ReviewException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/provider/activities/" + activityId + "/reviews";
    }

    // Actividad inexistente o de otro prestador: vuelve a "mis actividades" con un aviso
    @ExceptionHandler(ActivityNotFoundException.class)
    public String handleNotFound(ActivityNotFoundException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return "redirect:/provider/activities";
    }
}
