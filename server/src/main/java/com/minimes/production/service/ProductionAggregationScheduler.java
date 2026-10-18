package com.minimes.production.service;

import com.minimes.workorder.service.WorkResultChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 주기적으로 일별 집계를 다시 한다 (기본 1분마다).
 * 매번 모든 날짜를 다시 하지 않고, 지난 실행 이후에 새 데이터가 들어온 날짜만 집계한다.
 * POP 실적이 등록·취소된 날짜도 다시 집계한다 (불량 수량이 바뀐다).
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

    // POP 실적이 바뀐 날짜. 요청을 처리하는 스레드가 넣고 스케줄러 스레드가 꺼내므로 동시에 써도 되는 Set을 쓴다
    private final Set<LocalDate> resultChangedDates = ConcurrentHashMap.newKeySet();

    // 커밋된 뒤에만 넣는다: 롤백된 실적은 DB에 없으므로 다시 집계할 이유가 없다
    @TransactionalEventListener
    public void onWorkResultChanged(WorkResultChangedEvent event) {
        resultChangedDates.add(event.prodDate());
    }

    @Scheduled(initialDelayString = "${mes.aggregation.initial-delay:PT10S}", fixedDelayString = "${mes.aggregation.interval:PT1M}")
    public void run() {
        // 조회하기 "전"의 시각을 기억한다. 집계하는 동안 들어온 데이터는 다음 실행에서 다시 보게 되어 빠지지 않는다
        // (두 번 집계될 수는 있지만 집계는 멱등이라 괜찮다).
        LocalDateTime startedAt = LocalDateTime.now(clock);
        Set<LocalDate> changed = Set.copyOf(resultChangedDates);
        resultChangedDates.removeAll(changed);   // 꺼낸 뒤에 들어온 실적은 다시 들어가 다음 실행에서 집계된다
        Set<LocalDate> dates = new TreeSet<>(changed);
        try {
            dates.addAll(aggregationService.datesReceivedSince(lastRunAt));
            for (LocalDate date : dates) {
                int saved = aggregationService.aggregate(date);   // 날짜마다 따로 트랜잭션
                log.info("[집계] {} 설비 {}대", date, saved);
            }
        } catch (RuntimeException e) {
            resultChangedDates.addAll(changed);   // 실패하면 다음 실행에서 다시 한다 (lastRunAt도 그대로 둔다)
            throw e;
        }
        lastRunAt = startedAt;
    }
}
