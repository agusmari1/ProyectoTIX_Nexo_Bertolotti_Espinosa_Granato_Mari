package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.nexo.mnatural.dto.PasswordChangeDTO;
import uy.edu.um.nexo.mnatural.entities.Admin;
import uy.edu.um.nexo.mnatural.exception.InvalidPasswordChangeException;
import uy.edu.um.nexo.mnatural.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test unitario: repositorio y codificador son mocks, no se toca la base de datos
class AccountServiceTest {

    private static final String EMAIL = "admin@ejemplo.test";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private AccountService service;
    private Admin user;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, passwordEncoder);
        user = new Admin();
        user.setEmail(EMAIL);
        user.setPasswordHash("hash-viejo");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("actual1234", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode("nueva12345")).thenReturn("hash-nuevo");
    }

    private PasswordChangeDTO dto(String current, String newPassword) {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setCurrentPassword(current);
        dto.setNewPassword(newPassword);
        dto.setConfirmPassword(newPassword);
        return dto;
    }

    @Test
    void withTheRightCurrentPasswordTheNewOneIsSavedAsAHash() {
        service.changePassword(EMAIL, dto("actual1234", "nueva12345"));

        assertThat(user.getPasswordHash()).isEqualTo("hash-nuevo");
        verify(userRepository).save(user);
    }

    @Test
    void aWrongCurrentPasswordChangesNothing() {
        assertThatThrownBy(() -> service.changePassword(EMAIL, dto("equivocada", "nueva12345")))
                .isInstanceOf(InvalidPasswordChangeException.class)
                .hasMessageContaining("actual")
                .satisfies(e -> assertThat(((InvalidPasswordChangeException) e).getField()).isEqualTo("currentPassword"));

        assertThat(user.getPasswordHash()).isEqualTo("hash-viejo");
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void theNewPasswordMustBeDifferentFromTheCurrentOne() {
        assertThatThrownBy(() -> service.changePassword(EMAIL, dto("actual1234", "actual1234")))
                .isInstanceOf(InvalidPasswordChangeException.class)
                .satisfies(e -> assertThat(((InvalidPasswordChangeException) e).getField()).isEqualTo("newPassword"));

        assertThat(user.getPasswordHash()).isEqualTo("hash-viejo");
        verify(userRepository, never()).save(any());
    }

    @Test
    void anUnknownAccountCannotChangeAPassword() {
        when(userRepository.findByEmail("fantasma@ejemplo.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("fantasma@ejemplo.test", dto("actual1234", "nueva12345")))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }
}
