package com.minimes.tool.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 주기적으로 공구 사용 수량을 다시 센다 (기본 30초마다). */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.tool-life", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ToolLifeScheduler {

    private final ToolLifeService toolLifeService;

    @Scheduled(initialDelayString = "${mes.tool-life.initial-delay:PT15S}", fixedDelayString = "${mes.tool-life.interval:PT30S}")
    public void run() {
        int raised = toolLifeService.refresh();
        if (raised > 0) {
            log.info("[공구] 수명 알람 {}건", raised);
        }
    }
}
