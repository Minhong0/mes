package com.minimes.monitoring.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 확인 전 알람 한 건 (대시보드 카드용). alarmType: VIBRATION_UCL, TEMP_UCL, TOOL_LIFE */
public record OpenAlarm(long alarmId, String equipCode, String alarmType, BigDecimal measuredValue,
                        BigDecimal limitValue, LocalDateTime occurredAt) {
}
