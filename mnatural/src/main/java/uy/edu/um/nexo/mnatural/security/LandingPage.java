package uy.edu.um.nexo.mnatural.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;

// Dónde empieza cada rol cuando ya tiene la sesión iniciada: el admin en su panel de prestadores y
// el prestador en sus actividades. Los turistas no tienen un panel propio, así que quedan en la
// página de inicio.
// Son los mismos destinos que usa SecurityConfig (loginSuccessHandler) después de iniciar sesión.
public final class LandingPage {

    private static final String HOME = "/";
    private static final String ADMIN_PANEL = "/admin/prestadores";
    private static final String PROVIDER_PANEL = "/provider/activities";

    private LandingPage() {
    }

    public static String urlFor(Authentication authentication) {
        if (hasRole(authentication, Role.ADMIN)) {
            return ADMIN_PANEL;
        }
        if (hasRole(authentication, Role.PROVIDER)) {
            return PROVIDER_PANEL;
        }
        return HOME;
    }

    // Si el rol tiene un panel propio al que mandarlo
    public static boolean hasOwnPanel(Authentication authentication) {
        return !HOME.equals(urlFor(authentication));
    }

    // Para mostrarle el acceso a "Mis reservas" en la página de inicio
    public static boolean isTourist(Authentication authentication) {
        return hasRole(authentication, Role.TOURIST);
    }

    // Lo que necesita el encabezado de una página pública para mostrar los accesos según la sesión:
    // con quién se inició sesión, su panel (si tiene) y si es turista. Sin sesión no agrega nada
    public static void addSessionInfo(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        model.addAttribute("loggedInAs", authentication.getName());
        if (hasOwnPanel(authentication)) {
            model.addAttribute("panelUrl", urlFor(authentication));
        }
        model.addAttribute("tourist", isTourist(authentication));
    }

    private static boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
