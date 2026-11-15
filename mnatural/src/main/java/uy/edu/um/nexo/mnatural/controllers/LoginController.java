package uy.edu.um.nexo.mnatural.controllers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import uy.edu.um.nexo.mnatural.security.LandingPage;

import java.net.URI;

// Solo muestra el formulario. El POST a /login lo procesa Spring Security
// (ver SecurityConfig), no este controller
@Controller
public class LoginController {

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    @GetMapping("/login")
    public String showLogin(Authentication authentication, HttpServletRequest request,
                            HttpServletResponse response, Model model) {
        // Si ya inició sesión no tiene sentido mostrarle el login de nuevo: se lo manda a donde
        // empieza su rol (el admin a su panel, el prestador a sus actividades, el resto al inicio)
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:" + LandingPage.urlFor(authentication);
        }
        // Un visitante que tocó "Reservar" llega acá desde /reservar/...: se le explica por qué tiene que
        // entrar y se le ofrece crear la cuenta. Después de iniciar sesión Spring Security lo devuelve a
        // la reserva (la página que pidió está guardada en su sesión, nunca viaja en la URL)
        model.addAttribute("reserveFirst", cameFromABooking(request, response));
        return "login";
    }

    // getRequest solo mira la página guardada: no la borra, así que el regreso después del login sigue funcionando
    private boolean cameFromABooking(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return false;
        }
        try {
            String path = URI.create(saved.getRedirectUrl()).getPath();
            return path != null && path.startsWith(request.getContextPath() + "/reservar/");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
