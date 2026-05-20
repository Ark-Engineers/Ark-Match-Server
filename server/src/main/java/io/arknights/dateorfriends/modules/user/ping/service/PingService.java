package io.arknights.dateorfriends.modules.user.ping.service;

import reactor.core.publisher.Mono;

public interface PingService {

    Mono<String> ping();

}

