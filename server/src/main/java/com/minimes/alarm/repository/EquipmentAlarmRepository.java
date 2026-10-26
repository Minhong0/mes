package com.minimes.alarm.repository;

import com.minimes.alarm.domain.AlarmType;
import com.minimes.alarm.domain.EquipmentAlarm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EquipmentAlarmRepository extends JpaRepository<EquipmentAlarm, Long> {

    boolean existsByEquipCodeAndAlarmTypeAndAckAtIsNull(String equipCode, AlarmType alarmType);

    List<EquipmentAlarm> findByAckAtIsNullOrderByOccurredAtDesc();

    List<EquipmentAlarm> findTop100ByOrderByOccurredAtDesc();

    /** 이 공구를 설치한 뒤에 알람을 낸 적이 있는가 (설치 한 번에 알람 한 번). 설치와 같은 시각의 알람은 바꾸기 전 공구의 것 */
    boolean existsByToolCodeAndOccurredAtGreaterThan(String toolCode, LocalDateTime since);

    List<EquipmentAlarm> findByToolCodeAndAckAtIsNull(String toolCode);
}
