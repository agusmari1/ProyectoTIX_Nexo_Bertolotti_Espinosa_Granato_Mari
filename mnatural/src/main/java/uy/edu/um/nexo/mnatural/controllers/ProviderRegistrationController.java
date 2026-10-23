package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.PropertyAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
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

    // Si el RUT trae letras, Spring no puede convertirlo a Long y genera un error técnico
    // en inglés. Lo reemplazamos por el mismo mensaje claro de las otras validaciones del RUT.
    @InitBinder("registration")
    public void initBinder(WebDataBinder binder) {
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
                if ("rut".equals(ex.getPropertyName())) {
                    bindingResult.addError(new FieldError("registration", "rut", ex.getValue(), true,
                            new String[]{"typeMismatch.rut"}, null, ProviderRegistrationDTO.RUT_MESSAGE));
                } else {
                    super.processPropertyAccessException(ex, bindingResult);
                }
            }
        });
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
        // Solo comparo si ya escribió algo en "Repetir contraseña"; si está vacío ya sale el error de obligatorio
        String confirmPassword = registration.getConfirmPassword();
        if (registration.getPassword() != null && confirmPassword != null && !confirmPassword.isBlank()
                && !registration.getPassword().equals(confirmPassword)) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Las contraseñas no coinciden");
        }

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
