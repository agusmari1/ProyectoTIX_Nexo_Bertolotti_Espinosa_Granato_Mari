package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.nexo.mnatural.dto.TouristRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.DocumentType;
import uy.edu.um.nexo.mnatural.exception.RegistrationException;
import uy.edu.um.nexo.mnatural.service.TouristService;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/registro/turista")
public class TouristRegistrationController {

    private static final String FORM_VIEW = "registro-turista";

    private final TouristService touristService;

    public TouristRegistrationController(TouristService touristService) {
        this.touristService = touristService;
    }

    // Opciones del <select> de tipo de documento (valor del enum -> texto visible)
    @ModelAttribute("documentTypes")
    public Map<DocumentType, String> documentTypes() {
        Map<DocumentType, String> options = new LinkedHashMap<>();
        options.put(DocumentType.CI, "Cédula de identidad");
        options.put(DocumentType.PASSPORT, "Pasaporte");
        options.put(DocumentType.DNI, "DNI");
        options.put(DocumentType.OTHER, "Otro");
        return options;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("registration", new TouristRegistrationDTO());
        return FORM_VIEW;
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("registration") TouristRegistrationDTO registration,
                           BindingResult bindingResult,
                           Model model) {
        // Solo se compara si las dos están completas; si falta alguna ya hay un error de @NotBlank
        if (registration.getPassword() != null && registration.getConfirmPassword() != null
                && !registration.getConfirmPassword().isBlank()
                && !registration.getPassword().equals(registration.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "passwordMismatch", "Las contraseñas no coinciden");
        }

        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            touristService.register(registration);
        } catch (RegistrationException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return FORM_VIEW;
        }

        // Post/Redirect/Get: evita que recargar la página reenvíe el formulario
        return "redirect:/registro/turista/exito";
    }

    @GetMapping("/exito")
    public String showSuccess() {
        return "registro-turista-exito";
    }
}
