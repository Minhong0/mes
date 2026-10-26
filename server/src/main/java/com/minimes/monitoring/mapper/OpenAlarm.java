package com.minimes.monitoring.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 확인 전 알람 한 건 (대시보드 카드용). alarmType: VIBRATION_UCL, TEMP_UCL, TOOL_LIFE.
 * 공구 수명 알람이면 toolCode가 있고 measuredValue = 사용 수량, limitValue = 수명이다.
 */
public record OpenAlarm(long alarmId, String equipCode, String alarmType, BigDecimal measuredValue,
                        BigDecimal limitValue, LocalDateTime occurredAt, String toolCode) {
}
