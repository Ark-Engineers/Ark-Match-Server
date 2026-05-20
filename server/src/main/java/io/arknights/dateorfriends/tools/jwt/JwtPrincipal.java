package io.arknights.dateorfriends.tools.jwt;

import java.time.Instant;

public record JwtPrincipal(
        long userId,
        String role,
        int weight,
        long tokenVersion,
        String jti,
        JwtTokenType tokenType,
        Instant expiresAt
) {
}

