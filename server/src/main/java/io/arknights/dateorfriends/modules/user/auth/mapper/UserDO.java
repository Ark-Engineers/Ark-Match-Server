package io.arknights.dateorfriends.modules.user.auth.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserDO {

    private Long id;
    private String account;
    private String email;
    private String passwordHash;
    private String role;
    private String nickname;
    private String avatarUrl;
    private String status;
    private LocalDateTime emailVerifiedAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Integer loginFailCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    private LocalDateTime deletedAt;
}
