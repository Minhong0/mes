package com.minimes.monitoring.push;

import com.minimes.equipment.domain.EquipmentStatus;
import com.minimes.equipment.service.EquipmentDataSavedEvent;
import com.minimes.monitoring.service.Dashboard;
import com.minimes.monitoring.service.MonitoringService;
import com.minimes.production.service.ProductionAggregatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 언제 보내고 언제 안 보내는지. 스케줄러 대신 pushIfChanged()를 직접 불러 "1초가 지났다"를 흉내 낸다. */
class DashboardPusherTest {

    static final Dashboard DASHBOARD = new Dashboard(LocalDateTime.of(2026, 10, 18, 10, 0), LocalDate.of(2026, 10, 18), List.of());

    MonitoringService monitoringService;
    SimpMessagingTemplate messagingTemplate;
    DashboardPusher pusher;

    @BeforeEach
    void setUp() {
        monitoringService = mock(MonitoringService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        pusher = new DashboardPusher(monitoringService, messagingTemplate);
        when(monitoringService.dashboard()).thenReturn(DASHBOARD);
    }

    static EquipmentDataSavedEvent saved(String equipCode) {
        return new EquipmentDataSavedEvent(equipCode, LocalDateTime.of(2026, 10, 18, 10, 0), EquipmentStatus.RUN,
                new BigDecimal("1.2"), new BigDecimal("35.0"));
    }

    @Test
    @DisplayName("바뀐 것이 없으면 조회도 전송도 하지 않는다")
    void nothingChanged() {
        pusher.pushIfChanged();

        verify(monitoringService, never()).dashboard();
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("한 주기 안에 여러 건이 저장돼도 한 번만 보낸다")
    void coalesce() {
        pusher.onDataSaved(saved("CNC01"));
        pusher.onDataSaved(saved("CNC02"));
        pusher.onAggregated(new ProductionAggregatedEvent(LocalDate.of(2026, 10, 18)));

        pusher.pushIfChanged();
        pusher.pushIfChanged();   // 그 사이 바뀐 것이 없으니 보내지 않는다

        verify(messagingTemplate, times(1)).convertAndSend(DashboardPusher.TOPIC, DASHBOARD);
    }

    @Test
    @DisplayName("전송에 실패하면 다음 주기에 다시 보낸다")
    void retryAfterFailure() {
        doThrow(new IllegalStateException("broker down"))
                .doNothing()
                .when(messagingTemplate).convertAndSend(eq(DashboardPusher.TOPIC), any(Object.class));
        pusher.onDataSaved(saved("CNC01"));

        pusher.pushIfChanged();   // 실패
        pusher.pushIfChanged();   // 재시도

        verify(messagingTemplate, times(2)).convertAndSend(DashboardPusher.TOPIC, DASHBOARD);
    }
}
