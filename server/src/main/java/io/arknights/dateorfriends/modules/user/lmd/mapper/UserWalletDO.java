package io.arknights.dateorfriends.modules.user.lmd.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserWalletDO {
    private Long id;
    private Long userId;
    private Long balance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
