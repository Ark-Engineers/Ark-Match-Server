package io.arknights.dateorfriends.modules.admin.spine.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpineAssetFileMapper {
    @Insert("""
            INSERT INTO spine_asset_file(asset_id, file_type, original_name, stored_name, relative_path, size_bytes, mime_type)
            VALUES(#{assetId}, #{fileType}, #{originalName}, #{storedName}, #{relativePath}, #{sizeBytes}, #{mimeType})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFile(SpineAssetFileDO file);

    @Select("SELECT * FROM spine_asset_file WHERE asset_id=#{assetId} ORDER BY id ASC")
    List<SpineAssetFileDO> listByAssetId(@Param("assetId") long assetId);

    @Delete("DELETE FROM spine_asset_file WHERE asset_id=#{assetId}")
    int deleteByAssetId(@Param("assetId") long assetId);
}
