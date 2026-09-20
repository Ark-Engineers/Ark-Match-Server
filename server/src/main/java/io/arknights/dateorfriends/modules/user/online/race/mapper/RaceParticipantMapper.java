package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RaceParticipantMapper {

    @Insert("""
            <script>
            INSERT INTO horse_race_participant(race_id, sort_no, spine_asset_id, asset_key, name, type)
            VALUES
            <foreach collection="list" item="p" separator=",">
              (#{p.raceId}, #{p.sortNo}, #{p.spineAssetId}, #{p.assetKey}, #{p.name}, #{p.type})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<RaceParticipantDO> list);

    @Select("SELECT * FROM horse_race_participant WHERE race_id=#{raceId} ORDER BY sort_no")
    List<RaceParticipantDO> selectByRaceId(@Param("raceId") long raceId);

    @Select("SELECT * FROM horse_race_participant WHERE id=#{id}")
    RaceParticipantDO selectById(@Param("id") long id);
}
