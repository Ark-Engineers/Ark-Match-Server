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
            INSERT INTO horse_race_round(race_id, round_no, status, bet_start_at, bet_end_at, race_start_at, podium_end_at)
            VALUES(#{raceId}, #{roundNo}, #{status}, #{betStartAt}, #{betEndAt}, #{raceStartAt}, #{podiumEndAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RaceRoundDO round);

    @Select("SELECT * FROM horse_race_round WHERE id=#{id}")
    RaceRoundDO selectById(@Param("id") long id);

    @Select("SELECT * FROM horse_race_round WHERE id=#{id} FOR UPDATE")
    RaceRoundDO selectByIdForUpdate(@Param("id") long id);

    @Select("SELECT * FROM horse_race_round WHERE race_id=#{raceId} ORDER BY round_no DESC LIMIT 1")
    RaceRoundDO selectCurrentByRaceId(@Param("raceId") long raceId);

    @Select("SELECT * FROM horse_race_round WHERE race_id=#{raceId} ORDER BY round_no DESC")
    List<RaceRoundDO> selectByRaceId(@Param("raceId") long raceId);

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
