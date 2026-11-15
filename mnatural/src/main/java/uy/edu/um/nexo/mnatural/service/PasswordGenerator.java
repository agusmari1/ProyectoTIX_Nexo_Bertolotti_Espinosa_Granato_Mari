package uy.edu.um.nexo.mnatural.service;

import java.security.SecureRandom;

// Genera contraseñas al azar para las cuentas que no eligen la suya (el primer admin y los
// admins que crea otro admin)
public final class PasswordGenerator {

    public static final int LENGTH = 16;

    // Sin 0/O ni 1/l/I, para que se pueda copiar a mano sin confundirse
    static final String CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            password.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return password.toString();
    }
}
