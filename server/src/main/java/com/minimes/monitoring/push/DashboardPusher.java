package com.minimes.monitoring.push;

import com.minimes.equipment.service.EquipmentDataSavedEvent;
import com.minimes.monitoring.service.MonitoringService;
import com.minimes.production.service.ProductionAggregatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 데이터가 바뀌면 대시보드를 /topic/dashboard로 보낸다 (서버 → 화면 push).
 *
 * 메시지가 올 때마다 바로 보내지 않고 "바뀌었음" 표시만 해 두었다가, 1초마다 표시가 있으면 한 번만 보낸다.
 * 설비 5대가 배속으로 1초에 수십 건을 보내도 대시보드 조회와 전송은 1초에 최대 한 번이다.
 * 바뀐 것이 없으면 조회도 전송도 하지 않는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.monitoring.push", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DashboardPusher {

    public static final String TOPIC = "/topic/dashboard";

    private final MonitoringService monitoringService;
    private final SimpMessagingTemplate messagingTemplate;

    private final AtomicBoolean changed = new AtomicBoolean(false);

    // 커밋된 뒤에만 표시한다: 롤백된 저장은 DB에 없으므로 화면에 보낼 것도 없다
    @TransactionalEventListener
    public void onDataSaved(EquipmentDataSavedEvent event) {
        changed.set(true);
    }

    @TransactionalEventListener
    public void onAggregated(ProductionAggregatedEvent event) {
        changed.set(true);
    }

    @Scheduled(fixedDelayString = "${mes.monitoring.push.interval:PT1S}")
    public void pushIfChanged() {
        if (!changed.getAndSet(false)) {
            return;
        }
        try {
            messagingTemplate.convertAndSend(TOPIC, monitoringService.dashboard());
        } catch (RuntimeException e) {
            changed.set(true);   // 다음 주기에 다시 보낸다
            log.warn("[push] 대시보드 전송 실패, 다음 주기에 재시도: {}", e.getMessage());
        }
    }
}
