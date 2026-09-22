package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RaceRoundMapper {

    @Insert("""
            INSERT INTO horse_race_round(race_id, round_no, racer_ids, status, bet_start_at,
                bet_duration_seconds, pre_race_duration_seconds, race_duration_seconds, podium_duration_seconds)
            VALUES(#{raceId}, #{roundNo}, #{racerIds}, #{status}, #{betStartAt},
                #{betDurationSeconds}, #{preRaceDurationSeconds}, #{raceDurationSeconds}, #{podiumDurationSeconds})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RaceRoundDO round);

    @Select("SELECT * FROM horse_race_round WHERE id=#{id}")
    RaceRoundDO selectById(@Param("id") long id);

    @Select("SELECT * FROM horse_race_round WHERE id=#{id} FOR UPDATE")
    RaceRoundDO selectByIdForUpdate(@Param("id") long id);

    @Select("""
            SELECT * FROM horse_race_round WHERE race_id=#{raceId}
            ORDER BY CASE WHEN status='FINISHED' THEN 1 ELSE 0 END,
                     CASE WHEN status<>'FINISHED' THEN round_no END ASC,
                     round_no DESC
            LIMIT 1
            """)
    RaceRoundDO selectCurrentByRaceId(@Param("raceId") long raceId);

    @Select("""
            <script>
            SELECT * FROM (
                SELECT *, ROW_NUMBER() OVER (
                    PARTITION BY race_id
                    ORDER BY CASE WHEN status='FINISHED' THEN 1 ELSE 0 END,
                             CASE WHEN status != 'FINISHED' THEN round_no END ASC,
                             round_no DESC
                ) AS rn
                FROM horse_race_round
                WHERE race_id IN
                <foreach item='id' collection='raceIds' open='(' separator=',' close=')'>
                    #{id}
                </foreach>
            ) t WHERE rn = 1
            </script>
            """)
    List<RaceRoundDO> selectCurrentByRaceIds(@Param("raceIds") List<Long> raceIds);

    @Select("SELECT * FROM horse_race_round WHERE race_id=#{raceId} AND round_no=#{roundNo}")
    RaceRoundDO selectByRaceAndRoundNo(@Param("raceId") long raceId, @Param("roundNo") int roundNo);

    @Select("SELECT * FROM horse_race_round WHERE race_id=#{raceId} ORDER BY round_no DESC")
    List<RaceRoundDO> selectByRaceId(@Param("raceId") long raceId);

    @Update("""
            UPDATE horse_race_round
            SET developer_controlled=1, seed=#{seed}, result_cipher=#{resultCipher}, result_commit=#{resultCommit}
            WHERE id=#{id} AND status='BETTING' AND bet_count=0
            """)
    int setPlannedRanking(
            @Param("id") long id,
            @Param("seed") String seed,
            @Param("resultCipher") String resultCipher,
            @Param("resultCommit") String resultCommit
    );

    /** 立即开赛：回写推算后的竞猜开始时间（开赛时间 = 该值 + 竞猜 + 预备），使比赛从当前时刻开始 */
    @Update("""
            UPDATE horse_race_round
            SET bet_start_at=#{betStartAt}
            WHERE id=#{id} AND status IN ('BETTING','RACING')
            """)
    int startImmediately(@Param("id") long id, @Param("betStartAt") LocalDateTime betStartAt);

    @Update("""
            UPDATE horse_race_round
            SET bet_duration_seconds=#{betDurationSeconds}, pre_race_duration_seconds=#{preRaceDurationSeconds},
                race_duration_seconds=#{raceDurationSeconds}, podium_duration_seconds=#{podiumDurationSeconds}
            WHERE id=#{id} AND race_id=#{raceId} AND status=#{status}
            """)
    int updateDurationsOnly(RaceRoundDO round);

    /** 复用空占位轮次开启下一场：不限当前状态（兼容迁移遗留的 FINISHED 占位），仅从未开赛、无下注、无结果的空轮次 */
    @Update("""
            UPDATE horse_race_round
            SET racer_ids=#{racerIds}, status='BETTING', bet_start_at=#{betStartAt}, next_lineup_json=NULL,
                bet_duration_seconds=#{betDurationSeconds}, pre_race_duration_seconds=#{preRaceDurationSeconds},
                race_duration_seconds=#{raceDurationSeconds}, podium_duration_seconds=#{podiumDurationSeconds}
            WHERE id=#{id} AND bet_count=0 AND total_pool=0 AND paid_total=0
              AND settled_at IS NULL AND seed IS NULL AND result_cipher IS NULL AND result_commit IS NULL
            """)
    int resetEmptyPlaceholder(RaceRoundDO round);

    @Update("""
            UPDATE horse_race_round
            SET racer_ids=#{racerIds}, bet_start_at=#{betStartAt}
            WHERE id=#{id} AND status='BETTING' AND bet_count=0 AND total_pool=0 AND paid_total=0
              AND settled_at IS NULL AND seed IS NULL AND result_cipher IS NULL AND result_commit IS NULL
              AND COALESCE(developer_controlled, 0)=0
              AND NOT EXISTS (SELECT 1 FROM horse_race_bet WHERE round_id=#{id})
              AND NOT EXISTS (SELECT 1 FROM horse_race_settlement WHERE round_id=#{id})
            """)
    int repairEmptyBettingLineup(RaceRoundDO round);

    @Update("UPDATE horse_race_round SET next_lineup_json=#{nextLineupJson} WHERE id=#{id}")
    int updateNextLineupJson(@Param("id") long id, @Param("nextLineupJson") String nextLineupJson);

    /** 竞猜结束：生成名次与种子，BETTING -> RACING；影响行数为0说明已被其他流程处理 */
    @Update("""
            UPDATE horse_race_round
            SET status='RACING', seed=#{seed}, result_cipher=#{resultCipher}, result_commit=#{resultCommit}
            WHERE id=#{id} AND status='BETTING'
            """)
    int markRacing(
            @Param("id") long id,
            @Param("seed") String seed,
            @Param("resultCipher") String resultCipher,
            @Param("resultCommit") String resultCommit
    );

    /** 结算完成：RACING -> PODIUM */
    @Update("""
            UPDATE horse_race_round
            SET status='PODIUM', paid_total=#{paidTotal}, settled_at=#{settledAt}
            WHERE id=#{id} AND status='RACING'
            """)
    int markPodium(
            @Param("id") long id,
            @Param("paidTotal") long paidTotal,
            @Param("settledAt") LocalDateTime settledAt
    );

    /** 领奖台结束：PODIUM -> FINISHED */
    @Update("UPDATE horse_race_round SET status='FINISHED' WHERE id=#{id} AND status='PODIUM'")
    int markFinished(@Param("id") long id);

    /** 关闭模式时把未结算轮次置为 FINISHED */
    @Update("""
            UPDATE horse_race_round SET status='FINISHED'
            WHERE id=#{id} AND status IN ('BETTING','RACING','PODIUM')
            """)
    int forceFinish(@Param("id") long id);

    /** 下注入池（下注事务内调用，配合行锁保证一致性） */
    @Update("""
            UPDATE horse_race_round
            SET total_pool = total_pool + #{amount}, bet_count = bet_count + 1
            WHERE id=#{id}
            """)
    int addPool(@Param("id") long id, @Param("amount") long amount);
}
