package com.minimes.monitoring.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 설비 한 대의 "지금" 모습. 여러 테이블에서 각자 가장 최근 한 행을 모은 것이다 (MonitoringMapper.xml).
 *
 * @param status        설비 현재 상태 (equipment.status)
 * @param statusSince   지금 상태가 시작된 시각 (끝나지 않은 상태 이력)
 * @param stopStartedAt 진행 중인 정지의 시작 시각. 가동 중이면 null (아래 dt* 필드도 모두 null)
 * @param lastSeenAt    마지막 메시지의 측정 시각 (공장 시계)
 */
public record EquipmentNow(
        String equipCode,
        String equipName,
        String status,
        String statusName,
        LocalDateTime statusSince,
        String dtCat,
        String dtCatName,
        String dtSub,
        String dtSubName,
        LocalDateTime stopStartedAt,
        LocalDateTime lastSeenAt,
        Integer cycleCount,
        BigDecimal spindleVibration,
        BigDecimal spindleTemp
) {
}
