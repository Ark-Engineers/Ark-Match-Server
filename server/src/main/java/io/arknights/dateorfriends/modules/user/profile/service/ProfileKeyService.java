package io.arknights.dateorfriends.modules.user.profile.service;

import io.arknights.dateorfriends.tools.web.BusinessException;
import io.arknights.dateorfriends.tools.web.ErrorCode;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProfileKeyService {

    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();

    public ProfileKeyService(@Value("${app.profile.key-encryption-key:}") String keyBase64) {
        var raw = keyBase64 == null ? "" : keyBase64.trim();
        if (raw.isBlank()) {
            this.masterKey = null;
        } else {
            this.masterKey = Base64.getDecoder().decode(raw);
        }
    }

    public record KeyPairPayload(String publicKeySpkiBase64, String privateKeyPkcs8Base64, String privateKeyPkcs8EncBase64) {
    }

    public KeyPairPayload generateAndProtect() {
        if (masterKey == null || masterKey.length != 32) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "缺少密钥配置");
        }
        try {
            var gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            var kp = gen.generateKeyPair();
            var pubSpki = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            var privPkcs8 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
            var enc = encryptAesGcmToBase64(kp.getPrivate().getEncoded());
            return new KeyPairPayload(pubSpki, privPkcs8, enc);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "密钥生成失败");
        }
    }

    public String decryptPrivateKeyPkcs8Base64(String privateKeyPkcs8EncBase64) {
        if (masterKey == null || masterKey.length != 32) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "缺少密钥配置");
        }
        var raw = privateKeyPkcs8EncBase64 == null ? "" : privateKeyPkcs8EncBase64.trim();
        if (raw.isBlank()) return null;
        try {
            var bytes = decryptAesGcmFromBase64(raw);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "密钥解密失败");
        }
    }

    public PrivateKey loadPrivateKeyFromPkcs8Base64(String privateKeyPkcs8Base64) {
        var raw = privateKeyPkcs8Base64 == null ? "" : privateKeyPkcs8Base64.trim();
        if (raw.isBlank()) return null;
        try {
            var bytes = Base64.getDecoder().decode(raw);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public String decryptContactBase64(PrivateKey privateKey, String cipherBase64) {
        if (privateKey == null) return null;
        var raw = cipherBase64 == null ? "" : cipherBase64.trim();
        if (raw.isBlank()) return null;
        try {
            var cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            var decrypted = cipher.doFinal(Base64.getDecoder().decode(raw));
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private String encryptAesGcmToBase64(byte[] plaintext) throws Exception {
        var iv = new byte[12];
        random.nextBytes(iv);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
        var encrypted = cipher.doFinal(plaintext);
        var out = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private byte[] decryptAesGcmFromBase64(String cipherBase64) throws Exception {
        var all = Base64.getDecoder().decode(cipherBase64);
        if (all.length < 13) throw new IllegalArgumentException("bad_cipher");
        var iv = new byte[12];
        System.arraycopy(all, 0, iv, 0, 12);
        var encrypted = new byte[all.length - 12];
        System.arraycopy(all, 12, encrypted, 0, encrypted.length);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
        return cipher.doFinal(encrypted);
    }
}

