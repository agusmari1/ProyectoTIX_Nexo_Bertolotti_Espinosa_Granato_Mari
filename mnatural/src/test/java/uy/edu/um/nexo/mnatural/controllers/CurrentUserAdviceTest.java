package uy.edu.um.nexo.mnatural.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import uy.edu.um.nexo.mnatural.dto.CurrentUserDTO;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.repository.UserRepository;
import uy.edu.um.nexo.mnatural.security.Role;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserAdviceTest {

    private UserRepository userRepository;
    private ObjectProvider<UserRepository> provider;
    private CurrentUserAdvice advice;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(userRepository);
        advice = new CurrentUserAdvice(provider);
    }

    @Test
    void withoutSessionThereIsNoCurrentUser() {
        Authentication anonymous = new AnonymousAuthenticationToken("clave", "anonimo",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(advice.currentUser(null)).isNull();
        assertThat(advice.currentUser(anonymous)).isNull();
    }

    @Test
    void usesTheNameInitialAndRoleOfTheUser() {
        Tourist tourist = new Tourist();
        tourist.setName("ágata");
        when(userRepository.findByEmail("ana@ejemplo.test")).thenReturn(Optional.of(tourist));

        CurrentUserDTO user = advice.currentUser(userWithRole(Role.TOURIST));

        assertThat(user.displayName()).isEqualTo("ágata");
        assertThat(user.initial()).isEqualTo("Á");
        assertThat(user.isTourist()).isTrue();
        assertThat(user.isProvider()).isFalse();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void fallsBackToTheEmailWhenTheUserIsNotFound() {
        when(userRepository.findByEmail("ana@ejemplo.test")).thenReturn(Optional.empty());

        CurrentUserDTO user = advice.currentUser(userWithRole(Role.ADMIN));

        assertThat(user.displayName()).isEqualTo("ana@ejemplo.test");
        assertThat(user.initial()).isEqualTo("A");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void initialSkipsSymbols() {
        Tourist tourist = new Tourist();
        tourist.setName("[Prueba] Turista");
        when(userRepository.findByEmail("ana@ejemplo.test")).thenReturn(Optional.of(tourist));

        assertThat(advice.currentUser(userWithRole(Role.TOURIST)).initial()).isEqualTo("P");
    }

    @Test
    void worksWithoutRepositoryInControllerSliceTests() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(advice.currentUser(userWithRole(Role.PROVIDER)).displayName()).isEqualTo("ana@ejemplo.test");
    }

    private Authentication userWithRole(Role role) {
        return UsernamePasswordAuthenticationToken.authenticated("ana@ejemplo.test", null,
                AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
    }
}
