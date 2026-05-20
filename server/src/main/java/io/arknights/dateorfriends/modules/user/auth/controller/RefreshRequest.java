package io.arknights.dateorfriends.modules.user.auth.controller;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank String refreshToken
) {
}

