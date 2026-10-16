package com.minimes.equipment.service;

import com.minimes.equipment.domain.Downtime;
import com.minimes.equipment.domain.DowntimeSource;
import com.minimes.equipment.domain.EquipmentStatus;
import com.minimes.equipment.domain.EquipmentStatusLog;
import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.repository.DowntimeRepository;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.equipment.repository.EquipmentStatusLogRepository;
import com.minimes.master.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 설비 메시지의 상태가 바뀌었을 때 이력을 남긴다.
 *
 *   RUN → DOWN : 상태 이력(RUN) 닫기, 상태 이력(DOWN) 열기, 비가동 기록 열기
 *   DOWN → RUN : 상태 이력(DOWN) 닫기, 상태 이력(RUN) 열기, 비가동 기록 닫기(정지 시간 계산)
 *   RUN → RUN  : 아무것도 하지 않는다 (대부분의 메시지)
 *
 * 메시지마다 행을 쌓지 않고 "바뀐 순간"만 기록하므로, 상태 이력은 메시지 수보다 훨씬 적다.
 *
 * 소식 끊김: 직전 메시지와 OFFLINE_GAP(30분)보다 멀리 떨어진 메시지가 오면, 그 사이에는 설비가 꺼졌거나 통신이
 * 끊겼던 것으로 본다. 진행 중이던 이력을 직전 메시지 시각에 닫고 새로 연다. 그 사이는 어떤 상태로도 기록하지 않으므로
 * 일별 집계의 계획 시간에서도 빠진다. (설비는 정지 중에도 10분마다 상태를 보내므로, 정상이면 30분 넘게 비지 않는다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentStatusService {

    static final Duration OFFLINE_GAP = Duration.ofMinutes(30);

    private final EquipmentStatusLogRepository statusLogRepository;
    private final EquipmentDataRepository equipmentDataRepository;
    private final DowntimeRepository downtimeRepository;
    private final EquipmentRepository equipmentRepository;
    private final Clock clock;

    public void apply(String equipCode, EquipmentStatus status, String dtCat, String dtSub, LocalDateTime collectedAt) {
        // 상태 이력과 비가동 테이블의 시각 컬럼은 초 단위(DATETIME2(0))라 초 아래를 버린다.
        // DB가 반올림하게 두면 0.6초가 1초로 올라가, 같은 순간의 두 행이 1초 어긋날 수 있다.
        LocalDateTime at = collectedAt.truncatedTo(ChronoUnit.SECONDS);

        EquipmentStatusLog current = statusLogRepository
                .findFirstByEquipCodeAndEndedAtIsNullOrderByStartedAtDesc(equipCode)
                .orElse(null);

        if (current != null) {
            if (at.isBefore(current.getStartedAt())) {
                // 지금 상태가 시작된 시각보다 이른 메시지: 순서가 뒤바뀌어 늦게 도착한 것이다.
                // 이걸로 상태를 바꾸면 이력이 거꾸로 꼬이므로, 원본(equipment_data)에만 남기고 상태는 건드리지 않는다.
                log.info("[상태] 늦게 도착한 메시지, 상태 반영 안 함: {} {} {}", equipCode, status, at);
                return;
            }
            LocalDateTime lastSeen = lastSeenBefore(equipCode, collectedAt, current.getStartedAt());
            if (Duration.between(lastSeen, at).compareTo(OFFLINE_GAP) > 0) {
                // 소식이 끊겼다 돌아왔다: 마지막으로 소식이 있던 시각에 닫고, 상태가 같아도 새로 연다.
                log.info("[상태] {} 소식 끊김 {} ~ {}, 그 사이는 기록하지 않음", equipCode, lastSeen, at);
                current.end(lastSeen);
                closeOpenDowntime(equipCode, lastSeen);
            } else if (current.getStatus() == status) {
                return;   // 상태가 그대로면 할 일이 없다
            } else {
                current.end(at);   // 변경 감지: save를 부르지 않아도 커밋할 때 UPDATE 된다
            }
        }

        statusLogRepository.save(EquipmentStatusLog.start(equipCode, status, at));
        equipmentRepository.findById(equipCode).ifPresent(equipment -> equipment.changeStatus(status.name()));

        // 비가동 기록: 진행 중인 정지가 있으면 닫고, 새 상태가 정지면 새로 연다.
        // (DOWN → SETUP처럼 정지끼리 바로 바뀌어도 앞 정지를 닫고 새 정지를 연다)
        closeOpenDowntime(equipCode, at);
        if (status.isStopped()) {
            if (dtCat == null) {
                log.warn("[상태] 정지 사유가 없는 정지 메시지: {} {} {}", equipCode, status, at);
            } else {
                downtimeRepository.save(Downtime.startFromEquipment(equipCode, dtCat, dtSub, at, LocalDateTime.now(clock)));
            }
        }
        log.info("[상태] {} {} → {} ({})", equipCode, current == null ? "없음" : current.getStatus(), status, at);
    }

    private void closeOpenDowntime(String equipCode, LocalDateTime at) {
        downtimeRepository.findFirstByEquipCodeAndSourceAndEndedAtIsNullOrderByStartedAtDesc(equipCode, DowntimeSource.EQUIP)
                .ifPresent(open -> open.end(at));
    }

    /** 이 메시지 직전에 받은 메시지의 측정 시각 (초 단위). 없으면, 또는 지금 상태 시작보다 이르면 지금 상태의 시작 시각. */
    private LocalDateTime lastSeenBefore(String equipCode, LocalDateTime collectedAt, LocalDateTime currentStartedAt) {
        LocalDateTime lastSeen = equipmentDataRepository
                .findFirstByEquipCodeAndCollectedAtLessThanOrderByCollectedAtDesc(equipCode, collectedAt)
                .map(EquipmentData::getCollectedAt)
                .map(t -> t.truncatedTo(ChronoUnit.SECONDS))
                .orElse(currentStartedAt);
        return lastSeen.isBefore(currentStartedAt) ? currentStartedAt : lastSeen;
    }
}
