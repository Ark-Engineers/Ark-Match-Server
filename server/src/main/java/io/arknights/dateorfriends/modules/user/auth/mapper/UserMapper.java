package io.arknights.dateorfriends.modules.user.auth.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT
              id,
              account,
              email,
              password_hash AS passwordHash,
              role,
              nickname,
              avatar_url AS avatarUrl,
              status,
              email_verified_at AS emailVerifiedAt,
              last_login_at AS lastLoginAt,
              last_login_ip AS lastLoginIp,
              login_fail_count AS loginFailCount,
              locked_until AS lockedUntil,
              created_at AS createdAt,
              updated_at AS updatedAt,
              deleted,
              deleted_at AS deletedAt
            FROM `user`
            WHERE account = #{account}
              AND deleted = 0
            LIMIT 1
            """)
    UserDO selectByAccount(@Param("account") String account);

    @Select("""
            SELECT
              id,
              account,
              email,
              password_hash AS passwordHash,
              role,
              nickname,
              avatar_url AS avatarUrl,
              status,
              email_verified_at AS emailVerifiedAt,
              last_login_at AS lastLoginAt,
              last_login_ip AS lastLoginIp,
              login_fail_count AS loginFailCount,
              locked_until AS lockedUntil,
              created_at AS createdAt,
              updated_at AS updatedAt,
              deleted,
              deleted_at AS deletedAt
            FROM `user`
            WHERE id = #{id}
              AND deleted = 0
            LIMIT 1
            """)
    UserDO selectById(@Param("id") long id);

    @Select("""
            SELECT COUNT(1)
            FROM `user`
            WHERE role = 'ADMIN'
              AND deleted = 0
            """)
    long countAdmin();

    @Select("""
            SELECT COUNT(1)
            FROM `user`
            WHERE account = #{account}
            LIMIT 1
            """)
    long countByAccountAll(@Param("account") String account);

    @Select("""
            SELECT COUNT(1)
            FROM `user`
            WHERE email = #{email}
            LIMIT 1
            """)
    long countByEmailAll(@Param("email") String email);

    @Insert("""
            INSERT INTO `user` (
              account,
              email,
              password_hash,
              nickname
            )
            VALUES (
              #{account},
              #{email},
              #{passwordHash},
              #{nickname}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserDO user);

    @Insert("""
            INSERT INTO `user` (
              account,
              email,
              password_hash,
              role,
              nickname,
              status,
              email_verified_at,
              login_fail_count,
              locked_until,
              deleted,
              deleted_at
            )
            VALUES (
              #{account},
              #{email},
              #{passwordHash},
              'ADMIN',
              #{nickname},
              'NORMAL',
              NOW(),
              0,
              NULL,
              0,
              NULL
            )
            ON DUPLICATE KEY UPDATE
              role = 'ADMIN',
              account = VALUES(account),
              email = VALUES(email),
              password_hash = VALUES(password_hash),
              nickname = VALUES(nickname),
              status = 'NORMAL',
              email_verified_at = COALESCE(email_verified_at, VALUES(email_verified_at)),
              login_fail_count = 0,
              locked_until = NULL,
              deleted = 0,
              deleted_at = NULL
            """)
    int upsertAdmin(
            @Param("account") String account,
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("nickname") String nickname
    );

    @Update("""
            UPDATE `user`
            SET
              login_fail_count = #{loginFailCount},
              locked_until = #{lockedUntil}
            WHERE id = #{id}
            """)
    int updateLoginFailState(@Param("id") long id, @Param("loginFailCount") int loginFailCount, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Update("""
            UPDATE `user`
            SET
              login_fail_count = 0,
              locked_until = NULL,
              last_login_at = #{lastLoginAt},
              last_login_ip = #{lastLoginIp}
            WHERE id = #{id}
            """)
    int updateLoginSuccessState(@Param("id") long id, @Param("lastLoginAt") LocalDateTime lastLoginAt, @Param("lastLoginIp") String lastLoginIp);
}
