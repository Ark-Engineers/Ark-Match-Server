package io.arknights.dateorfriends.modules.user.ping.service.impl;

import io.arknights.dateorfriends.modules.user.ping.service.PingService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("userPingService")
public class PingServiceImpl implements PingService {

    @Override
    public Mono<String> ping() {
        return Mono.just("user pong");
    }

}

