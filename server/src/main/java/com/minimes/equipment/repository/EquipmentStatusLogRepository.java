package com.minimes.equipment.repository;

import com.minimes.equipment.domain.EquipmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentStatusLogRepository extends JpaRepository<EquipmentStatusLog, Long> {

    /** 지금 이어지고 있는 상태 (끝나지 않은 행) */
    Optional<EquipmentStatusLog> findFirstByEquipCodeAndEndedAtIsNullOrderByStartedAtDesc(String equipCode);

    List<EquipmentStatusLog> findByEquipCodeOrderByStartedAt(String equipCode);
}
