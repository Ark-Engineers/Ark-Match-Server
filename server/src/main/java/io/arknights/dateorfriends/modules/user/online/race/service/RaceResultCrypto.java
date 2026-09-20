package io.arknights.dateorfriends.modules.user.online.race.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 赛马名次结果加密：AES-256-GCM（12 字节随机 IV 前置）。
 * 名次 JSON 仅以密文形式落库，结算时解密；密钥来自 app.race.secret（Base64 32 字节），
 * 未配置时回退 sha256(app.verify.secret)，生产环境必须显式配置以保证跨重启可解密。
 */
@Component
public class RaceResultCrypto {

    private final byte[] key32;
    private final SecureRandom random = new SecureRandom();

    public RaceResultCrypto(
            @Value("${app.race.secret:}") String secretBase64,
            @Value("${app.verify.secret:dev-verify-secret-change-me}") String verifySecret,
            @Value("${app.race.secret-required:false}") boolean secretRequired
    ) {
        var raw = secretBase64 == null ? "" : secretBase64.trim();
        if (!raw.isBlank()) {
            this.key32 = Base64.getDecoder().decode(raw);
        } else if (secretRequired) {
            throw new IllegalStateException("app.race.secret 未配置：生产环境必须注入 Base64 编码的 32 字节密钥（fail-closed）");
        } else {
            this.key32 = sha256(verifySecret == null ? "" : verifySecret);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            var iv = new byte[12];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("race result encrypt failed", e);
        }
    }

    public String decrypt(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isBlank()) return null;
        try {
            var all = Base64.getDecoder().decode(cipherBase64);
            if (all.length < 13) return null;
            var iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            var encrypted = new byte[all.length - 12];
            System.arraycopy(all, 12, encrypted, 0, encrypted.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key32, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("race result decrypt failed", e);
        }
    }

    /** 名次承诺：SHA-256(resultJson|seed|roundId)，赛后核验结果未被篡改 */
    public String commit(String resultJson, String seed, long roundId) {
        var raw = resultJson + "|" + seed + "|" + roundId;
        return HexFormat.of().formatHex(sha256(raw));
    }

    public boolean verifyCommit(String resultJson, String seed, long roundId, String expectedCommit) {
        if (expectedCommit == null || expectedCommit.isBlank()) return false;
        return MessageDigest.isEqual(
                commit(resultJson, seed, roundId).getBytes(StandardCharsets.UTF_8),
                expectedCommit.getBytes(StandardCharsets.UTF_8)
        );
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
