package io.arknights.dateorfriends.modules.user.profile.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserProfileDO {
    private Long userId;
    private String featuredRole;
    private String signature;
    private String regionIp;
    private LocalDate birthday;
    private Integer birthdayVisible;
    private String tagsJson;
    private String contactPubkeySpki;
    private String contactPrivkeyPkcs8Enc;
    private String qqEnc;
    private String wechatEnc;
    private String emailEnc;
    private Integer arknightsBound;
    private Integer arknightsIsMinor;
    private String arknightsHgId;
    private String arknightsUid;
    private String arknightsNickname;
    private String arknightsChannelName;
    private LocalDateTime arknightsBoundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
