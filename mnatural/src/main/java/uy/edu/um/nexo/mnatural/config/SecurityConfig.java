package uy.edu.um.nexo.mnatural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import uy.edu.um.nexo.mnatural.security.AppUserDetailsService;
import uy.edu.um.nexo.mnatural.security.Role;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Verifica email y contraseña contra la base usando AppUserDetailsService.
    // Por defecto Spring revisa si la cuenta está bloqueada ANTES de comparar la contraseña;
    // lo invertimos para que alguien que no sabe la contraseña no pueda averiguar
    // si un email es de un prestador pendiente o rechazado
    @Bean
    public DaoAuthenticationProvider authenticationProvider(AppUserDetailsService userDetailsService,
                                                            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Rutas públicas y rutas por rol. Lo que no esté listado requiere iniciar sesión.
                // Si agregan una pantalla pública nueva (por ejemplo el portal de actividades),
                // hay que sumarla a la primera lista
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/registro/**", "/actividades/**", "/error",
                                "/css/**", "/js/**", "/images/**", "/img/**").permitAll()
                        .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/prestador/**").hasRole(Role.PROVIDER.name())
                        // Espacio del prestador (actividades de Camila)
                        .requestMatchers("/provider/**").hasRole(Role.PROVIDER.name())
                        .requestMatchers("/turista/**").hasRole(Role.TOURIST.name())
                        .anyRequest().authenticated())
                // Formulario propio en /login (LoginController). Spring procesa el POST a /login
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                // POST a /logout cierra la sesión
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        // CSRF queda activado (es el valor por defecto). Los formularios con th:action
        // ya incluyen el token automáticamente
        return http.build();
    }

    // El prestador va directo a sus actividades y el admin a su panel de prestadores. El resto
    // vuelve a la página que quería ver antes del login, o al inicio
    private AuthenticationSuccessHandler loginSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        defaultHandler.setDefaultTargetUrl("/");

        return (request, response, authentication) -> {
            boolean isProvider = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + Role.PROVIDER.name()));
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + Role.ADMIN.name()));
            if (isProvider) {
                response.sendRedirect(request.getContextPath() + "/provider/activities");
            } else if (isAdmin) {
                response.sendRedirect(request.getContextPath() + "/admin/prestadores");
            } else {
                defaultHandler.onAuthenticationSuccess(request, response, authentication);
            }
        };
    }

    // Redirige al login con un parámetro distinto según por qué falló,
    // para que la vista muestre el mensaje correspondiente
    private AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String reason;
            if (exception instanceof LockedException) {
                reason = "pending";
            } else if (exception instanceof DisabledException) {
                reason = "rejected";
            } else {
                reason = "error";
            }
            response.sendRedirect(request.getContextPath() + "/login?" + reason);
        };
    }
}
