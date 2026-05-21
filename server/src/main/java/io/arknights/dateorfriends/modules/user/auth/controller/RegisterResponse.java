package io.arknights.dateorfriends.modules.user.auth.controller;

public record RegisterResponse(
        long userId,
        String account,
        String email,
        String nickname
) {
}
