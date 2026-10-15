package com.minimes.equipment.mqtt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 설비 시뮬레이터(C#)가 보내는 JSON 메시지. 필드 이름은 C# EquipmentMessage를 camelCase로 바꾼 것과 같다.
 * 예: {"messageId":"CNC01-261015090000-000001","equipCode":"CNC01","collectedAt":"2026-10-15T09:00:00.1234567+09:00",
 *      "cycleCount":1,"status":"RUN","spindleVibration":1.21,"spindleTemp":35.4}
 *
 * collectedAt은 시간대(+09:00)가 붙어 오므로 OffsetDateTime으로 받는다.
 * 진동·온도는 DB가 DECIMAL이라 double 대신 BigDecimal로 받는다 (소수 계산 오차 없음).
 * downtimeCategory, downtimeReason은 정지 중일 때만 온다. 가동 중이면 JSON에 없고 null이 된다.
 */
public record EquipmentMessage(
        String messageId,
        String equipCode,
        OffsetDateTime collectedAt,
        Integer cycleCount,
        String status,
        BigDecimal spindleVibration,
        BigDecimal spindleTemp,
        String downtimeCategory,
        String downtimeReason) {
}
