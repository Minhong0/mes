package com.minimes.monitoring.service;

import com.minimes.monitoring.mapper.DowntimeReasonRow;
import com.minimes.monitoring.mapper.MonitoringMapper;
import com.minimes.monitoring.service.DowntimePareto.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 파레토 비율과 "중점 관리 대상" 표시. 합계·누계는 SQL이 구한다고 보고, 그 결과(행)를 가짜로 정해 둔다. */
class DowntimeParetoServiceTest {

    static final LocalDate SERVER_TODAY = LocalDate.of(2026, 10, 16);

    MonitoringMapper mapper;
    DowntimeParetoService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MonitoringMapper.class);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        service = new DowntimeParetoService(mapper, Clock.fixed(SERVER_TODAY.atStartOfDay(zone).toInstant(), zone));
    }

    static DowntimeReasonRow row(String dtSub, int count, int minutes, int cumulative) {
        return new DowntimeReasonRow("SETUP", "품번교체(셋업)", dtSub, dtSub + " 이름", count, minutes, cumulative, 1000);
    }

    @Test
    @DisplayName("비율·누적 비율(소수 1자리), 80%를 넘기는 첫 줄까지 중점 관리 대상")
    void percentagesAndVitalFew() {
        when(mapper.selectDowntimeByReason(any(), any(), any(), anyBoolean())).thenReturn(List.of(
                row("A", 10, 500, 500),     // 50.0 → 누적 50.0
                row("B", 5, 250, 750),      // 25.0 → 75.0  (앞 누적 50 < 80)
                row("C", 3, 133, 883),      // 13.3 → 88.3  (앞 누적 75 < 80: 80을 넘기는 줄이라 포함)
                row("D", 2, 117, 1000)));   // 11.7 → 100.0 (앞 누적 88.3 ≥ 80)

        DowntimePareto pareto = service.pareto(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), null, true);

        assertThat(pareto.items())
                .extracting(Item::dtSub, i -> i.sharePct().toPlainString(), i -> i.cumulativePct().toPlainString(), Item::vitalFew)
                .containsExactly(
                        tuple("A", "50.0", "50.0", true),
                        tuple("B", "25.0", "75.0", true),
                        tuple("C", "13.3", "88.3", true),
                        tuple("D", "11.7", "100.0", false));
        assertThat(pareto.totalMin()).isEqualTo(1000);
        assertThat(pareto.stopCount()).isEqualTo(20);
    }

    @Test
    @DisplayName("기간 끝(to)은 포함: SQL에는 다음 날 0시 미만으로 넘긴다")
    void toIsInclusive() {
        when(mapper.selectDowntimeByReason(any(), any(), any(), anyBoolean())).thenReturn(List.of());

        service.pareto(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), "CNC03", false);

        verify(mapper).selectDowntimeByReason(LocalDateTime.of(2025, 10, 1, 0, 0), LocalDateTime.of(2025, 11, 1, 0, 0),
                "CNC03", false);
    }

    @Test
    @DisplayName("기간을 비우면 가장 최근 정지일까지 30일, 정지 기록이 없으면 서버 날짜까지 30일")
    void defaultPeriod() {
        when(mapper.selectDowntimeByReason(any(), any(), any(), anyBoolean())).thenReturn(List.of());
        when(mapper.selectLatestDowntimeDate()).thenReturn(LocalDate.of(2026, 10, 18));

        DowntimePareto pareto = service.pareto(null, null, null, true);
        assertThat(pareto.from()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(pareto.to()).isEqualTo(LocalDate.of(2026, 10, 18));

        when(mapper.selectLatestDowntimeDate()).thenReturn(null);
        assertThat(service.pareto(null, null, null, true).to()).isEqualTo(SERVER_TODAY);
    }

    @Test
    @DisplayName("정지가 없으면 빈 목록, 합계 0")
    void empty() {
        when(mapper.selectDowntimeByReason(eq(LocalDateTime.of(2025, 1, 1, 0, 0)), any(), any(), anyBoolean())).thenReturn(List.of());

        DowntimePareto pareto = service.pareto(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 7), null, true);

        assertThat(pareto.items()).isEmpty();
        assertThat(pareto.totalMin()).isZero();
    }
}
