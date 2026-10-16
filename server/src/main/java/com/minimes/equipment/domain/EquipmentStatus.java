package com.minimes.equipment.domain;

/** 설비 상태. 공통코드 EQUIP_STATUS와 같은 값이다. */
public enum EquipmentStatus {
    RUN,     // 가동
    IDLE,    // 대기
    SETUP,   // 셋업 (계획된 정지)
    DOWN;    // 정지 (고장, 자재 대기 등)

    /** 비가동으로 기록하는 상태인가 (downtime 테이블에 남긴다) */
    public boolean isStopped() {
        return this == SETUP || this == DOWN;
    }
}
