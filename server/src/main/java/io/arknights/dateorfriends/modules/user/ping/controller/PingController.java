package io.arknights.dateorfriends.modules.user.ping.controller;

import io.arknights.dateorfriends.modules.user.ping.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController("userPingController")
@RequestMapping("/user")
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    /**
     * 用户端存活探针（用于校验登录态与基础连通性）。
     * 权限要求：需要登录（Access Token 有效即可）。
     */
    @GetMapping("/ping")
    public Mono<String> ping() {
        return pingService.ping();
    }

}

