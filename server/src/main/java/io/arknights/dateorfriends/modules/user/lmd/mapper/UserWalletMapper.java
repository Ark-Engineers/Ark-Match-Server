package io.arknights.dateorfriends.modules.user.lmd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserWalletMapper {

    @Insert("""
            INSERT IGNORE INTO `user_wallet` (user_id)
            VALUES (#{userId})
            """)
    int ensureRow(@Param("userId") long userId);

    @Update("""
            UPDATE `user_wallet`
            SET
              balance = balance + #{amount},
              updated_at = NOW()
            WHERE user_id = #{userId}
              AND balance + #{amount} >= 0
            """)
    int addBalance(@Param("userId") long userId, @Param("amount") long amount);

    @Select("""
            SELECT
              id,
              user_id AS userId,
              balance,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `user_wallet`
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    UserWalletDO selectByUserId(@Param("userId") long userId);

    @Select("""
            SELECT
              id,
              user_id AS userId,
              balance,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM `user_wallet`
            WHERE user_id = #{userId}
            LIMIT 1
            FOR UPDATE
            """)
    UserWalletDO selectByUserIdForUpdate(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT
              user_id AS userId,
              balance
            FROM `user_wallet`
            WHERE user_id IN
            <foreach item="userId" collection="userIds" open="(" separator="," close=")">
              #{userId}
            </foreach>
            </script>
            """)
    java.util.List<UserWalletDO> selectByUserIds(@Param("userIds") java.util.List<Long> userIds);

    @Select("""
            SELECT COUNT(1)
            FROM `user_wallet`
            """)
    long countAll();
}
