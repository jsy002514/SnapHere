package com.ssafy.snaphere.domain.tag.repository;

import com.ssafy.snaphere.domain.tag.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    List<Tag> findByNameIn(List<String> names);

    /** 인기 태그. 정렬에 PK 를 붙여 동일 사용횟수 구간의 순서를 고정한다. */
    List<Tag> findAllByOrderByUsageCountDescIdAsc(Pageable pageable);

    /**
     * 사용 횟수 증감. 엔티티를 읽어 수정하지 않고 UPDATE 한 방으로 처리한다.
     * 동시에 여러 사람이 같은 태그를 쓸 때 갱신 손실(lost update)을 피하기 위함이다.
     */
    @Modifying
    @Query("UPDATE Tag t SET t.usageCount = t.usageCount + :delta WHERE t.id IN :ids")
    void addUsageCount(@Param("ids") List<Long> ids, @Param("delta") int delta);
}
