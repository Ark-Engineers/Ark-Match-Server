package io.arknights.dateorfriends.modules.user.profile.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserProfileMapper {

    @Select("""
            SELECT
              user_id AS userId,
              featured_role AS featuredRole,
              signature,
              region_ip AS regionIp,
              birthday,
              birthday_visible AS birthdayVisible,
              tags_json AS tagsJson,
              contact_pubkey_spki AS contactPubkeySpki,
              contact_privkey_pkcs8_enc AS contactPrivkeyPkcs8Enc,
              qq_enc AS qqEnc,
              wechat_enc AS wechatEnc,
              email_enc AS emailEnc,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM user_profile
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    UserProfileDO selectByUserId(@Param("userId") long userId);

    @Insert("""
            INSERT INTO user_profile (
              user_id,
              featured_role,
              signature,
              region_ip,
              birthday,
              birthday_visible,
              tags_json,
              contact_pubkey_spki,
              contact_privkey_pkcs8_enc,
              qq_enc,
              wechat_enc,
              email_enc
            )
            VALUES (
              #{p.userId},
              #{p.featuredRole},
              #{p.signature},
              #{p.regionIp},
              #{p.birthday},
              #{p.birthdayVisible},
              #{p.tagsJson},
              #{p.contactPubkeySpki},
              #{p.contactPrivkeyPkcs8Enc},
              #{p.qqEnc},
              #{p.wechatEnc},
              #{p.emailEnc}
            )
            ON DUPLICATE KEY UPDATE
              featured_role = VALUES(featured_role),
              signature = VALUES(signature),
              region_ip = VALUES(region_ip),
              birthday = VALUES(birthday),
              birthday_visible = VALUES(birthday_visible),
              tags_json = VALUES(tags_json),
              contact_pubkey_spki = VALUES(contact_pubkey_spki),
              contact_privkey_pkcs8_enc = VALUES(contact_privkey_pkcs8_enc),
              qq_enc = VALUES(qq_enc),
              wechat_enc = VALUES(wechat_enc),
              email_enc = VALUES(email_enc)
            """)
    int upsert(@Param("p") UserProfileDO profile);
}
