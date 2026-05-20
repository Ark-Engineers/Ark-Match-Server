package io.arknights.dateorfriends.modules.admin.auth.service.impl;

import io.arknights.dateorfriends.modules.admin.auth.service.TokenAdminService;
import io.arknights.dateorfriends.modules.user.auth.service.AuthService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("tokenAdminService")
public class TokenAdminServiceImpl implements TokenAdminService {

    private final AuthService authService;

    public TokenAdminServiceImpl(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<Void> revokeAll(long userId) {
        return authService.logoutAll(userId);
    }
}

