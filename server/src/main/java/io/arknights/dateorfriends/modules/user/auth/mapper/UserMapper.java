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
            WHERE email = #{email}
              AND deleted = 0
            LIMIT 1
            """)
    UserDO selectByEmail(@Param("email") String email);

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
            WHERE last_login_ip = #{ip}
              AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    java.util.List<UserDO> selectListByLastLoginIp(@Param("ip") String ip, @Param("limit") int limit);

    @Select("""
            <script>
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
            WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
              #{id}
            </foreach>
              AND deleted = 0
            </script>
            """)
    java.util.List<UserDO> selectListByIds(@Param("ids") java.util.List<Long> ids);

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
            WHERE account LIKE CONCAT('%', #{account}, '%')
              AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    java.util.List<UserDO> selectListByAccountLike(@Param("account") String account, @Param("limit") int limit);

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
            WHERE email LIKE CONCAT('%', #{email}, '%')
              AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    java.util.List<UserDO> selectListByEmailLike(@Param("email") String email, @Param("limit") int limit);

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
            WHERE last_login_ip LIKE CONCAT('%', #{ip}, '%')
              AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    java.util.List<UserDO> selectListByLastLoginIpLike(@Param("ip") String ip, @Param("limit") int limit);

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
            WHERE nickname LIKE CONCAT('%', #{nickname}, '%')
              AND deleted = 0
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    java.util.List<UserDO> selectListByNicknameLike(@Param("nickname") String nickname, @Param("limit") int limit);

    @Select("""
            <script>
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
            WHERE deleted = 0
              <if test="role != null and role != ''">
                AND role = #{role}
              </if>
              <if test="keyword != null and keyword != ''">
                AND (
                  account LIKE CONCAT('%', #{keyword}, '%')
                  OR email LIKE CONCAT('%', #{keyword}, '%')
                  OR nickname LIKE CONCAT('%', #{keyword}, '%')
                  OR CAST(id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    java.util.List<UserDO> selectListByRoleAndKeyword(
            @Param("role") String role,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `user`
            WHERE deleted = 0
              <if test="role != null and role != ''">
                AND role = #{role}
              </if>
              <if test="keyword != null and keyword != ''">
                AND (
                  account LIKE CONCAT('%', #{keyword}, '%')
                  OR email LIKE CONCAT('%', #{keyword}, '%')
                  OR nickname LIKE CONCAT('%', #{keyword}, '%')
                  OR CAST(id AS CHAR) LIKE CONCAT('%', #{keyword}, '%')
                )
              </if>
            </script>
            """)
    long countByRoleAndKeyword(@Param("role") String role, @Param("keyword") String keyword);

    @Update("""
            UPDATE `user`
            SET status = #{status}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") long id, @Param("status") String status);

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
            WHERE role = 'SUPER_ADMIN'
              AND deleted = 0
            """)
    long countSuperAdmin();

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
              'SUPER_ADMIN',
              #{nickname},
              'NORMAL',
              NOW(),
              0,
              NULL,
              0,
              NULL
            )
            ON DUPLICATE KEY UPDATE
              role = 'SUPER_ADMIN',
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
    int upsertSuperAdmin(
            @Param("account") String account,
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("nickname") String nickname
    );

    @Update("""
            UPDATE `user`
            SET role = #{role}
            WHERE id = #{id}
            """)
    int updateRole(@Param("id") long id, @Param("role") String role);

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
