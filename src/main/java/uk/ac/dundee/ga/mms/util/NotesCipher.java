package uk.ac.dundee.ga.mms.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for meeting notes and MFA secrets (Section 9.3 "At rest").
 * The key comes from the NOTES_ENCRYPTION_KEY config var (base64, 32 bytes).
 * A static holder is used so that the JPA attribute converter can reach it.
 */
@Slf4j
public final class NotesCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKey key;

    private NotesCipher() {
    }

    public static void init(String base64Key) {
        try {
            if (base64Key == null || base64Key.isBlank()) {
                log.warn("NOTES_ENCRYPTION_KEY is not set - using an insecure development key. NEVER do this in production.");
                byte[] dev = MessageDigest.getInstance("SHA-256").digest("mms-dev-only-key".getBytes(StandardCharsets.UTF_8));
                key = new SecretKeySpec(dev, "AES");
                return;
            }
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalArgumentException("NOTES_ENCRYPTION_KEY must decode to 32 bytes (AES-256)");
            }
            key = new SecretKeySpec(raw, "AES");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static SecretKey key() {
        if (key == null) {
            init(System.getenv("NOTES_ENCRYPTION_KEY"));
        }
        return key;
    }

    public static String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored; // legacy / unencrypted value
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, in, 0, IV_LEN));
            byte[] pt = c.doFinal(in, IV_LEN, in.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed - check NOTES_ENCRYPTION_KEY", e);
        }
    }
}
