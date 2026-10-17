package com.minimes.alarm.repository;

import com.minimes.alarm.domain.AlarmType;
import com.minimes.alarm.domain.EquipmentAlarm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentAlarmRepository extends JpaRepository<EquipmentAlarm, Long> {

    boolean existsByEquipCodeAndAlarmTypeAndAckAtIsNull(String equipCode, AlarmType alarmType);

    List<EquipmentAlarm> findByAckAtIsNullOrderByOccurredAtDesc();

    List<EquipmentAlarm> findTop100ByOrderByOccurredAtDesc();
}
