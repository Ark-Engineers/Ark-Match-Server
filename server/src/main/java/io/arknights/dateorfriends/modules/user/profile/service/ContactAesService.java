package io.arknights.dateorfriends.modules.user.profile.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ContactAesService {

    private final byte[] key32;
    private final SecureRandom random = new SecureRandom();

    public ContactAesService(
            @Value("${app.profile.contact-aes-key:}") String aesKeyBase64,
            @Value("${app.verify.secret:dev-verify-secret-change-me}") String secret
    ) {
        var raw = aesKeyBase64 == null ? "" : aesKeyBase64.trim();
        if (!raw.isBlank()) {
            this.key32 = Base64.getDecoder().decode(raw);
        } else {
            this.key32 = sha256(secret == null ? "" : secret);
        }
    }

    public String encryptToBase64(String plaintext) {
        var text = plaintext == null ? "" : plaintext.trim();
        if (text.isBlank()) return null;
        if (key32 == null || key32.length != 32) return null;
        try {
            var iv = new byte[12];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            var encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            var out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            return null;
        }
    }

    public String decryptFromBase64(String cipherBase64) {
        var raw = cipherBase64 == null ? "" : cipherBase64.trim();
        if (raw.isBlank()) return null;
        if (key32 == null || key32.length != 32) return null;
        try {
            var all = Base64.getDecoder().decode(raw);
            if (all.length < 13) return null;
            var iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            var encrypted = new byte[all.length - 12];
            System.arraycopy(all, 12, encrypted, 0, encrypted.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            var decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] sha256(String text) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[32];
        }
    }
}

