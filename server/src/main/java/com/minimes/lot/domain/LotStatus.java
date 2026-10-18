package com.minimes.lot.domain;

/** LOT 상태. lot.status의 CHECK 제약과 같은 값. */
public enum LotStatus {
    ACTIVE,     // 쓸 수 있음 (남은 수량이 있음)
    HOLD,       // 품질 문제로 사용 보류
    CONSUMED,   // 다음 공정에 모두 투입됨
    SHIPPED,    // 출하됨
    SCRAPPED    // 폐기 (실적 취소로 없어진 LOT 포함)
}
