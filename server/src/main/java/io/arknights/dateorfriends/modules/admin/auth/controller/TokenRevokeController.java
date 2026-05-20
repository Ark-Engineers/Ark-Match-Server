package io.arknights.dateorfriends.modules.admin.auth.controller;

import io.arknights.dateorfriends.modules.admin.auth.service.TokenAdminService;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController("adminTokenRevokeController")
@RequestMapping("/admin/auth")
public class TokenRevokeController {

    private final TokenAdminService tokenAdminService;

    public TokenRevokeController(TokenAdminService tokenAdminService) {
        this.tokenAdminService = tokenAdminService;
    }

    /**
     * 管理员接口：吊销指定用户的全部令牌（全端登出）。
     * 权限要求：仅 ADMIN 可访问（由全局权限拦截控制）。
     */
    @PostMapping("/revoke/{userId}")
    public Mono<ApiResponse<Void>> revokeAll(@PathVariable("userId") long userId) {
        return tokenAdminService.revokeAll(userId).thenReturn(ApiResponse.ok(null));
    }
}
