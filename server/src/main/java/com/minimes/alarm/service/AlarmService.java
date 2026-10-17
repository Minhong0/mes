package com.minimes.alarm.service;

import com.minimes.alarm.domain.EquipmentAlarm;
import com.minimes.alarm.domain.EquipmentLimit;
import com.minimes.alarm.domain.EquipmentLimitId;
import com.minimes.alarm.domain.LimitMetric;
import com.minimes.alarm.repository.EquipmentAlarmRepository;
import com.minimes.alarm.repository.EquipmentLimitRepository;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.domain.EquipmentStatus;
import com.minimes.equipment.service.EquipmentDataSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 관리한계(UCL) 경보.
 *
 * 메시지가 저장될 때마다 가동 중인 설비의 진동·온도를 그 설비의 UCL과 비교하고, 넘으면 equipment_alarm에 남긴다.
 * 같은 설비·종류의 열린 알람(확인 전)이 이미 있으면 새로 만들지 않는다. 고장 징후는 몇 시간씩 이어지므로,
 * 매 메시지마다 알람을 만들면 담당자가 볼 수 없을 만큼 쌓이기 때문이다. 확인한 뒤에도 계속 넘으면 다시 알람이 생긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlarmService {

    private final EquipmentAlarmRepository alarmRepository;
    private final EquipmentLimitRepository limitRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // @EventListener는 이벤트를 낸 쪽과 같은 스레드·트랜잭션에서 바로 실행된다.
    // 그래서 메시지 저장이 롤백되면 알람도 함께 취소되고, 브로커가 다시 보낸 메시지로 다시 판단한다.
    @EventListener
    public void onDataSaved(EquipmentDataSavedEvent event) {
        if (event.status() != EquipmentStatus.RUN) {
            return;   // 정지 중에는 스핀들이 멈춰 있어 진동·온도로 상태를 판단할 수 없다
        }
        for (LimitMetric metric : LimitMetric.values()) {
            check(event, metric);
        }
    }

    private void check(EquipmentDataSavedEvent event, LimitMetric metric) {
        BigDecimal value = metric.valueOf(event);
        if (value == null) {
            return;
        }
        Optional<EquipmentLimit> limit = limitRepository.findById(new EquipmentLimitId(event.equipCode(), metric));
        if (limit.isEmpty() || !limit.get().isExceededBy(value)) {
            return;   // 아직 한계를 정하지 않은 설비는 판단하지 않는다
        }
        if (alarmRepository.existsByEquipCodeAndAlarmTypeAndAckAtIsNull(event.equipCode(), metric.alarmType())) {
            return;
        }
        alarmRepository.save(EquipmentAlarm.limitExceeded(event.equipCode(), metric.alarmType(), value,
                limit.get().getUcl(), event.collectedAt()));
        log.warn("[알람] {} {} {} > UCL {}", event.equipCode(), metric.alarmType(), value, limit.get().getUcl());
    }

    @Transactional(readOnly = true)
    public List<EquipmentAlarm> findAlarms(boolean openOnly) {
        return openOnly
                ? alarmRepository.findByAckAtIsNullOrderByOccurredAtDesc()
                : alarmRepository.findTop100ByOrderByOccurredAtDesc();
    }

    /** 담당자가 알람을 확인했다. 확인 시각은 서버 시각이다. */
    public EquipmentAlarm acknowledge(long alarmId, String ackBy) {
        EquipmentAlarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALARM_NOT_FOUND));
        alarm.acknowledge(ackBy, LocalDateTime.now(clock));
        eventPublisher.publishEvent(new AlarmAcknowledgedEvent(alarmId, alarm.getEquipCode()));
        return alarm;
    }
}
