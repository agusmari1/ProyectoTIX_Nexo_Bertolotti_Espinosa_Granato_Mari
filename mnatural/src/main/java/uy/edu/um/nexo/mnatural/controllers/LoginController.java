package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.security.LandingPage;

// Solo muestra el formulario. El POST a /login lo procesa Spring Security
// (ver SecurityConfig), no este controller
@Controller
public class LoginController {

    @GetMapping("/login")
    public String showLogin(Authentication authentication) {
        // Si ya inició sesión no tiene sentido mostrarle el login de nuevo: se lo manda a donde
        // empieza su rol (el admin a su panel, el prestador a sus actividades, el resto al inicio)
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:" + LandingPage.urlFor(authentication);
        }
        return "login";
    }
}
