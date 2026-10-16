package com.minimes.equipment.repository;

import com.minimes.equipment.domain.EquipmentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EquipmentDataRepository extends JpaRepository<EquipmentData, Long> {

    boolean existsByMessageId(String messageId);

    /** 하루 [from, to) 동안 설비가 보낸 메시지, 측정 시각 순 */
    List<EquipmentData> findByEquipCodeAndCollectedAtGreaterThanEqualAndCollectedAtLessThanOrderByCollectedAt(
            String equipCode, LocalDateTime from, LocalDateTime to);

    /** from 직전의 마지막 메시지. 그날 첫 메시지의 누적 수량과 비교할 기준이 된다 */
    Optional<EquipmentData> findFirstByEquipCodeAndCollectedAtLessThanOrderByCollectedAtDesc(String equipCode, LocalDateTime from);

    /** 설비의 가장 최근 메시지. 아직 끝나지 않은 상태가 "지금"까지 이어졌다고 볼 때 그 "지금"이다 */
    Optional<EquipmentData> findFirstByEquipCodeOrderByCollectedAtDesc(String equipCode);

    /**
     * since 이후에 서버가 받은 메시지들의 측정 시각 범위 [가장 이른 것, 가장 늦은 것].
     * 집계를 다시 해야 하는 날짜를 고르는 데 쓴다. 받은 것이 없으면 두 값 모두 null.
     */
    @Query("""
            select new com.minimes.equipment.repository.CollectedRange(min(d.collectedAt), max(d.collectedAt))
            from EquipmentData d
            where d.receivedAt >= :since
            """)
    CollectedRange findCollectedRangeReceivedSince(@Param("since") LocalDateTime since);
}
