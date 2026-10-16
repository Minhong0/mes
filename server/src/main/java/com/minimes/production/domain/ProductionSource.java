package com.minimes.production.domain;

/** 생산 집계를 누가 만들었나. production_daily.source의 CHECK 제약과 같은 값이다. */
public enum ProductionSource {
    BOOTCAMP,   // 부트캠프 과거 실적 (seed). 집계가 덮어쓰지 않는다
    MES         // 설비 수집 데이터로 집계 (ProductionAggregationService)
}
