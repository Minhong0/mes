package com.minimes.monitoring.service;

import com.minimes.monitoring.mapper.EquipmentNow;
import com.minimes.monitoring.mapper.MonitoringMapper;
import com.minimes.monitoring.mapper.OpenAlarm;
import com.minimes.monitoring.service.Dashboard.EquipmentCard;
import com.minimes.production.mapper.OeeDaily;
import com.minimes.production.mapper.OeeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 대시보드 데이터 조립 규칙. Spring도 DB도 띄우지 않는 순수 단위 테스트다.
 * 매퍼 두 개를 가짜(mock)로 바꿔 "DB가 이런 행을 돌려줬다면"을 정해 두고, 서비스의 판단만 확인한다.
 * (매퍼의 SQL 자체는 MSSQL 전용 문법이라 MssqlIntegrationTest가 확인한다)
 */
class MonitoringServiceTest {

    static final LocalDateTime SERVER_NOW = LocalDateTime.of(2026, 10, 16, 15, 0);

    MonitoringMapper monitoringMapper;
    OeeMapper oeeMapper;
    MonitoringService service;

    @BeforeEach
    void setUp() {
        monitoringMapper = mock(MonitoringMapper.class);
        oeeMapper = mock(OeeMapper.class);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        service = new MonitoringService(monitoringMapper, oeeMapper, Clock.fixed(SERVER_NOW.atZone(zone).toInstant(), zone));
        when(oeeMapper.selectDaily(any())).thenReturn(List.of());
    }

    static EquipmentNow running(String equipCode, String statusSince, String lastSeenAt) {
        return new EquipmentNow(equipCode, equipCode + " 이름", "RUN", "가동", LocalDateTime.parse(statusSince),
                null, null, null, null, null, LocalDateTime.parse(lastSeenAt), 10, new BigDecimal("1.2"), new BigDecimal("35.0"));
    }

    @Test
    @DisplayName("공장 시계의 지금 = 가장 늦은 측정 시각. 몇 분째와 오늘 날짜도 그 기준이다 (서버 시각이 아니라)")
    void factoryClock() {
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(
                running("CNC01", "2026-10-18T09:00", "2026-10-18T10:00"),
                running("CNC02", "2026-10-18T09:30", "2026-10-18T10:05")));

        Dashboard dashboard = service.dashboard();

        assertThat(dashboard.factoryNow()).isEqualTo(LocalDateTime.parse("2026-10-18T10:05"));
        assertThat(dashboard.prodDate()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(dashboard.equipment()).extracting(EquipmentCard::statusMinutes).containsExactly(65L, 35L);
    }

    @Test
    @DisplayName("공장 시계로 30분 넘게 소식이 없는 설비는 통신 없음(online = false)")
    void offline() {
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(
                running("CNC01", "2026-10-18T09:00", "2026-10-18T10:00"),    // 지금(10:40)보다 40분 전
                running("CNC02", "2026-10-18T09:00", "2026-10-18T10:10"),    // 30분 전: 아직 온라인
                running("CNC03", "2026-10-18T09:00", "2026-10-18T10:40")));

        assertThat(service.dashboard().equipment()).extracting(EquipmentCard::online).containsExactly(false, true, true);
    }

    @Test
    @DisplayName("진행 중인 정지가 있으면 사유를 붙이고, 가동 중이면 stop은 null")
    void stop() {
        EquipmentNow down = new EquipmentNow("CNC03", "CNC 밀링 #1", "DOWN", "정지", LocalDateTime.parse("2026-10-18T10:00"),
                "BREAKDOWN", "설비고장", "BRK_SPINDLE", "스핀들 이상", LocalDateTime.parse("2026-10-18T10:00"),
                LocalDateTime.parse("2026-10-18T10:20"), 30, new BigDecimal("0.05"), new BigDecimal("29.0"));
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(down, running("CNC04", "2026-10-18T09:00", "2026-10-18T10:20")));

        List<EquipmentCard> cards = service.dashboard().equipment();

        assertThat(cards.get(0).stop().dtSubName()).isEqualTo("스핀들 이상");
        assertThat(cards.get(0).statusMinutes()).isEqualTo(20L);
        assertThat(cards.get(1).stop()).isNull();
    }

    @Test
    @DisplayName("오늘 OEE는 설비 코드로 찾아 붙이고, 집계 전이면 null")
    void todayOee() {
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(
                running("CNC01", "2026-10-18T09:00", "2026-10-18T10:00"),
                running("CNC02", "2026-10-18T09:00", "2026-10-18T10:00")));
        when(oeeMapper.selectDaily(LocalDate.of(2026, 10, 18))).thenReturn(List.of(
                new OeeDaily(LocalDate.of(2026, 10, 18), "CNC02", "MES", 600, 30, 570, 44, 44, new BigDecimal("12.00"),
                        new BigDecimal("95.00"), new BigDecimal("92.63"), new BigDecimal("100.00"), new BigDecimal("88.00"))));

        List<EquipmentCard> cards = service.dashboard().equipment();

        assertThat(cards.get(0).today()).isNull();
        assertThat(cards.get(1).today().oee()).isEqualByComparingTo("88.00");
        assertThat(cards.get(1).today().prodQty()).isEqualTo(44);
    }

    @Test
    @DisplayName("확인 전 알람은 설비별로 나눠 붙이고, 없는 설비는 빈 목록")
    void alarms() {
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(
                running("CNC01", "2026-10-18T09:00", "2026-10-18T10:00"),
                running("CNC03", "2026-10-18T09:00", "2026-10-18T10:00")));
        when(monitoringMapper.selectOpenAlarms()).thenReturn(List.of(
                new OpenAlarm(7, "CNC03", "TEMP_UCL", new BigDecimal("40.2"), new BigDecimal("39.100"), LocalDateTime.parse("2026-10-18T09:50"), null),
                new OpenAlarm(5, "CNC03", "VIBRATION_UCL", new BigDecimal("2.10"), new BigDecimal("1.700"), LocalDateTime.parse("2026-10-18T09:40"), null)));

        List<EquipmentCard> cards = service.dashboard().equipment();

        assertThat(cards.get(0).alarms()).isEmpty();
        assertThat(cards.get(1).alarms()).extracting(OpenAlarm::alarmId).containsExactly(7L, 5L);
    }

    @Test
    @DisplayName("데이터가 하나도 없으면 서버 시각을 쓰고, 설비는 모두 통신 없음")
    void noData() {
        when(monitoringMapper.selectEquipmentNow()).thenReturn(List.of(
                new EquipmentNow("CNC01", "CNC 선반 #1", "IDLE", "대기", null, null, null, null, null, null, null, null, null, null)));

        Dashboard dashboard = service.dashboard();

        assertThat(dashboard.factoryNow()).isEqualTo(SERVER_NOW);
        assertThat(dashboard.equipment().get(0).online()).isFalse();
        assertThat(dashboard.equipment().get(0).statusMinutes()).isNull();
    }
}
