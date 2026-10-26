package uy.edu.um.nexo.mnatural.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class LandingPageTest {

    private Authentication userWithRole(Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "usuario@ejemplo.test", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void anAdminStartsInThePanelOfProviders() {
        Authentication admin = userWithRole(Role.ADMIN);

        assertThat(LandingPage.urlFor(admin)).isEqualTo("/admin/prestadores");
        assertThat(LandingPage.hasOwnPanel(admin)).isTrue();
    }

    @Test
    void aProviderStartsInItsActivities() {
        Authentication provider = userWithRole(Role.PROVIDER);

        assertThat(LandingPage.urlFor(provider)).isEqualTo("/provider/activities");
        assertThat(LandingPage.hasOwnPanel(provider)).isTrue();
    }

    @Test
    void aTouristHasNoPanelAndStaysInTheHome() {
        Authentication tourist = userWithRole(Role.TOURIST);

        assertThat(LandingPage.urlFor(tourist)).isEqualTo("/");
        assertThat(LandingPage.hasOwnPanel(tourist)).isFalse();
    }

    @Test
    void withoutASessionItStaysInTheHome() {
        assertThat(LandingPage.urlFor(null)).isEqualTo("/");
        assertThat(LandingPage.hasOwnPanel(null)).isFalse();
    }
}
