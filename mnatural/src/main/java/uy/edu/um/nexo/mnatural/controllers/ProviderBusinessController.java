package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.PropertyAccessException;
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
import uy.edu.um.nexo.mnatural.dto.BusinessDTO;
import uy.edu.um.nexo.mnatural.dto.ProviderRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.exception.BusinessNotFoundException;
import uy.edu.um.nexo.mnatural.exception.RutRequiredException;
import uy.edu.um.nexo.mnatural.service.BusinessService;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

// "Mis emprendimientos": el prestador ve, edita y agrega sus emprendimientos.
// La ruta /provider/** ya la cubre SecurityConfig (rol PROVIDER), no hace falta una regla nueva
@Controller
@RequestMapping("/provider/businesses")
public class ProviderBusinessController {

    private static final String LIST_VIEW = "provider/businesses/list";
    private static final String FORM_VIEW = "provider/businesses/form";
    private static final String LIST_URL = "redirect:/provider/businesses";

    private final BusinessService businessService;

    public ProviderBusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @ModelAttribute("legalTypes")
    public Map<LegalType, String> legalTypes() {
        Map<LegalType, String> options = new LinkedHashMap<>();
        options.put(LegalType.INDIVIDUAL, "Persona particular");
        options.put(LegalType.COMPANY, "Empresa");
        return options;
    }

    // Igual que en el registro: si el RUT trae letras, mensaje claro en vez del error técnico
    @InitBinder("business")
    public void initBinder(WebDataBinder binder) {
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
                if ("rut".equals(ex.getPropertyName())) {
                    bindingResult.addError(new FieldError("business", "rut", ex.getValue(), true,
                            new String[]{"typeMismatch.rut"}, null, ProviderRegistrationDTO.RUT_MESSAGE));
                } else {
                    super.processPropertyAccessException(ex, bindingResult);
                }
            }
        });
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("businesses", businessService.findOwn(principal.getName()));
        return LIST_VIEW;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("business", new BusinessDTO());
        model.addAttribute("formAction", "/provider/businesses");
        model.addAttribute("editing", false);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("business") BusinessDTO business,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        model.addAttribute("formAction", "/provider/businesses");
        model.addAttribute("editing", false);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            businessService.create(principal.getName(), business);
        } catch (RutRequiredException e) {
            bindingResult.rejectValue("rut", "rut.required", e.getMessage());
            return FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Emprendimiento agregado.");
        return LIST_URL;
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("business", BusinessDTO.from(businessService.findOwn(principal.getName(), id)));
        model.addAttribute("formAction", "/provider/businesses/" + id);
        model.addAttribute("editing", true);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("business") BusinessDTO business,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        model.addAttribute("formAction", "/provider/businesses/" + id);
        model.addAttribute("editing", true);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            businessService.update(principal.getName(), id, business);
        } catch (RutRequiredException e) {
            bindingResult.rejectValue("rut", "rut.required", e.getMessage());
            return FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute("successMessage", "Emprendimiento actualizado.");
        return LIST_URL;
    }

    // Emprendimiento inexistente o de otro prestador: vuelve a la lista con un aviso
    @ExceptionHandler(BusinessNotFoundException.class)
    public String handleNotFound(BusinessNotFoundException e, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("errorMessage", e.getMessage());
        return LIST_URL;
    }
}
