package com.minimes.equipment.service;

import com.minimes.equipment.domain.EquipmentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 설비 메시지 한 건을 새로 저장했다 (중복·모르는 설비는 제외).
 * 듣는 쪽: 관리한계 경보(저장과 같은 트랜잭션 안), 대시보드 push(커밋된 뒤).
 *
 * @param collectedAt 측정 시각 (서버 시간대로 바꾼 값)
 * @param status      상태. 모르는 값이었으면 null
 */
public record EquipmentDataSavedEvent(String equipCode, LocalDateTime collectedAt, EquipmentStatus status,
                                      BigDecimal spindleVibration, BigDecimal spindleTemp) {
}
