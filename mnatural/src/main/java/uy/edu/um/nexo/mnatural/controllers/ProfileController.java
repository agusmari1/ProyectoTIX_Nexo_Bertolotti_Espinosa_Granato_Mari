package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.dto.ProfileDTO;
import uy.edu.um.nexo.mnatural.service.ProfileService;

import java.util.Optional;

// "Mi perfil" de los tres roles. Pide sesión por la regla general de SecurityConfig (anyRequest)
@Controller
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/perfil")
    public String profile(Authentication authentication, Model model) {
        Optional<ProfileDTO> profile = profileService.findProfile(authentication.getName());
        if (profile.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("profile", profile.get());
        return "perfil";
    }
}
