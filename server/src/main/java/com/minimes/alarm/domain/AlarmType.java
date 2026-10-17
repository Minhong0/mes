package com.minimes.alarm.domain;

/** equipment_alarm.alarm_type (DB CHECK 제약과 같은 값). TOOL_LIFE는 공구 수명 관리를 만들 때 쓴다. */
public enum AlarmType {
    VIBRATION_UCL,
    TEMP_UCL,
    TOOL_LIFE
}
