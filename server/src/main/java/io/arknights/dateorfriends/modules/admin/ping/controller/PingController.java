package io.arknights.dateorfriends.modules.admin.ping.controller;

import io.arknights.dateorfriends.modules.admin.ping.service.PingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController("adminPingController")
@RequestMapping("/admin")
public class PingController {

    private final PingService pingService;

    public PingController(PingService pingService) {
        this.pingService = pingService;
    }

    @GetMapping("/ping")
    public Mono<String> ping() {
        return pingService.ping();
    }

}

