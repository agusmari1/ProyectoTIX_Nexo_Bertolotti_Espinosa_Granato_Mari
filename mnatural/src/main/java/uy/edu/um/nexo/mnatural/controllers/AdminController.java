package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.exception.ProviderReviewException;
import uy.edu.um.nexo.mnatural.repository.AdminRepository;
import uy.edu.um.nexo.mnatural.service.AdminService;

// SecurityConfig ya exige el rol ADMIN para todo lo que empiece con /admin
@Controller
@RequestMapping("/admin/prestadores")
public class AdminController {

    private static final String LIST_URL = "redirect:/admin/prestadores";

    private final AdminService adminService;
    private final AdminRepository adminRepository;

    public AdminController(AdminService adminService, AdminRepository adminRepository) {
        this.adminService = adminService;
        this.adminRepository = adminRepository;
    }

    @GetMapping
    public String listPending(Model model) {
        model.addAttribute("providers", adminService.findPendingProviders());
        return "admin/prestadores";
    }

    @PostMapping("/{id}/aprobar")
    public String approve(@PathVariable("id") Long providerId,
                          Authentication authentication,
                          RedirectAttributes redirectAttributes) {
        try {
            Provider provider = adminService.approve(providerId, currentAdmin(authentication));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Aprobaste a " + provider.getName() + " " + provider.getLastName() + ".");
        } catch (ProviderReviewException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    @PostMapping("/{id}/rechazar")
    public String reject(@PathVariable("id") Long providerId,
                         @RequestParam(name = "motivo", required = false) String reason,
                         Authentication authentication,
                         RedirectAttributes redirectAttributes) {
        try {
            Provider provider = adminService.reject(providerId, currentAdmin(authentication), reason);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Rechazaste a " + provider.getName() + " " + provider.getLastName() + ".");
        } catch (ProviderReviewException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return LIST_URL;
    }

    // El admin que resuelve la solicitud es el que inició sesión (su email es el nombre de usuario)
    private Admin currentAdmin(Authentication authentication) {
        return adminRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el administrador con sesión iniciada: " + authentication.getName()));
    }
}
