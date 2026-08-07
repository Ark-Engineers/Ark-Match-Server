package io.arknights.dateorfriends.modules.user.match.mapper;

import java.time.LocalDate;
import lombok.Data;

@Data
public class MatchCandidateDO {
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String lastLoginIp;
    private String regionIp;
    private LocalDate birthday;
    private Integer birthdayVisible;
    private String tagsJson;
}

