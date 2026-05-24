package io.arknights.dateorfriends.modules.user.auth.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank String account,
        @NotBlank String password,
        @NotBlank @Size(max = 128) String captchaId,
        @NotBlank @Size(max = 16) String captchaText
) {
}
