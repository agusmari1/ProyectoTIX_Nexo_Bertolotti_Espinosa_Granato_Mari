package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.ActivityTypeDTO;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.exception.ActivityTypeException;
import uy.edu.um.nexo.mnatural.service.ActivityTypeService;

// El administrador carga y mantiene los tipos de actividad que después eligen los prestadores.
// SecurityConfig ya exige el rol ADMIN para todo lo que empiece con /admin
@Controller
@RequestMapping("/admin/tipos-actividad")
public class AdminActivityTypeController {

    private static final String VIEW = "admin/tipos-actividad";
    private static final String LIST_URL = "redirect:/admin/tipos-actividad";

    private final ActivityTypeService activityTypeService;

    public AdminActivityTypeController(ActivityTypeService activityTypeService) {
        this.activityTypeService = activityTypeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("newType", new ActivityTypeDTO());
        addListData(model);
        return VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newType") ActivityTypeDTO newType,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ActivityType created = activityTypeService.create(newType.getName());
                redirectAttributes.addFlashAttribute("successMessage",
                        "Agregaste el tipo \"" + created.getName() + "\".");
                return LIST_URL;
            } catch (ActivityTypeException e) {
                // El error aparece debajo del campo, junto con lo que el admin escribió
                bindingResult.rejectValue("name", "invalid", e.getMessage());
            }
        }
        addListData(model);
        return VIEW;
    }

    @PostMapping("/{id}/editar")
    public String rename(@PathVariable("id") Long typeId,
                         @RequestParam(name = "name", required = false) String name,
                         RedirectAttributes redirectAttributes) {
        try {
            ActivityType renamed = activityTypeService.rename(typeId, name);
            redirectAttributes.addFlashAttribute("successMessage",
                    "El tipo ahora se llama \"" + renamed.getName() + "\".");
        } catch (ActivityTypeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    @PostMapping("/{id}/estado")
    public String setEnabled(@PathVariable("id") Long typeId,
                             @RequestParam("enabled") boolean enabled,
                             RedirectAttributes redirectAttributes) {
        try {
            ActivityType type = activityTypeService.setEnabled(typeId, enabled);
            redirectAttributes.addFlashAttribute("successMessage", enabled
                    ? "Activaste \"" + type.getName() + "\". Los prestadores ya pueden elegirlo."
                    : "Desactivaste \"" + type.getName() + "\". Ya no se ofrece para actividades nuevas.");
        } catch (ActivityTypeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    @PostMapping("/{id}/eliminar")
    public String delete(@PathVariable("id") Long typeId, RedirectAttributes redirectAttributes) {
        try {
            activityTypeService.delete(typeId);
            redirectAttributes.addFlashAttribute("successMessage", "Borraste el tipo de actividad.");
        } catch (ActivityTypeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    private void addListData(Model model) {
        model.addAttribute("types", activityTypeService.findAll());
        model.addAttribute("usage", activityTypeService.countActivitiesByType());
    }
}
