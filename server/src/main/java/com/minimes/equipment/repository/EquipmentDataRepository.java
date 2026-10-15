package com.minimes.equipment.repository;

import com.minimes.equipment.domain.EquipmentData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentDataRepository extends JpaRepository<EquipmentData, Long> {

    boolean existsByMessageId(String messageId);
}
