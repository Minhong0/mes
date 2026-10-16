package com.minimes.equipment.service;

import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.domain.EquipmentStatus;
import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.master.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 설비 메시지 저장.
 * 시뮬레이터는 "최소 한 번" 보낸다 (확인을 못 받으면 다시 보냄). 그래서 같은 메시지가 두 번 올 수 있고,
 * 여기서 messageId로 걸러 "정확히 한 번" 저장되게 한다. 이렇게 같은 요청을 여러 번 받아도 결과가 같게 만드는 것을 멱등성이라고 한다.
 *
 * 새 메시지를 저장한 뒤에는 상태 이력·비가동 기록도 갱신한다 (EquipmentStatusService). 같은 트랜잭션이라
 * 둘 중 하나가 실패하면 원본 저장도 취소되고, 브로커가 다시 보낸 메시지로 처음부터 다시 처리한다.
 * 중복 메시지는 여기서 걸러지므로 상태 이력이 두 번 바뀌는 일도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EquipmentDataService {

    private final EquipmentDataRepository equipmentDataRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentStatusService equipmentStatusService;
    private final Clock clock;

    public SaveResult save(EquipmentMessage message) {
        // 1차 방어: 이미 받은 메시지인지 먼저 확인한다.
        // 2차 방어: 동시에 같은 메시지가 들어와 둘 다 이 검사를 통과해도, DB의 UNIQUE 제약이 두 번째 INSERT를 막는다.
        if (equipmentDataRepository.existsByMessageId(message.messageId())) {
            return SaveResult.DUPLICATE;
        }
        if (!equipmentRepository.existsById(message.equipCode())) {
            return SaveResult.UNKNOWN_EQUIPMENT;   // 저장하면 외래키 오류가 나므로 미리 거른다
        }

        // DB 컬럼(DATETIME2)에는 시간대가 없으므로, 서버 시간대(한국)의 시각으로 바꿔 저장한다.
        LocalDateTime collectedAt = message.collectedAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
        equipmentDataRepository.save(EquipmentData.of(
                message.messageId(),
                message.equipCode(),
                collectedAt,
                LocalDateTime.now(clock),
                message.cycleCount(),
                message.status(),
                message.spindleVibration(),
                message.spindleTemp()));

        EquipmentStatus status = parseStatus(message);
        if (status != null) {
            equipmentStatusService.apply(message.equipCode(), status,
                    message.downtimeCategory(), message.downtimeReason(), collectedAt);
        }
        return SaveResult.SAVED;
    }

    /** 문자열 상태를 enum으로. 모르는 값이면 원본만 저장하고 상태 이력은 건드리지 않는다. */
    private EquipmentStatus parseStatus(EquipmentMessage message) {
        if (message.status() == null) {
            return null;
        }
        try {
            return EquipmentStatus.valueOf(message.status());
        } catch (IllegalArgumentException e) {
            log.warn("[상태] 알 수 없는 상태 값, 상태 반영 안 함: {} {}", message.messageId(), message.status());
            return null;
        }
    }
}
