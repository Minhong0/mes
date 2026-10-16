package com.minimes.equipment.service;

import com.minimes.equipment.domain.Downtime;
import com.minimes.equipment.domain.DowntimeSource;
import com.minimes.equipment.domain.EquipmentStatus;
import com.minimes.equipment.domain.EquipmentStatusLog;
import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.repository.DowntimeRepository;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.equipment.repository.EquipmentStatusLogRepository;
import com.minimes.master.repository.EquipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 메시지 상태가 바뀔 때 상태 이력(equipment_status_log)과 비가동 기록(downtime)이 맞게 남는지 본다.
 * 메시지를 시간 순서대로 save()에 넣고, 마지막에 테이블에 남은 행을 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/equipment-fixture.sql")
class EquipmentStatusServiceTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(LocalDateTime.of(2026, 10, 15, 12, 0).atZone(zone).toInstant(), zone);
        }
    }

    @Autowired
    EquipmentDataService equipmentDataService;

    @Autowired
    EquipmentDataRepository equipmentDataRepository;

    @Autowired
    EquipmentStatusLogRepository statusLogRepository;

    @Autowired
    DowntimeRepository downtimeRepository;

    @Autowired
    EquipmentRepository equipmentRepository;

    int sequence;

    /** 2026-10-15 hh:mm:ss (한국 시각)에 CNC01이 보낸 메시지 */
    EquipmentMessage message(String time, String status, String dtCat, String dtSub) {
        sequence++;
        LocalDateTime local = LocalDateTime.parse("2026-10-15T" + time);
        return new EquipmentMessage("CNC01-TEST-%06d".formatted(sequence), "CNC01",
                OffsetDateTime.of(local, ZoneOffset.ofHours(9)), sequence, status,
                new BigDecimal("1.2"), new BigDecimal("35.0"), dtCat, dtSub);
    }

    EquipmentMessage run(String time) {
        return message(time, "RUN", null, null);
    }

    List<EquipmentStatusLog> logs() {
        return statusLogRepository.findByEquipCodeOrderByStartedAt("CNC01");
    }

    LocalDateTime at(String time) {
        return LocalDateTime.parse("2026-10-15T" + time);
    }

    @Test
    @DisplayName("RUN → DOWN → RUN: 상태 이력 3행, 비가동 1건(36분), 설비 현재 상태는 RUN")
    void runDownRun() {
        equipmentDataService.save(run("09:00:00"));
        equipmentDataService.save(run("09:12:00"));
        equipmentDataService.save(message("09:24:00", "DOWN", "BREAKDOWN", "BRK_SPINDLE"));
        equipmentDataService.save(run("10:00:00"));
        equipmentDataService.save(run("10:12:00"));

        assertThat(logs()).extracting(EquipmentStatusLog::getStatus, EquipmentStatusLog::getStartedAt, EquipmentStatusLog::getEndedAt)
                .containsExactly(
                        tuple(EquipmentStatus.RUN, at("09:00:00"), at("09:24:00")),
                        tuple(EquipmentStatus.DOWN, at("09:24:00"), at("10:00:00")),
                        tuple(EquipmentStatus.RUN, at("10:00:00"), null));

        assertThat(downtimeRepository.findByEquipCodeAndSourceOrderByStartedAt("CNC01", DowntimeSource.EQUIP))
                .singleElement().satisfies(downtime -> {
                    assertThat(downtime.getDtCat()).isEqualTo("BREAKDOWN");
                    assertThat(downtime.getDtSub()).isEqualTo("BRK_SPINDLE");
                    assertThat(downtime.getStartedAt()).isEqualTo(at("09:24:00"));
                    assertThat(downtime.getEndedAt()).isEqualTo(at("10:00:00"));
                    assertThat(downtime.getDurationMin()).isEqualTo(36);
                });

        assertThat(equipmentRepository.findById("CNC01").orElseThrow().getStatus()).isEqualTo("RUN");
        assertThat(equipmentDataRepository.count()).isEqualTo(5);   // 원본은 메시지 수만큼 그대로
    }

    @Test
    @DisplayName("정지 중에는 비가동 기록이 열려 있고(ended_at 없음), 설비 현재 상태는 SETUP")
    void openStop() {
        equipmentDataService.save(run("09:00:00"));
        equipmentDataService.save(message("09:30:00", "SETUP", "SETUP", "SETUP_JIG"));

        Downtime open = downtimeRepository
                .findFirstByEquipCodeAndSourceAndEndedAtIsNullOrderByStartedAtDesc("CNC01", DowntimeSource.EQUIP)
                .orElseThrow();
        assertThat(open.getDtCat()).isEqualTo("SETUP");
        assertThat(open.getDurationMin()).isNull();
        assertThat(equipmentRepository.findById("CNC01").orElseThrow().getStatus()).isEqualTo("SETUP");
    }

    @Test
    @DisplayName("정지 시간은 분 단위로 반올림한다: 12분 40초 → 13분")
    void roundDuration() {
        equipmentDataService.save(message("09:00:00", "DOWN", "MATERIAL", "MAT_NOT_RECEIVED"));
        equipmentDataService.save(run("09:12:40"));

        assertThat(downtimeRepository.findByEquipCodeAndSourceOrderByStartedAt("CNC01", DowntimeSource.EQUIP))
                .extracting(Downtime::getDurationMin).containsExactly(13);
    }

    @Test
    @DisplayName("상태 이력 시각은 초 아래를 버린다 (DATETIME2(0) 컬럼)")
    void truncateToSeconds() {
        equipmentDataService.save(new EquipmentMessage("CNC01-TEST-FRACTION", "CNC01",
                OffsetDateTime.parse("2026-10-15T09:00:00.9+09:00"), 1, "RUN", null, null, null, null));

        assertThat(logs()).extracting(EquipmentStatusLog::getStartedAt).containsExactly(at("09:00:00"));
    }

    @Test
    @DisplayName("지금 상태보다 이른 시각의 메시지(순서가 뒤바뀜)는 원본만 저장하고 상태 이력은 그대로 둔다")
    void ignoreLateMessage() {
        equipmentDataService.save(run("09:00:00"));
        equipmentDataService.save(message("09:24:00", "DOWN", "BREAKDOWN", "BRK_SPINDLE"));
        equipmentDataService.save(run("09:20:00"));   // 09:24 DOWN보다 먼저 일어난 일이 늦게 도착

        assertThat(logs()).extracting(EquipmentStatusLog::getStatus)
                .containsExactly(EquipmentStatus.RUN, EquipmentStatus.DOWN);
        assertThat(equipmentDataRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 메시지가 다시 와도 상태 이력은 한 번만 바뀐다")
    void duplicateDoesNotChangeStatusTwice() {
        EquipmentMessage down = message("09:24:00", "DOWN", "BREAKDOWN", "BRK_SPINDLE");
        equipmentDataService.save(run("09:00:00"));
        equipmentDataService.save(down);
        equipmentDataService.save(down);

        assertThat(logs()).hasSize(2);
        assertThat(downtimeRepository.findByEquipCodeAndSourceOrderByStartedAt("CNC01", DowntimeSource.EQUIP)).hasSize(1);
    }

    @Test
    @DisplayName("모르는 상태 값은 원본만 저장하고 상태 이력은 건드리지 않는다")
    void unknownStatus() {
        equipmentDataService.save(message("09:00:00", "WARMUP", null, null));

        assertThat(equipmentDataRepository.count()).isEqualTo(1);
        assertThat(logs()).isEmpty();
    }
}
