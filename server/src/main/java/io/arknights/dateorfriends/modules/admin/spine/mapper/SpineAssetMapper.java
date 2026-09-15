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
            INSERT INTO spine_asset(asset_key, name, created_by, updated_by)
            VALUES(#{assetKey}, #{name}, #{createdBy}, #{updatedBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAsset(SpineAssetDO asset);

    @Update("""
            UPDATE spine_asset
            SET name=#{name}, updated_by=#{updatedBy}
            WHERE id=#{id}
            """)
    int updateAsset(@Param("id") long id, @Param("name") String name, @Param("updatedBy") long updatedBy);

    @Select("SELECT * FROM spine_asset WHERE id=#{id}")
    SpineAssetDO selectById(@Param("id") long id);

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
            </where>
            ORDER BY updated_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SpineAssetDO> list(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    @Delete("DELETE FROM spine_asset WHERE id=#{id}")
    int deleteAsset(@Param("id") long id);
}
