package io.arknights.dateorfriends.modules.user.auth.controller;

public record TokenResponse(
        String tokenType,
        String accessToken,
        long accessExpiresIn,
        String refreshToken,
        long refreshExpiresIn,
        long userId,
        String role,
        int weight
) {
}

