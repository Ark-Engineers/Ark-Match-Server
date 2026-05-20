package io.arknights.dateorfriends.modules.user.auth.service;

import io.arknights.dateorfriends.modules.user.auth.controller.TokenResponse;
import io.arknights.dateorfriends.tools.jwt.JwtPrincipal;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<TokenResponse> login(String account, String password, String ip);

    Mono<TokenResponse> refresh(String refreshToken);

    Mono<Void> logout(JwtPrincipal principal, String accessToken, String refreshToken);

    Mono<Void> logoutAll(long userId);
}
