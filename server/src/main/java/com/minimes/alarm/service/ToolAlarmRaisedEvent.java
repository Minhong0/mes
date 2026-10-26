package com.minimes.alarm.service;

/** 공구 수명 알람이 새로 생겼다. 트랜잭션이 커밋된 뒤에 듣는 쪽(대시보드 push)이 받는다. */
public record ToolAlarmRaisedEvent(long alarmId, String equipCode, String toolCode) {
}
