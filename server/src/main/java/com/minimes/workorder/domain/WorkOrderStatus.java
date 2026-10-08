package com.minimes.workorder.domain;

/**
 * 작업지시 상태. 공통코드 WO_STATUS와 같은 값.
 * 대기(WAIT) → 진행(RUN) → 완료(DONE) → 마감(CLOSED) 순서로만 바뀐다.
 */
public enum WorkOrderStatus {
    WAIT,
    RUN,
    DONE,
    CLOSED
}
