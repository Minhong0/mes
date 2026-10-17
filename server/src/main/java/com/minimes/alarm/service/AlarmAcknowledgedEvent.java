package com.minimes.alarm.service;

/** 담당자가 알람을 확인했다. 트랜잭션이 커밋된 뒤에 듣는 쪽(대시보드 push)이 받는다. */
public record AlarmAcknowledgedEvent(long alarmId, String equipCode) {
}
