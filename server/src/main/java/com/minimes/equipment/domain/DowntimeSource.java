package com.minimes.equipment.domain;

/** 비가동 기록을 누가 남겼나. downtime.source의 CHECK 제약과 같은 값이다. */
public enum DowntimeSource {
    BOOTCAMP,   // 부트캠프 과거 실적 (seed)
    POP,        // 작업자가 POP 화면에서 입력 (예정)
    EQUIP       // 설비 데이터로 자동 기록 (이 수집기)
}
