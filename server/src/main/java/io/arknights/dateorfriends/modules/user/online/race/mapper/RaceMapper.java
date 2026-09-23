package io.arknights.dateorfriends.modules.user.online.race.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RaceMapper {

    @Insert("""
            INSERT INTO horse_race(room_id, name, status, session_type, total_rounds, participant_mode,
                bet_duration_seconds, pre_race_duration_seconds, race_duration_seconds, podium_duration_seconds, created_by)
            VALUES(#{roomId}, #{name}, #{status}, #{sessionType}, #{totalRounds}, #{participantMode},
                #{betDurationSeconds}, #{preRaceDurationSeconds}, #{raceDurationSeconds}, #{podiumDurationSeconds}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RaceDO race);

    @Select("SELECT * FROM horse_race WHERE id=#{id}")
    RaceDO selectById(@Param("id") long id);

    @Select("SELECT * FROM horse_race WHERE id=#{raceId} FOR UPDATE")
    RaceDO selectByIdForUpdate(@Param("raceId") long raceId);

    @Insert("""
            INSERT INTO horse_race_control_log(race_id, round_id, admin_id, action, payload)
            VALUES(#{raceId}, #{roundId}, #{adminId}, #{action}, #{payload})
            """)
    int insertControlLog(
            @Param("raceId") long raceId,
            @Param("roundId") Long roundId,
            @Param("adminId") long adminId,
            @Param("action") String action,
            @Param("payload") String payload
    );

    @Select("""
            SELECT * FROM horse_race
            WHERE room_id=#{roomId} AND status=#{status}
            ORDER BY id DESC LIMIT 1
            """)
    RaceDO selectByRoomIdAndStatus(@Param("roomId") String roomId, @Param("status") String status);

    @Select("SELECT * FROM horse_race WHERE status='ACTIVE'")
    List<RaceDO> selectActive();

    @Update("""
            UPDATE horse_race SET bet_duration_seconds=#{betDurationSeconds}, pre_race_duration_seconds=#{preRaceDurationSeconds},
                race_duration_seconds=#{raceDurationSeconds}, podium_duration_seconds=#{podiumDurationSeconds}
            WHERE id=#{id} AND status='ACTIVE'
            """)
    int updateDurations(RaceDO race);

    @Select("SELECT COUNT(1) FROM horse_race")
    long countAll();

    @Select("SELECT COUNT(1) FROM horse_race WHERE status='ACTIVE'")
    long countActive();

    @Update("UPDATE horse_race SET status=#{status} WHERE id=#{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
