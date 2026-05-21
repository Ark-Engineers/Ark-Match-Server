package io.arknights.dateorfriends.modules.user.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String account,
        @NotBlank @Email String email,
        @NotBlank String password,
        String nickname
) {
}
