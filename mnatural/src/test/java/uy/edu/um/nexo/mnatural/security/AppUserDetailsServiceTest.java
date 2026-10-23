package uy.edu.um.nexo.mnatural.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.entities.ApprovalStatus;
import uy.edu.um.nexo.mnatural.entities.Provider;
import uy.edu.um.nexo.mnatural.entities.Tourist;
import uy.edu.um.nexo.mnatural.entities.User;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: el repositorio es un mock, así que no hace falta la base de datos
class AppUserDetailsServiceTest {

    private UserRepository userRepository;
    private AppUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AppUserDetailsService(userRepository);
    }

    @Test
    void adminGetsAdminRole() {
        givenUser(new Admin(), "admin@mnatural.uy");

        UserDetails details = service.loadUserByUsername("admin@mnatural.uy");

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void touristGetsTouristRole() {
        givenUser(new Tourist(), "turista@mail.com");

        UserDetails details = service.loadUserByUsername("turista@mail.com");

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_TOURIST");
    }

    @Test
    void approvedProviderCanLogIn() {
        givenUser(provider(ApprovalStatus.APPROVED), "prestador@mail.com");

        UserDetails details = service.loadUserByUsername("prestador@mail.com");

        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_PROVIDER");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void pendingProviderIsLocked() {
        givenUser(provider(ApprovalStatus.PENDING), "prestador@mail.com");

        UserDetails details = service.loadUserByUsername("prestador@mail.com");

        assertThat(details.isAccountNonLocked()).isFalse();
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void rejectedProviderIsDisabled() {
        givenUser(provider(ApprovalStatus.REJECTED), "prestador@mail.com");

        UserDetails details = service.loadUserByUsername("prestador@mail.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void providerWithoutApprovedStatusCannotLogIn() {
        givenUser(provider(null), "prestador@mail.com");

        UserDetails details = service.loadUserByUsername("prestador@mail.com");

        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    void emailIsTrimmedAndLowercased() {
        givenUser(new Tourist(), "turista@mail.com");

        UserDetails details = service.loadUserByUsername("  Turista@MAIL.com ");

        assertThat(details.getUsername()).isEqualTo("turista@mail.com");
    }

    @Test
    void unknownEmailThrows() {
        when(userRepository.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nadie@mail.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void invalidEmailsAreRejectedWithoutQueryingTheDatabase() {
        String tooLong = "a".repeat(250) + "@mail.com";

        for (String email : new String[]{null, "", "   ", "sin-arroba", "a@b", "con espacio@mail.com", tooLong}) {
            assertThatThrownBy(() -> service.loadUserByUsername(email))
                    .isInstanceOf(UsernameNotFoundException.class);
        }
        verify(userRepository, never()).findByEmail(anyString());
    }

    private void givenUser(User user, String email) {
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashDePrueba");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    }

    private Provider provider(ApprovalStatus status) {
        Provider provider = new Provider();
        provider.setApprovalStatus(status);
        return provider;
    }
}
