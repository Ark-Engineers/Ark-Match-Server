package io.arknights.dateorfriends.modules.admin.ping.service.impl;

import io.arknights.dateorfriends.modules.admin.ping.service.PingService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("adminPingService")
public class PingServiceImpl implements PingService {

    @Override
    public Mono<String> ping() {
        return Mono.just("admin pong");
    }

}

