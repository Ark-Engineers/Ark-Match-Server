package io.arknights.dateorfriends.modules.admin.ping.service;

import reactor.core.publisher.Mono;

public interface PingService {

    Mono<String> ping();

}

