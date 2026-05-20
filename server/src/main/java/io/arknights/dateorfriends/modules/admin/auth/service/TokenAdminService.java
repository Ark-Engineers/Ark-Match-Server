package io.arknights.dateorfriends.modules.admin.auth.service;

import reactor.core.publisher.Mono;

public interface TokenAdminService {

    Mono<Void> revokeAll(long userId);
}

