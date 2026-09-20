package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RaceBetMapper {

    @Insert("""
            INSERT INTO horse_race_bet(round_id, race_id, user_id, participant_id, amount, status)
            VALUES(#{roundId}, #{raceId}, #{userId}, #{participantId}, #{amount}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RaceBetDO bet);

    @Select("SELECT * FROM horse_race_bet WHERE round_id=#{roundId} AND user_id=#{userId} ORDER BY id")
    List<RaceBetDO> selectByRoundAndUser(@Param("roundId") long roundId, @Param("userId") long userId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM horse_race_bet
            WHERE round_id=#{roundId} AND user_id=#{userId} AND status IN ('ACTIVE','WON','LOST')
            """)
    long sumByRoundAndUser(@Param("roundId") long roundId, @Param("userId") long userId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM horse_race_bet
            WHERE round_id=#{roundId} AND participant_id=#{participantId} AND status='ACTIVE'
            """)
    long sumActiveByRoundAndParticipant(@Param("roundId") long roundId, @Param("participantId") long participantId);

    @Select("SELECT * FROM horse_race_bet WHERE round_id=#{roundId} AND status='ACTIVE' ORDER BY id")
    List<RaceBetDO> selectActiveByRound(@Param("roundId") long roundId);

    @Select("""
            SELECT * FROM horse_race_bet
            WHERE round_id=#{roundId} AND participant_id=#{participantId} AND status='ACTIVE'
            ORDER BY id
            """)
    List<RaceBetDO> selectActiveByRoundAndParticipant(
            @Param("roundId") long roundId,
            @Param("participantId") long participantId
    );

    @Select("""
            SELECT COUNT(1) FROM horse_race_bet
            WHERE round_id=#{roundId} AND user_id=#{userId} AND participant_id=#{participantId} AND status='ACTIVE'
            """)
    long countActiveByRoundUserParticipant(
            @Param("roundId") long roundId,
            @Param("userId") long userId,
            @Param("participantId") long participantId
    );

    /** 模式关闭时退款：race 下所有 ACTIVE 下注 */
    @Select("""
            SELECT * FROM horse_race_bet
            WHERE race_id=#{raceId} AND status='ACTIVE' ORDER BY id
            """)
    List<RaceBetDO> selectActiveByRace(@Param("raceId") long raceId);

    @Update("""
            UPDATE horse_race_bet SET status=#{status}, payout=#{payout}
            WHERE id=#{id} AND status='ACTIVE'
            """)
    int updateStatusAndPayout(
            @Param("id") long id,
            @Param("status") String status,
            @Param("payout") Long payout
    );

    @Select("SELECT * FROM horse_race_bet WHERE round_id=#{roundId} ORDER BY id")
    List<RaceBetDO> selectAllByRound(@Param("roundId") long roundId);

    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM horse_race_bet
            WHERE round_id=#{roundId} AND status IN ('ACTIVE','WON','LOST')
            """)
    long sumPoolByRound(@Param("roundId") long roundId);
}
