package JSONUtil;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Passwords {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 600_000;
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ENCODER.encodeToString(salt) + ":" + ENCODER.encodeToString(derive(password, salt));
    }

    static boolean matches(String password, String stored) {
        if (stored == null || password == null) return false;
        String[] parts = stored.split(":", -1);
        if (parts.length != 2) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] digest = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(digest, derive(password, salt));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt) {
        PBEKeySpec key = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing unavailable", e);
        } finally {
            key.clearPassword();
        }
    }
}
