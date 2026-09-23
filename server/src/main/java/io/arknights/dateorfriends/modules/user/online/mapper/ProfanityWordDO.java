package io.arknights.dateorfriends.modules.user.online.mapper;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ProfanityWordDO {
    private Integer id;
    private String word;
    private LocalDateTime createdAt;
}
