package io.arknights.dateorfriends.tools.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_WEIGHT = "weight";
    private static final String CLAIM_VERSION = "ver";
    private static final String CLAIM_TYPE = "typ";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(normalizeSecret(properties.getSecret()));
    }

    public String createAccessToken(long userId, String role, int weight, long tokenVersion) {
        return createToken(JwtTokenType.ACCESS, Duration.ofSeconds(properties.getAccessTtlSeconds()), userId, role, weight, tokenVersion);
    }

    public String createRefreshToken(long userId, String role, int weight, long tokenVersion) {
        return createToken(JwtTokenType.REFRESH, Duration.ofSeconds(properties.getRefreshTtlSeconds()), userId, role, weight, tokenVersion);
    }

    public JwtPrincipal parseAndValidate(String token, JwtTokenType expectedType) {
        var claims = parseClaims(token);
        var type = parseType(claims);
        if (type != expectedType) {
            throw new IllegalArgumentException("invalid token type");
        }
        var userId = Long.parseLong(claims.getSubject());
        var role = claims.get(CLAIM_ROLE, String.class);
        var weight = ((Number) claims.get(CLAIM_WEIGHT)).intValue();
        var ver = ((Number) claims.get(CLAIM_VERSION)).longValue();
        var jti = claims.getId();
        var exp = claims.getExpiration().toInstant();
        return new JwtPrincipal(userId, role, weight, ver, jti, type, exp);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String createToken(JwtTokenType type, Duration ttl, long userId, String role, int weight, long tokenVersion) {
        var now = Instant.now();
        var exp = now.plus(ttl);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(Long.toString(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_WEIGHT, weight)
                .claim(CLAIM_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, type.name())
                .signWith(key)
                .compact();
    }

    private JwtTokenType parseType(Claims claims) {
        var typ = claims.get(CLAIM_TYPE, String.class);
        return JwtTokenType.valueOf(typ);
    }

    private byte[] normalizeSecret(String secret) {
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
