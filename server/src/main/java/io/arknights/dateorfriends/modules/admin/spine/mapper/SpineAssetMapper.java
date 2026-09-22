package io.arknights.dateorfriends.modules.admin.spine.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpineAssetMapper {
    @Insert("""
            INSERT INTO spine_asset(asset_key, name, type, created_by, updated_by)
            VALUES(#{assetKey}, #{name}, #{type}, #{createdBy}, #{updatedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAsset(SpineAssetDO asset);

    @Update("""
            UPDATE spine_asset
            SET name=#{name}, type=#{type}, idle_animation=#{idleAnimation}, move_animation=#{moveAnimation}, display_scale=#{displayScale}, updated_by=#{updatedBy}
            WHERE id=#{id}
            """)
    int updateAsset(
            @Param("id") long id,
            @Param("name") String name,
            @Param("type") int type,
            @Param("idleAnimation") String idleAnimation,
            @Param("moveAnimation") String moveAnimation,
            @Param("displayScale") Double displayScale,
            @Param("updatedBy") long updatedBy
    );

    @Update("""
            <script>
            UPDATE spine_asset
            <set>
              <if test="hasName">name=#{name},</if>
              <if test="hasType">type=#{type},</if>
              <if test="hasIdle">idle_animation=#{idleAnimation},</if>
              <if test="hasMove">move_animation=#{moveAnimation},</if>
              <if test="hasScale">display_scale=#{displayScale},</if>
              updated_by=#{updatedBy}
            </set>
            WHERE id=#{id}
            </script>
            """)
    int updateAssetPartial(
            @Param("id") long id,
            @Param("name") String name,
            @Param("hasName") boolean hasName,
            @Param("type") int type,
            @Param("hasType") boolean hasType,
            @Param("idleAnimation") String idleAnimation,
            @Param("hasIdle") boolean hasIdle,
            @Param("moveAnimation") String moveAnimation,
            @Param("hasMove") boolean hasMove,
            @Param("displayScale") Double displayScale,
            @Param("hasScale") boolean hasScale,
            @Param("updatedBy") long updatedBy
    );

    @Select("SELECT * FROM spine_asset WHERE id=#{id}")
    SpineAssetDO selectById(@Param("id") long id);

    @Select("""
            <script>
            SELECT * FROM spine_asset WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            </script>
            """)
    List<SpineAssetDO> selectByIds(@Param("ids") List<Long> ids);

    @Select("SELECT * FROM spine_asset WHERE asset_key=#{assetKey}")
    SpineAssetDO selectByKey(@Param("assetKey") String assetKey);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM spine_asset
            <where>
              <if test="keyword != null and keyword != ''">
                AND (asset_key LIKE CONCAT('%', #{keyword}, '%') OR name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </where>
            </script>
            """)
    long count(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT *
            FROM spine_asset
            <where>
              <if test="keyword != null and keyword != ''">
                AND (asset_key LIKE CONCAT('%', #{keyword}, '%') OR name LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="type != null">
                AND type = #{type}
              </if>
            </where>
            ORDER BY updated_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SpineAssetDO> list(
            @Param("keyword") String keyword,
            @Param("type") Integer type,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Delete("DELETE FROM spine_asset WHERE id=#{id}")
    int deleteAsset(@Param("id") long id);

    @Select("""
            <script>
            SELECT *
            FROM spine_asset
            WHERE type IN
            <foreach collection="types" item="t" open="(" separator="," close=")">
              #{t}
            </foreach>
            ORDER BY type DESC, updated_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<SpineAssetDO> listByTypes(@Param("types") List<Integer> types, @Param("limit") int limit);

    @Update("""
            UPDATE spine_asset
            SET race_count = race_count + 1,
                first_place_count = first_place_count + #{first},
                second_place_count = second_place_count + #{second},
                third_place_count = third_place_count + #{third},
                unplaced_count = unplaced_count + #{unplaced}
            WHERE id = #{id}
            """)
    int incrementRaceStats(
            @Param("id") long id,
            @Param("first") int first,
            @Param("second") int second,
            @Param("third") int third,
            @Param("unplaced") int unplaced
    );
}
