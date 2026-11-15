package uy.edu.um.nexo.mnatural.controllers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.nexo.mnatural.dto.CurrentUserDTO;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.LandingPage;

// Le pasa a todas las pantallas el usuario con la sesión iniciada, para fragments/header.html.
// Sin sesión, currentUser queda en null y el encabezado muestra "Iniciar sesión".
// El repositorio es opcional porque este advice también se carga en los @WebMvcTest de
// cualquier controller, donde no existe: ahí se usa el email como nombre
@ControllerAdvice
public class CurrentUserAdvice {

    private final ObjectProvider<UserRepository> userRepository;

    public CurrentUserAdvice(ObjectProvider<UserRepository> userRepository) {
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public CurrentUserDTO currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        String displayName = displayNameOf(authentication.getName());
        return new CurrentUserDTO(displayName, initialOf(displayName), LandingPage.roleOf(authentication));
    }

    // La primera letra, salteando símbolos. Sin ninguna letra, un ícono genérico
    private String initialOf(String name) {
        return name.codePoints()
                .filter(Character::isLetter)
                .findFirst()
                .stream()
                .mapToObj(letter -> Character.toString(letter).toUpperCase())
                .findFirst()
                .orElse("👤");
    }

    private String displayNameOf(String email) {
        UserRepository repository = userRepository.getIfAvailable();
        if (repository == null) {
            return email;
        }
        return repository.findByEmail(email).map(User::getName).orElse(email);
    }
}
