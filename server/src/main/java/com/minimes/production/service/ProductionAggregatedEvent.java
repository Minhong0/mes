package com.minimes.production.service;

import java.time.LocalDate;

/** 하루치 일별 실적을 다시 집계해 저장했다. 트랜잭션이 커밋된 뒤에 듣는 쪽이 받는다. */
public record ProductionAggregatedEvent(LocalDate prodDate) {
}
