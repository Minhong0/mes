package com.minimes.lot.domain;

/** LOT 이력의 종류. lot_history.event_type의 CHECK 제약과 같은 값. */
public enum LotEventType {
    RECEIVE,   // 원자재 입고
    PRODUCE,   // 실적으로 만들어짐
    CONSUME,   // 실적에 투입됨
    MERGE,
    SPLIT,
    INSPECT,
    HOLD,
    RELEASE,
    SHIP,
    SCRAP,
    CANCEL     // 실적 취소로 되돌림
}
