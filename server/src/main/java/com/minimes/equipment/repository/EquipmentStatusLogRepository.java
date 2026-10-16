package com.minimes.equipment.repository;

import com.minimes.equipment.domain.EquipmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EquipmentStatusLogRepository extends JpaRepository<EquipmentStatusLog, Long> {

    /** 지금 이어지고 있는 상태 (끝나지 않은 행) */
    Optional<EquipmentStatusLog> findFirstByEquipCodeAndEndedAtIsNullOrderByStartedAtDesc(String equipCode);

    List<EquipmentStatusLog> findByEquipCodeOrderByStartedAt(String equipCode);

    /**
     * [from, to) 구간과 조금이라도 겹치는 상태 이력.
     * 겹친다 = 이 구간이 끝나기 전에 시작했고(started_at < to), 이 구간이 시작한 뒤에 끝났다(ended_at > from, 진행 중이면 null).
     * 예: 하루가 [10-16 00:00, 10-17 00:00)일 때 10-15 22:00 ~ 10-16 01:00 RUN도 포함된다 (자정을 넘긴 상태).
     */
    @Query("""
            select l from EquipmentStatusLog l
            where l.equipCode = :equipCode
              and l.startedAt < :to
              and (l.endedAt is null or l.endedAt > :from)
            order by l.startedAt
            """)
    List<EquipmentStatusLog> findOverlapping(@Param("equipCode") String equipCode,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);
}
