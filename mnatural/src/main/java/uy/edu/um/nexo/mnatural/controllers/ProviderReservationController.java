package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.RequestContextUtils;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.ProviderReservationService;

// "Reservas": el prestador consulta las reservas de sus actividades (solo lectura).
// /provider/** ya está cubierta por SecurityConfig (rol PROVIDER)
@Controller
@RequestMapping("/provider/reservations")
public class ProviderReservationController {

    private final ProviderReservationService reservationService;
    private final ActivityService activityService;

    public ProviderReservationController(ProviderReservationService reservationService,
                                         ActivityService activityService) {
        this.reservationService = reservationService;
        this.activityService = activityService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long activityId,
                       @RequestParam(required = false) Long scheduleId,
                       Authentication authentication,
                       Model model) {
        String email = authentication.getName();
        model.addAttribute("activities", activityService.findActivitiesOf(email));
        // Los horarios para filtrar solo se ofrecen una vez elegida la actividad
        if (activityId != null) {
            model.addAttribute("schedules", activityService.findSchedules(email, activityId));
        }
        model.addAttribute("selectedActivityId", activityId);
        model.addAttribute("selectedScheduleId", scheduleId);
        model.addAttribute("reservations", reservationService.findReservations(email, activityId, scheduleId));
        return "provider/reservations";
    }

    // Actividad que no existe o es de otro prestador: se vuelve a la lista completa con un aviso
    @ExceptionHandler(ActivityNotFoundException.class)
    public String handleNotFound(ActivityNotFoundException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return "redirect:/provider/reservations";
    }
}
