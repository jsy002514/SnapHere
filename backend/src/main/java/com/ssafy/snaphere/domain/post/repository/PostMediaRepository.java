package com.ssafy.snaphere.domain.post.repository;

import com.ssafy.snaphere.domain.post.entity.PostMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

    List<PostMedia> findByPostIdOrderBySortOrderAscIdAsc(Long postId);

    /** 목록 화면에서 여러 게시물의 미디어를 한 번에 가져온다(N+1 방지). */
    List<PostMedia> findByPostIdInOrderByPostIdAscSortOrderAsc(List<Long> postIds);

    boolean existsByMediaHash(String mediaHash);

    void deleteByPostId(Long postId);
}
