package com.ssafy.snaphere.domain.tour.repository;

import com.ssafy.snaphere.domain.tour.entity.SyncLog;
import com.ssafy.snaphere.domain.tour.entity.SyncType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /** 관리자 화면에서 최근 실행 내역 확인용. 정렬에 PK 를 붙여 동시각 레코드의 순서를 고정한다. */
    List<SyncLog> findBySyncTypeOrderByStartedAtDescIdDesc(SyncType syncType, Pageable pageable);
}
