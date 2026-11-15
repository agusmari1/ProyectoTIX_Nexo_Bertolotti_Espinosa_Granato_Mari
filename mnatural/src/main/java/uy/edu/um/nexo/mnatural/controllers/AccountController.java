package uy.edu.um.nexo.mnatural.controllers;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.PasswordChangeDTO;
import uy.edu.um.nexo.mnatural.exception.InvalidPasswordChangeException;
import uy.edu.um.nexo.mnatural.service.AccountService;

// Cambiar la propia contraseña. Sirve para los tres tipos de usuario: /cuenta/** no tiene una regla propia en
// SecurityConfig y cae en "anyRequest().authenticated()", así que solo entra quien inició sesión
@Controller
@RequestMapping("/cuenta/contrasena")
public class AccountController {

    private static final String FORM_VIEW = "cuenta/contrasena";

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("passwordChange", new PasswordChangeDTO());
        return FORM_VIEW;
    }

    @PostMapping
    public String changePassword(@Valid @ModelAttribute("passwordChange") PasswordChangeDTO passwordChange,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        // Solo comparo si ya escribió algo en "Repetir": si está vacío ya sale el error de obligatorio
        String confirm = passwordChange.getConfirmPassword();
        if (passwordChange.getNewPassword() != null && confirm != null && !confirm.isBlank()
                && !passwordChange.getNewPassword().equals(confirm)) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Las contraseñas no coinciden");
        }
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }

        try {
            accountService.changePassword(authentication.getName(), passwordChange);
        } catch (InvalidPasswordChangeException e) {
            bindingResult.rejectValue(e.getField(), "password.invalid", e.getMessage());
            return FORM_VIEW;
        }

        // Post/Redirect/Get: recargar la página no reenvía el formulario (ni las contraseñas)
        redirectAttributes.addFlashAttribute("successMessage", "Cambiaste tu contraseña.");
        return "redirect:/cuenta/contrasena";
    }
}
