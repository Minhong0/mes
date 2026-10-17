package com.minimes.alarm.repository;

import com.minimes.alarm.domain.EquipmentLimit;
import com.minimes.alarm.domain.EquipmentLimitId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentLimitRepository extends JpaRepository<EquipmentLimit, EquipmentLimitId> {

    List<EquipmentLimit> findAllByOrderByEquipCodeAscMetricAsc();
}
