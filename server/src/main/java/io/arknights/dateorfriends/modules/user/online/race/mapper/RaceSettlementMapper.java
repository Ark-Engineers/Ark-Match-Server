package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RaceSettlementMapper {

    @Insert("""
            <script>
            INSERT INTO horse_race_settlement(round_id, user_id, asset_id, rank_no, bet_amount, payout)
            VALUES
            <foreach collection="list" item="s" separator=",">
              (#{s.roundId}, #{s.userId}, #{s.assetId}, #{s.rankNo}, #{s.betAmount}, #{s.payout})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<RaceSettlementDO> list);

    @Select("SELECT * FROM horse_race_settlement WHERE round_id=#{roundId} ORDER BY rank_no, id")
    List<RaceSettlementDO> selectByRoundId(@Param("roundId") long roundId);

    @Select("SELECT COALESCE(SUM(payout), 0) FROM horse_race_settlement WHERE round_id=#{roundId}")
    long sumPayoutByRound(@Param("roundId") long roundId);
}
