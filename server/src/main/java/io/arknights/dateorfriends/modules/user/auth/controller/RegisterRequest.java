package io.arknights.dateorfriends.modules.user.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String emailCode,
        @NotBlank String password,
        @NotBlank String confirmPassword,
        @NotBlank String nickname
) {
    @AssertTrue(message = "两次密码不一致")
    public boolean isPasswordConfirmed() {
        if (password == null || confirmPassword == null) return false;
        var a = password.trim();
        var b = confirmPassword.trim();
        return a.equals(b);
    }
}
