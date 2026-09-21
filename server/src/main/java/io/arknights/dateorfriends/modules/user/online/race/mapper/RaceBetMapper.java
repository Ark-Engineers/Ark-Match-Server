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
            INSERT INTO horse_race_bet(round_id, race_id, user_id, asset_id, amount, status)
            VALUES(#{roundId}, #{raceId}, #{userId}, #{assetId}, #{amount}, #{status})
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

    @Select("SELECT * FROM horse_race_bet WHERE round_id=#{roundId} AND status='ACTIVE' ORDER BY id")
    List<RaceBetDO> selectActiveByRound(@Param("roundId") long roundId);

    @Select("""
            SELECT COUNT(1) FROM horse_race_bet
            WHERE round_id=#{roundId} AND user_id=#{userId} AND asset_id=#{assetId} AND status='ACTIVE'
            """)
    long countActiveByRoundUserAsset(
            @Param("roundId") long roundId,
            @Param("userId") long userId,
            @Param("assetId") long assetId
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

    /** 展示用马匹彩池：不含已退款注单 */
    @Select("""
            SELECT COALESCE(SUM(amount), 0) FROM horse_race_bet
            WHERE round_id=#{roundId} AND asset_id=#{assetId} AND status IN ('ACTIVE','WON','LOST')
            """)
    long sumPoolByRoundAndAsset(@Param("roundId") long roundId, @Param("assetId") long assetId);
}
