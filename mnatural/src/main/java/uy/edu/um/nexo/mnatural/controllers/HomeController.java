package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.security.LandingPage;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        // Con la sesión iniciada la página de inicio ya no ofrece registrarse ni iniciar sesión:
        // muestra con qué usuario se está y, si el rol tiene panel propio, el acceso a ese panel
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            model.addAttribute("loggedInAs", authentication.getName());
            if (LandingPage.hasOwnPanel(authentication)) {
                model.addAttribute("panelUrl", LandingPage.urlFor(authentication));
            }
        }
        return "index";
    }
}
