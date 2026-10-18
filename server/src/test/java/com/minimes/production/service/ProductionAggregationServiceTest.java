package com.minimes.production.service;

import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.service.EquipmentDataService;
import com.minimes.production.domain.ProductionDaily;
import com.minimes.production.domain.ProductionDailyId;
import com.minimes.production.domain.ProductionSource;
import com.minimes.production.repository.ProductionDailyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 데이터 → 일별 생산 실적(production_daily) 집계.
 * 메시지를 수집기와 같은 경로(EquipmentDataService.save)로 넣어 상태 이력까지 만든 뒤 집계한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/equipment-fixture.sql")
class ProductionAggregationServiceTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 16);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(LocalDateTime.of(2026, 10, 16, 12, 0).atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    EquipmentDataService equipmentDataService;

    @Autowired
    ProductionAggregationService aggregationService;

    @Autowired
    ProductionDailyRepository productionDailyRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    int sequence;

    /** dateTime(예: "2026-10-16T09:00")에 CNC01이 보낸 메시지. cycleCount = 누적 가공 수량 */
    void send(String dateTime, int cycleCount, String status, String dtCat, String dtSub) {
        sequence++;
        equipmentDataService.save(new EquipmentMessage("CNC01-AGG-%06d".formatted(sequence), "CNC01",
                OffsetDateTime.of(LocalDateTime.parse(dateTime), ZoneOffset.ofHours(9)), cycleCount, status,
                new BigDecimal("1.2"), new BigDecimal("35.0"), dtCat, dtSub));
    }

    void run(String dateTime, int cycleCount) {
        send(dateTime, cycleCount, "RUN", null, null);
    }

    ProductionDaily daily(LocalDate date) {
        return productionDailyRepository.findById(new ProductionDailyId(date, "CNC01")).orElseThrow();
    }

    @Test
    @DisplayName("08:00~10:00 중 30분 정지, 6개 생산 → 계획 120분, 비가동 30분, 생산 6개")
    void oneDay() {
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:12", 1);
        run("2026-10-16T08:24", 2);
        send("2026-10-16T08:30", 2, "DOWN", "BREAKDOWN", "BRK_SPINDLE");
        run("2026-10-16T09:00", 2);
        run("2026-10-16T09:12", 3);
        run("2026-10-16T09:24", 4);
        run("2026-10-16T09:36", 5);
        run("2026-10-16T10:00", 6);

        int saved = aggregationService.aggregate(DAY);

        assertThat(saved).isEqualTo(1);
        ProductionDaily daily = daily(DAY);
        assertThat(daily.getSource()).isEqualTo(ProductionSource.MES);
        assertThat(daily.getPlannedMin()).isEqualTo(120);     // 08:00 ~ 마지막 메시지 10:00
        assertThat(daily.getDowntimeMin()).isEqualTo(30);     // 08:30 ~ 09:00 DOWN
        assertThat(daily.getProdQty()).isEqualTo(6);
        assertThat(daily.getGoodQty()).isEqualTo(6);
        assertThat(daily.getDefectQty()).isZero();
        assertThat(daily.getIdealCtMin()).isEqualByComparingTo("12.00");   // 기준정보의 이론 사이클타임
    }

    @Test
    @DisplayName("자정을 넘긴 상태는 날짜별로 나뉜다: 23:00~01:00 가동 → 전날 60분, 다음 날 60분")
    void splitAtMidnight() {
        run("2026-10-16T23:00", 0);
        run("2026-10-16T23:24", 2);
        run("2026-10-16T23:48", 3);
        run("2026-10-17T00:12", 5);
        run("2026-10-17T00:36", 7);
        run("2026-10-17T01:00", 10);

        aggregationService.aggregate(DAY);
        aggregationService.aggregate(DAY.plusDays(1));

        assertThat(daily(DAY).getPlannedMin()).isEqualTo(60);
        assertThat(daily(DAY).getProdQty()).isEqualTo(3);
        assertThat(daily(DAY.plusDays(1)).getPlannedMin()).isEqualTo(60);
        assertThat(daily(DAY.plusDays(1)).getProdQty()).isEqualTo(7);   // 전날 마지막 3 → 5 → 7 → 10
    }

    @Test
    @DisplayName("시뮬레이터를 다시 켜서 누적 카운트가 0부터 다시 시작해도 생산 수량이 이어진다")
    void counterReset() {
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:12", 1);
        run("2026-10-16T08:24", 2);
        run("2026-10-16T08:30", 0);   // 재시작
        run("2026-10-16T08:42", 1);

        aggregationService.aggregate(DAY);

        assertThat(daily(DAY).getProdQty()).isEqualTo(3);   // 재시작 전 2개 + 재시작 후 1개
    }

    @Test
    @DisplayName("다시 집계하면 행을 새로 만들지 않고 값을 바꾼다 (늦게 온 데이터 반영)")
    void reAggregate() {
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:12", 1);
        aggregationService.aggregate(DAY);
        assertThat(daily(DAY).getProdQty()).isEqualTo(1);

        run("2026-10-16T08:24", 2);
        aggregationService.aggregate(DAY);

        assertThat(productionDailyRepository.count()).isEqualTo(1);
        assertThat(daily(DAY).getProdQty()).isEqualTo(2);
        assertThat(daily(DAY).getPlannedMin()).isEqualTo(24);
    }

    @Test
    @DisplayName("시뮬레이터가 꺼져 있던 시간은 계획 시간에서 빠진다: 08:00~09:00, 12:00~13:00 → 120분")
    void excludeOfflineTime() {
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:30", 2);
        run("2026-10-16T09:00", 5);
        run("2026-10-16T12:00", 0);   // 3시간 뒤 다시 켬 (카운트도 0부터)
        run("2026-10-16T12:30", 3);
        run("2026-10-16T13:00", 5);

        aggregationService.aggregate(DAY);

        assertThat(daily(DAY).getPlannedMin()).isEqualTo(120);
        assertThat(daily(DAY).getProdQty()).isEqualTo(10);
    }

    /**
     * POP 실적 한 건 (불량 수량만 집계에 쓰인다). 공정 작업은 이 테스트와 상관없어 아무 번호나 넣는다.
     * H2는 MSSQL 모드에서 MSSQL처럼 UNIQUE 컬럼의 NULL끼리도 중복으로 보므로 요청 ID를 넣는다.
     */
    void popResult(String endedAt, int goodQty, int defectQty, boolean cancelled) {
        sequence++;
        jdbcTemplate.update("""
                INSERT INTO work_result (wo_op_id, equip_code, worker_id, started_at, ended_at, good_qty, defect_qty, source,
                                         cancelled, cancelled_at, created_at, request_id)
                VALUES (1, 'CNC01', 'W1001', ?, ?, ?, ?, 'POP', ?, ?, CURRENT_TIMESTAMP, ?)
                """, LocalDateTime.parse(endedAt).minusHours(1), LocalDateTime.parse(endedAt), goodQty, defectQty,
                cancelled, cancelled ? LocalDateTime.parse(endedAt) : null, "agg-req-" + sequence);
    }

    @Test
    @DisplayName("POP 불량이 품질에 들어간다: 생산 6개 중 그날 불량 2개 → 양품 4 (취소한 실적, 다른 날 실적은 빼고)")
    void defectsFromPop() {
        for (int i = 0; i <= 6; i++) {
            run("2026-10-16T%02d:%02d".formatted(8 + i * 12 / 60, i * 12 % 60), i);   // 08:00부터 12분마다 1개
        }
        popResult("2026-10-16T09:00", 3, 2, false);
        popResult("2026-10-16T09:10", 0, 5, true);    // 취소
        popResult("2026-10-17T09:00", 0, 3, false);   // 다음 날

        aggregationService.aggregate(DAY);

        assertThat(daily(DAY).getProdQty()).isEqualTo(6);
        assertThat(daily(DAY).getDefectQty()).isEqualTo(2);
        assertThat(daily(DAY).getGoodQty()).isEqualTo(4);
    }

    @Test
    @DisplayName("불량이 설비 생산 수량보다 많으면 생산 수량까지만 반영한다 (prod = good + defect 제약을 지킨다)")
    void defectsCappedAtProduced() {
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:24", 2);
        popResult("2026-10-16T08:30", 0, 5, false);

        aggregationService.aggregate(DAY);

        assertThat(daily(DAY).getDefectQty()).isEqualTo(2);
        assertThat(daily(DAY).getGoodQty()).isZero();
    }

    @Test
    @DisplayName("부트캠프 실적이 있는 날은 덮어쓰지 않는다")
    void keepBootcamp() {
        jdbcTemplate.update("""
                INSERT INTO production_daily (prod_date, equip_code, planned_min, downtime_min, prod_qty, good_qty, defect_qty,
                                              ideal_ct_min, source, created_at)
                VALUES ('2026-10-16', 'CNC01', 960, 60, 70, 69, 1, 12, 'BOOTCAMP', CURRENT_TIMESTAMP)
                """);
        run("2026-10-16T08:00", 0);
        run("2026-10-16T08:12", 1);

        int saved = aggregationService.aggregate(DAY);

        assertThat(saved).isZero();
        assertThat(daily(DAY).getSource()).isEqualTo(ProductionSource.BOOTCAMP);
        assertThat(daily(DAY).getProdQty()).isEqualTo(70);
    }

    @Test
    @DisplayName("데이터가 없는 날은 행을 만들지 않는다")
    void noData() {
        assertThat(aggregationService.aggregate(DAY)).isZero();
        assertThat(productionDailyRepository.count()).isZero();
    }

    @Test
    @DisplayName("다시 집계할 날짜: 받은 메시지의 측정 시각 범위에 걸친 날짜 전부")
    void datesReceivedSince() {
        run("2026-10-16T23:00", 0);
        run("2026-10-18T01:00", 5);

        assertThat(aggregationService.datesReceivedSince(LocalDateTime.of(2026, 10, 16, 0, 0)))
                .containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2));
        assertThat(aggregationService.datesReceivedSince(LocalDateTime.of(2026, 10, 16, 13, 0)))
                .isEmpty();   // 그 뒤에 받은 메시지가 없다 (받은 시각은 고정된 12:00)
    }
}
