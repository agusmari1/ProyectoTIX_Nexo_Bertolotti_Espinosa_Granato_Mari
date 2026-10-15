package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.nexo.mnatural.dto.ProviderRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.entities.LegalType;
import uy.edu.um.nexo.mnatural.exception.RegistrationException;
import uy.edu.um.nexo.mnatural.service.ProviderService;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/registro/prestador")
public class ProviderRegistrationController {

    private static final String FORM_VIEW = "registro-prestador";

    private final ProviderService providerService;

    public ProviderRegistrationController(ProviderService providerService) {
        this.providerService = providerService;
    }

    // Opciones de los <select> del formulario (valor del enum -> texto visible),
    // disponibles en todas las vistas de este controller
    @ModelAttribute("documentTypes")
    public Map<DocumentType, String> documentTypes() {
        Map<DocumentType, String> options = new LinkedHashMap<>();
        options.put(DocumentType.CI, "Cédula de identidad");
        options.put(DocumentType.PASSPORT, "Pasaporte");
        options.put(DocumentType.DNI, "DNI");
        options.put(DocumentType.OTHER, "Otro");
        return options;
    }

    @ModelAttribute("legalTypes")
    public Map<LegalType, String> legalTypes() {
        Map<LegalType, String> options = new LinkedHashMap<>();
        options.put(LegalType.INDIVIDUAL, "Persona particular");
        options.put(LegalType.COMPANY, "Empresa");
        return options;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("registration", new ProviderRegistrationDTO());
        return FORM_VIEW;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("registration") ProviderRegistrationDTO registration,
                           BindingResult bindingResult,
                           Model model) {
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            providerService.register(registration);
        } catch (RegistrationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return FORM_VIEW;
        }

        // Post/Redirect/Get: evita que recargar la página reenvíe el formulario
        return "redirect:/registro/prestador/exito";
    }

    @GetMapping("/exito")
    public String showSuccess() {
        return "registro-exitoso";
    }
}
