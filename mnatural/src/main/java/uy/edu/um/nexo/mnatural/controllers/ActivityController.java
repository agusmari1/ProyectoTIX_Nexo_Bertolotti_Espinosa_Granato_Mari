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
import uy.edu.um.nexo.mnatural.dto.ActivityScheduleDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivitySchedule;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.exception.ActivityNotFoundException;
import uy.edu.um.nexo.mnatural.exception.ActivityValidationException;
import uy.edu.um.nexo.mnatural.service.ActivityService;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// Espacio del prestador: sus actividades, el alta y los horarios.
// ActivityService verifica en cada pedido que el usuario sea un prestador aprobado
// y que la actividad sea suya
@Controller
@RequestMapping("/provider/activities")
public class ActivityController {

    private static final String LIST_VIEW = "provider/activities/list";
    private static final String FORM_VIEW = "provider/activities/form";
    private static final String SCHEDULES_VIEW = "provider/activities/schedules";
    private static final String LIST_URL = "redirect:/provider/activities";
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    // Opciones del <select> de departamento (valor del enum -> texto visible),
    // también usadas en la lista para mostrar el nombre con tildes
    @ModelAttribute("departments")
    public Map<Department, String> departments() {
        Map<Department, String> options = new LinkedHashMap<>();
        options.put(Department.ARTIGAS, "Artigas");
        options.put(Department.CANELONES, "Canelones");
        options.put(Department.CERRO_LARGO, "Cerro Largo");
        options.put(Department.COLONIA, "Colonia");
        options.put(Department.DURAZNO, "Durazno");
        options.put(Department.FLORES, "Flores");
        options.put(Department.FLORIDA, "Florida");
        options.put(Department.LAVALLEJA, "Lavalleja");
        options.put(Department.MALDONADO, "Maldonado");
        options.put(Department.MONTEVIDEO, "Montevideo");
        options.put(Department.PAYSANDU, "Paysandú");
        options.put(Department.RIO_NEGRO, "Río Negro");
        options.put(Department.RIVERA, "Rivera");
        options.put(Department.ROCHA, "Rocha");
        options.put(Department.SALTO, "Salto");
        options.put(Department.SAN_JOSE, "San José");
        options.put(Department.SORIANO, "Soriano");
        options.put(Department.TACUAREMBO, "Tacuarembó");
        options.put(Department.TREINTA_Y_TRES, "Treinta y Tres");
        return options;
    }

    // Si un campo numérico o de fecha trae algo que no se puede convertir, Spring genera
    // un error técnico en inglés. Lo reemplazamos por un mensaje claro
    @InitBinder({"activity", "schedule"})
    public void initBinder(WebDataBinder binder) {
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
                String field = ex.getPropertyName();
                String message = "startDateTime".equals(field)
                        ? "Ingresá una fecha y hora válidas."
                        : "Ingresá un número válido.";
                bindingResult.addError(new FieldError(bindingResult.getObjectName(), field, ex.getValue(), true,
                        new String[]{"typeMismatch." + field}, null, message));
            }
        });
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute("activities", activityService.findActivitiesOf(authentication.getName()));
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String showForm(Authentication authentication, Model model) {
        model.addAttribute("activity", new ActivityDTO());
        addFormOptions(authentication, model);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("activity") ActivityDTO activity,
                         BindingResult bindingResult,
                         Authentication authentication,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Activity created = activityService.create(authentication.getName(), activity);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Publicaste \"" + created.getName() + "\". Ahora agregale fechas y horarios.");
                // Post/Redirect/Get: evita que recargar la página reenvíe el formulario
                return "redirect:/provider/activities/" + created.getId() + "/schedules";
            } catch (ActivityValidationException e) {
                addError(bindingResult, model, e);
            }
        }
        addFormOptions(authentication, model);
        return FORM_VIEW;
    }

    @GetMapping("/{id}/schedules")
    public String showSchedules(@PathVariable("id") Long activityId, Authentication authentication, Model model) {
        model.addAttribute("schedule", new ActivityScheduleDTO());
        addScheduleData(activityId, authentication, model);
        return SCHEDULES_VIEW;
    }

    @PostMapping("/{id}/schedules")
    public String addSchedule(@PathVariable("id") Long activityId,
                              @Valid @ModelAttribute("schedule") ActivityScheduleDTO schedule,
                              BindingResult bindingResult,
                              Authentication authentication,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ActivitySchedule created = activityService.addSchedule(authentication.getName(), activityId, schedule);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Agregaste el horario del " + created.getStartDateTime().format(DATE_TIME_FORMAT) + ".");
                return "redirect:/provider/activities/" + activityId + "/schedules";
            } catch (ActivityValidationException e) {
                addError(bindingResult, model, e);
            }
        }
        addScheduleData(activityId, authentication, model);
        return SCHEDULES_VIEW;
    }

    // Actividad inexistente o de otro prestador: vuelve a "mis actividades" con un aviso
    @ExceptionHandler(ActivityNotFoundException.class)
    public String handleNotFound(ActivityNotFoundException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return LIST_URL;
    }

    private void addFormOptions(Authentication authentication, Model model) {
        model.addAttribute("businesses", activityService.findBusinessesOf(authentication.getName()));
        model.addAttribute("activityTypes", activityService.findEnabledTypes());
    }

    private void addScheduleData(Long activityId, Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("activity", activityService.findOwnActivity(email, activityId));
        model.addAttribute("schedules", activityService.findSchedules(email, activityId));
    }

    // El error se muestra debajo del campo correspondiente, o arriba si es general
    private void addError(BindingResult bindingResult, Model model, ActivityValidationException e) {
        if (e.getField() == null) {
            model.addAttribute("errorMessage", e.getMessage());
        } else {
            bindingResult.rejectValue(e.getField(), "invalid", e.getMessage());
        }
    }
}
