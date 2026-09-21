package com.snaphere.api.post.repository;

import com.snaphere.api.post.entity.PostTagEntity;
import com.snaphere.api.post.entity.PostTagId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** 게시글-태그 연결. (PST-004, CMU-032) */
public interface PostTagRepository extends JpaRepository<PostTagEntity, PostTagId> {

    List<PostTagEntity> findByIdPostId(Long postId);

    /** 목록·상세 응답의 태그를 한 번에. 게시글마다 조회하면 N+1 이다 (SYS-018). */
    List<PostTagEntity> findByIdPostIdIn(Collection<Long> postIds);

    /** 게시글 수정 시 전부 지우고 다시 넣는다. 차이 계산은 버그를 부른다. (CMU-032) */
    void deleteByIdPostId(Long postId);

    /** 지역별 인기 태그. 살아 있는 게시글에 붙은 횟수로 센다. (CMU-031) */
    @Query("""
            select pt.id.tagId, count(pt.id.postId)
              from PostTagEntity pt, PostEntity p
             where pt.id.postId = p.postId
               and p.status = com.snaphere.api.post.PostStatus.ACTIVE
               and (:areaCode is null or p.areaCode = :areaCode)
             group by pt.id.tagId
             order by count(pt.id.postId) desc, pt.id.tagId asc
            """)
    List<Object[]> findPopularTagCounts(@Param("areaCode") Integer areaCode, Pageable pageable);
}
