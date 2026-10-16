package com.minimes.production.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주기적으로 일별 집계를 다시 한다 (기본 1분마다).
 * 매번 모든 날짜를 다시 하지 않고, 지난 실행 이후에 새 데이터가 들어온 날짜만 집계한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.aggregation", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ProductionAggregationScheduler {

    private final ProductionAggregationService aggregationService;
    private final Clock clock;

    // 처음 실행할 때는 그동안 받은 모든 데이터를 대상으로 한다 (서버를 껐다 켜도 빠짐없이)
    private LocalDateTime lastRunAt = LocalDateTime.of(2000, 1, 1, 0, 0);

    @Scheduled(initialDelayString = "${mes.aggregation.initial-delay:PT10S}", fixedDelayString = "${mes.aggregation.interval:PT1M}")
    public void run() {
        // 조회하기 "전"의 시각을 기억한다. 집계하는 동안 들어온 데이터는 다음 실행에서 다시 보게 되어 빠지지 않는다
        // (두 번 집계될 수는 있지만 집계는 멱등이라 괜찮다).
        LocalDateTime startedAt = LocalDateTime.now(clock);
        List<LocalDate> dates = aggregationService.datesReceivedSince(lastRunAt);
        for (LocalDate date : dates) {
            int saved = aggregationService.aggregate(date);   // 날짜마다 따로 트랜잭션: 한 날짜가 실패해도 다른 날짜는 저장된다
            log.info("[집계] {} 설비 {}대", date, saved);
        }
        lastRunAt = startedAt;
    }
}
