package uy.edu.um.nexo.mnatural.security;

import org.springframework.security.core.Authentication;

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

    private static boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }
}
