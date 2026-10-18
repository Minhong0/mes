package com.minimes.equipment.service;

/** 작업자가 정지 사유를 확정했다. 모니터링 화면이 정지 사유 표시를 새로 받도록 알린다. */
public record DowntimeReasonChangedEvent(String equipCode, long downtimeId) {
}
