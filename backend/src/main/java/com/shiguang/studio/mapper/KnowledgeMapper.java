package com.shiguang.studio.mapper;
import com.shiguang.studio.entity.KnowledgeArticle;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface KnowledgeMapper {
 @Select("SELECT * FROM knowledge_article ORDER BY updated_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
 List<KnowledgeArticle> list(@Param("limit") int limit,@Param("offset") int offset);
 @Select("SELECT COUNT(*) FROM knowledge_article") long count();
 @Select("SELECT * FROM knowledge_article WHERE status='PUBLISHED' AND audience='PUBLIC' ORDER BY id LIMIT #{limit} OFFSET #{offset}")
 List<KnowledgeArticle> published(@Param("limit") int limit,@Param("offset") int offset);
 @Select("SELECT COUNT(*) FROM knowledge_article WHERE status='PUBLISHED' AND audience='PUBLIC'") long publishedCount();
 @Options(flushCache=Options.FlushCachePolicy.TRUE)
 @Select("SELECT * FROM knowledge_article WHERE id=#{id}") KnowledgeArticle find(long id);
 @Insert("INSERT INTO knowledge_article(title,category,content,status,audience) VALUES(#{title},#{category},#{content},#{status},#{audience})")
 @Options(useGeneratedKeys=true,keyProperty="id") int insert(KnowledgeArticle a);
 @Update("UPDATE knowledge_article SET source_floor=IF(status<>#{status} OR audience<>#{audience},version+1,source_floor), indexed_version=IF(status<>#{status} OR audience<>#{audience},NULL,indexed_version),audience=#{audience}, title=#{title},category=#{category},content=#{content},status=#{status},version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND version=#{version}") int update(KnowledgeArticle a);
 @Insert("INSERT INTO knowledge_revision(article_id,version,title,category,content,status,audience) SELECT id,version,title,category,content,status,audience FROM knowledge_article WHERE id=#{id}") void revision(long id);
}
