package uy.edu.um.nexo.mnatural.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    void hasTheExpectedLengthAndOnlyReadableCharacters() {
        for (int i = 0; i < 200; i++) {
            String password = PasswordGenerator.generate();

            assertThat(password).hasSize(PasswordGenerator.LENGTH);
            assertThat(password).matches("[" + PasswordGenerator.CHARS + "]+");
        }
    }

    @Test
    void doesNotRepeatPasswords() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            passwords.add(PasswordGenerator.generate());
        }

        assertThat(passwords).hasSize(200);
    }
}
