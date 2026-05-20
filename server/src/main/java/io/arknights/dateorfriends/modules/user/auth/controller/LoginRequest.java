package io.arknights.dateorfriends.modules.user.auth.controller;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String account,
        @NotBlank String password
) {
}
