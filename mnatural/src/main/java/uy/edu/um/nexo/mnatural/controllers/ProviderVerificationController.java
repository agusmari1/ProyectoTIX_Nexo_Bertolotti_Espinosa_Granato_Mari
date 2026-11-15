package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uy.edu.um.nexo.mnatural.service.BookingVerificationService;

// "Verificar código": el prestador escribe (o pega lo leído del QR) el código de la reserva y ve si es válida.
// /provider/** ya está cubierta por SecurityConfig (rol PROVIDER)
@Controller
@RequestMapping("/provider/verificar")
public class ProviderVerificationController {

    private final BookingVerificationService verificationService;

    public ProviderVerificationController(BookingVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    // Sin "code" se muestra solo el formulario; con "code" se verifica y se muestra el resultado
    @GetMapping
    public String verify(@RequestParam(required = false) String code, Authentication authentication, Model model) {
        if (code != null) {
            model.addAttribute("code", code.trim());
            model.addAttribute("result", verificationService.verify(authentication.getName(), code));
        }
        return "provider/verificar";
    }
}
