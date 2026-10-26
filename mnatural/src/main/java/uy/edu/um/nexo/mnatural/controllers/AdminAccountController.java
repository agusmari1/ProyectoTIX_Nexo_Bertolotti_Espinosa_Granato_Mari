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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.dto.AdminRegistrationDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.exception.AdminNotFoundException;
import uy.edu.um.nexo.mnatural.exception.EmailAlreadyRegisteredException;
import uy.edu.um.nexo.mnatural.service.AdminAccountService;
import uy.edu.um.nexo.mnatural.service.EmailService;

// Un administrador crea a otros. La contraseña del nuevo admin la genera el sistema y le llega
// solo por correo. SecurityConfig ya exige el rol ADMIN para todo lo que empiece con /admin
@Controller
@RequestMapping("/admin/administradores")
public class AdminAccountController {

    private static final String VIEW = "admin/administradores";
    private static final String LIST_URL = "redirect:/admin/administradores";

    private final AdminAccountService adminAccountService;
    private final EmailService emailService;

    public AdminAccountController(AdminAccountService adminAccountService, EmailService emailService) {
        this.adminAccountService = adminAccountService;
        this.emailService = emailService;
    }

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("newAdmin", new AdminRegistrationDTO());
        model.addAttribute("admins", adminAccountService.findAll());
        return VIEW;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("newAdmin") AdminRegistrationDTO newAdmin,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Admin created = adminAccountService.create(newAdmin);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Creaste al administrador " + created.getName() + " " + created.getLastName() + ". "
                                + deliveryMessage(created));
                // Post/Redirect/Get: evita que recargar la página reenvíe el formulario
                return LIST_URL;
            } catch (EmailAlreadyRegisteredException e) {
                bindingResult.rejectValue("email", "emailTaken", e.getMessage());
            }
        }
        model.addAttribute("admins", adminAccountService.findAll());
        return VIEW;
    }

    // Genera una contraseña nueva y se la manda por correo (por si la primera no llegó)
    @PostMapping("/{id}/nueva-contrasena")
    public String resetPassword(@PathVariable("id") Long adminId, RedirectAttributes redirectAttributes) {
        try {
            Admin admin = adminAccountService.resetPassword(adminId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Generamos una contraseña nueva para " + admin.getName() + " " + admin.getLastName() + ". "
                            + deliveryMessage(admin));
        } catch (AdminNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    // Sin el envío de correo activado el mensaje no sale, y quien lo creó tampoco conoce la
    // contraseña: se lo decimos para que sepa dónde está. Y aun con el envío activado el correo
    // se manda después de guardar y un fallo no se informa acá, así que tampoco se promete que llegó
    private String deliveryMessage(Admin admin) {
        return emailService.isEnabled()
                ? "Le mandamos la contraseña, generada al azar, a " + admin.getEmail()
                        + ". Si no le llega, usá \"Enviar nueva contraseña\"."
                : "El envío de correo está desactivado en este entorno, así que no se mandó ningún mensaje: "
                        + "la contraseña generada quedó impresa en el log de la aplicación.";
    }
}
