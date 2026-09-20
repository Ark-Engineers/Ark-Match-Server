package io.arknights.dateorfriends.modules.user.lmd.mapper;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LmdTransactionMapper {

    @Insert("""
            INSERT INTO `lmd_transaction` (
              user_id,
              amount,
              balance_after,
              type,
              ref_type,
              ref_id,
              description,
              trace_id,
              request_ip,
              created_by,
              created_at
            )
            VALUES (
              #{userId},
              #{amount},
              #{balanceAfter},
              #{type},
              #{refType},
              #{refId},
              #{description},
              #{traceId},
              #{requestIp},
              #{createdBy},
              #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LmdTransactionDO tx);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `lmd_transaction`
            WHERE user_id = #{userId}
              <if test="type != null and type != ''">
                AND type = #{type}
              </if>
            </script>
            """)
    long countByUser(@Param("userId") long userId, @Param("type") String type);

    @Select("""
            <script>
            SELECT
              id,
              amount,
              balance_after AS balanceAfter,
              type,
              ref_type AS refType,
              ref_id AS refId,
              description,
              created_at AS createdAt
            FROM `lmd_transaction`
            WHERE user_id = #{userId}
              <if test="type != null and type != ''">
                AND type = #{type}
              </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UserTxItem> selectListByUser(
            @Param("userId") long userId,
            @Param("type") String type,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Data
    class UserTxItem {
        private Long id;
        private Long amount;
        private Long balanceAfter;
        private String type;
        private String refType;
        private Long refId;
        private String description;
        private LocalDateTime createdAt;
    }

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `lmd_transaction` t
            WHERE 1=1
              <if test="userId != null">
                AND t.user_id = #{userId}
              </if>
              <if test="type != null and type != ''">
                AND t.type = #{type}
              </if>
              <if test="refType != null and refType != ''">
                AND t.ref_type = #{refType}
              </if>
            </script>
            """)
    long countForAdmin(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("refType") String refType
    );

    @Select("""
            <script>
            SELECT
              t.id AS id,
              t.user_id AS userId,
              t.amount AS amount,
              t.balance_after AS balanceAfter,
              t.type AS type,
              t.ref_type AS refType,
              t.ref_id AS refId,
              t.description AS description,
              t.trace_id AS traceId,
              t.request_ip AS requestIp,
              t.created_by AS createdBy,
              t.created_at AS createdAt,
              u.account AS account,
              u.nickname AS nickname
            FROM `lmd_transaction` t
            LEFT JOIN `user` u ON u.id = t.user_id
            WHERE 1=1
              <if test="userId != null">
                AND t.user_id = #{userId}
              </if>
              <if test="type != null and type != ''">
                AND t.type = #{type}
              </if>
              <if test="refType != null and refType != ''">
                AND t.ref_type = #{refType}
              </if>
            ORDER BY t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminTxItem> selectListForAdmin(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("refType") String refType,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Data
    class AdminTxItem {
        private Long id;
        private Long userId;
        private Long amount;
        private Long balanceAfter;
        private String type;
        private String refType;
        private Long refId;
        private String description;
        private String traceId;
        private String requestIp;
        private Long createdBy;
        private LocalDateTime createdAt;
        private String account;
        private String nickname;
    }

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM `lmd_transaction`
            WHERE user_id = #{userId}
            """)
    long sumByUser(@Param("userId") long userId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM `lmd_transaction`
            WHERE type = #{type} AND ref_type = #{refType} AND ref_id = #{refId}
            """)
    long sumByTypeAndRef(
            @Param("type") String type,
            @Param("refType") String refType,
            @Param("refId") long refId
    );

    @Select("""
            SELECT
              w.user_id AS userId,
              w.balance AS balance,
              COALESCE(t.total, 0) AS ledgerSum
            FROM `user_wallet` w
            LEFT JOIN (
              SELECT user_id, SUM(amount) AS total
              FROM `lmd_transaction`
              GROUP BY user_id
            ) t ON t.user_id = w.user_id
            WHERE w.balance <> COALESCE(t.total, 0)
            ORDER BY w.user_id ASC
            """)
    List<MismatchItem> selectMismatchWallets();

    @Data
    class MismatchItem {
        private Long userId;
        private Long balance;
        private Long ledgerSum;
    }
}
