package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.ActivityFilterDTO;
import uy.edu.um.nexo.mnatural.entities.Activity;
import uy.edu.um.nexo.mnatural.entities.ActivityType;
import uy.edu.um.nexo.mnatural.entities.Department;
import uy.edu.um.nexo.mnatural.repository.ActivityTypeRepository;
import uy.edu.um.nexo.mnatural.service.ActivityCatalogService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Portal público: cualquier visitante puede ver estas páginas sin iniciar sesión
// ("/actividades/**" está en la lista pública de SecurityConfig)
@Controller
public class PublicActivityController {

    private final ActivityCatalogService catalogService;
    private final ActivityTypeRepository activityTypeRepository;

    public PublicActivityController(ActivityCatalogService catalogService,
                                    ActivityTypeRepository activityTypeRepository) {
        this.catalogService = catalogService;
        this.activityTypeRepository = activityTypeRepository;
    }

    // Nombres para mostrar. Es la misma lista que usa ActivityController
    // TODO: unificarla en un solo lugar (por ejemplo, un nombre en el enum Department)
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

    // Los filtros llegan como parámetros de la URL, así el listado filtrado se puede compartir o recargar
    @GetMapping("/actividades")
    public String list(@ModelAttribute("filter") ActivityFilterDTO filter, Model model) {
        List<ActivityType> activityTypes = activityTypeRepository.findAll(Sort.by("name")).stream()
                .filter(ActivityType::isEnabled)
                .toList();

        model.addAttribute("activities", catalogService.search(filter));
        model.addAttribute("activityTypes", activityTypes);
        return "actividades/list";
    }

    @GetMapping("/actividades/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Activity> activity = catalogService.findPublished(id);
        if (activity.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "La actividad que buscás no existe o ya no está disponible.");
            return "redirect:/actividades";
        }

        model.addAttribute("activity", activity.get());
        model.addAttribute("schedules", catalogService.upcomingSchedules(activity.get()));
        return "actividades/detail";
    }
}
