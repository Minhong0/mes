package com.minimes.workorder.repository;

import com.minimes.workorder.domain.WorkResult;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkResultRepository extends JpaRepository<WorkResult, Long> {

    // 응답에 불량 내역을 담는데, 트랜잭션이 끝난 뒤에는 지연 로딩을 할 수 없어 함께 읽는다
    @EntityGraph(attributePaths = "defects")
    Optional<WorkResult> findByRequestId(String requestId);

    @EntityGraph(attributePaths = "defects")
    Optional<WorkResult> findWithDefectsByResultId(long resultId);

    /** 이 공정의 마지막 유효 실적. 다음 실적의 시작 시각을 정할 때 쓴다. */
    Optional<WorkResult> findTopByWoOpIdAndCancelledFalseOrderByEndedAtDesc(long woOpId);

    /** 공정의 실적 목록 (최근 것부터, 불량 내역 함께) */
    @EntityGraph(attributePaths = "defects")
    List<WorkResult> findByWoOpIdOrderByResultIdDesc(long woOpId);
}
