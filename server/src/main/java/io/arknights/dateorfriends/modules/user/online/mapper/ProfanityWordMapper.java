package io.arknights.dateorfriends.modules.user.online.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProfanityWordMapper {

    @Select("SELECT word FROM `profanity_word`")
    List<String> selectAllWords();

    @Select("SELECT COUNT(*) FROM `profanity_word`")
    int countAll();

    @Select("SELECT id, word, created_at FROM `profanity_word` ORDER BY id DESC LIMIT #{offset}, #{limit}")
    List<ProfanityWordDO> selectPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT id, word, created_at FROM `profanity_word` WHERE id = #{id}")
    ProfanityWordDO selectById(@Param("id") int id);

    @Select("SELECT id FROM `profanity_word` WHERE word = #{word} LIMIT 1")
    Integer selectIdByWord(@Param("word") String word);

    @Insert("INSERT INTO `profanity_word` (word) VALUES (#{word})")
    int insert(@Param("word") String word);

    @Delete("DELETE FROM `profanity_word` WHERE id = #{id}")
    int deleteById(@Param("id") int id);
}
