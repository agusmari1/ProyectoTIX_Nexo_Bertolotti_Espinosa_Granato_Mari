package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.PropertyAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import uy.edu.um.nexo.mnatural.dto.ActivityDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityStatus;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.service.ActivityService;
import uy.edu.um.nexo.mnatural.service.SuspensionResult;

import java.math.BigDecimal;
import java.util.stream.Collectors;

// Editar y suspender una actividad ya publicada. Convive con ActivityController (alta y horarios):
// usa rutas distintas (/{id}/edit y /{id}/suspend), que /provider/** ya cubre en SecurityConfig
@Controller
@RequestMapping("/provider/activities")
public class ProviderActivityManagementController {

    private static final String EDIT_VIEW = "provider/activity-edit";
    private static final String LIST_URL = "redirect:/provider/activities";

    private final ActivityService activityService;

    public ProviderActivityManagementController(ActivityService activityService) {
        this.activityService = activityService;
    }

    // Igual que en el alta: un número mal escrito se muestra con un mensaje claro
    @InitBinder("activity")
    public void initBinder(WebDataBinder binder) {
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
                bindingResult.addError(new FieldError(bindingResult.getObjectName(), ex.getPropertyName(),
                        ex.getValue(), true, new String[]{"typeMismatch." + ex.getPropertyName()}, null,
                        "Ingresá un número válido."));
            }
        });
    }

    @GetMapping("/{id}/edit")
    public String showForm(@PathVariable("id") Long activityId, Authentication authentication, Model model) {
        Activity activity = activityService.findOwnActivity(authentication.getName(), activityId);
        model.addAttribute("activity", toDto(activity));
        addFormData(activity, model);
        return EDIT_VIEW;
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable("id") Long activityId,
                         @Valid @ModelAttribute("activity") ActivityDTO dto,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        Activity current = activityService.findOwnActivity(authentication.getName(), activityId);
        if (!bindingResult.hasErrors()) {
            BigDecimal oldPrice = current.getPricePerPerson();
            try {
                Activity updated = activityService.update(authentication.getName(), activityId, dto);
                String message = "Guardaste los cambios de \"" + updated.getName() + "\".";
                if (oldPrice != null && oldPrice.compareTo(updated.getPricePerPerson()) != 0) {
                    message += " El nuevo precio vale para las reservas nuevas: las que ya se hicieron "
                            + "mantienen el precio con el que se pagaron.";
                }
                redirectAttributes.addFlashAttribute("successMessage", message);
                return LIST_URL;
            } catch (ActivityValidationException e) {
                if (e.getField() != null) {
                    bindingResult.rejectValue(e.getField(), "activity.invalid", e.getMessage());
                } else {
                    model.addAttribute("errorMessage", e.getMessage());
                }
            }
        }
        addFormData(current, model);
        return EDIT_VIEW;
    }

    // Suspende toda la actividad: se saca del portal y se suspenden todos sus horarios futuros
    // (cada uno cancela sus reservas y reembolsa el 100 %)
    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable("id") Long activityId,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        SuspensionResult result = activityService.suspendActivity(authentication.getName(), activityId);
        String message = "Suspendiste la actividad: ya no se ve en el portal. Se cancelaron " + result.cancelledBookings()
                + " reservas y se reembolsó el 100 % de " + result.refunded() + " pagos.";
        if (result.failedRefunds() > 0) {
            message += " " + result.failedRefunds() + " reembolsos no se pudieron hacer ahora por un problema "
                    + "con el servicio de pagos: se reintentan automáticamente.";
        }
        redirectAttributes.addFlashAttribute("successMessage", message);
        return LIST_URL;
    }

    // Actividad inexistente o de otro prestador: vuelve a "mis actividades" con un aviso
    @ExceptionHandler(ActivityNotFoundException.class)
    public String handleNotFound(ActivityNotFoundException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return LIST_URL;
    }

    private void addFormData(Activity activity, Model model) {
        model.addAttribute("activityName", activity.getName());
        model.addAttribute("activityId", activity.getId());
        model.addAttribute("suspended", activity.getStatus() == ActivityStatus.SUSPENDED);
        model.addAttribute("activityTypes", activityService.findEnabledTypes());
    }

    // Llena el formulario con los datos actuales. El nombre, el lugar y el emprendimiento viajan solo para que
    // pase la validación del formulario: el service no los modifica
    private ActivityDTO toDto(Activity activity) {
        ActivityDTO dto = new ActivityDTO();
        dto.setBusinessId(activity.getBusiness().getId());
        dto.setName(activity.getName());
        dto.setDescription(activity.getDescription());
        dto.setAddress(activity.getAddress());
        dto.setDepartment(activity.getDepartment());
        dto.setLocality(activity.getLocality());
        dto.setDurationMinutes(activity.getDurationMinutes());
        dto.setPricePerPerson(activity.getPricePerPerson());
        dto.setMinAge(activity.getMinAge());
        dto.setRequirements(activity.getRequirements());
        dto.setEquipment(activity.getEquipment());
        dto.setBookingCutoffMinutes(activity.getBookingCutoffMinutes());
        dto.setCancellationAllowed(activity.isCancellationAllowed());
        dto.setCancellationCutoffHours(activity.getCancellationCutoffHours());
        dto.setRefundPercentage(activity.getRefundPercentage());
        dto.setActivityTypeIds(activity.getActivityTypes().stream()
                .map(ActivityType::getId)
                .collect(Collectors.toSet()));
        return dto;
    }
}
