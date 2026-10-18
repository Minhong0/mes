package com.minimes.equipment.service;

import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.domain.Downtime;
import com.minimes.equipment.repository.DowntimeRepository;
import com.minimes.equipment.service.DowntimeService.ReasonCommand;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** POP 정지 사유 입력: 설비가 남긴 정지 목록, 사유 확정과 입력 확인 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@Sql({"/sql/equipment-fixture.sql", "/sql/downtime-fixture.sql"})
class DowntimeServiceTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 17, 9, 0);

    @Autowired
    DowntimeService downtimeService;

    @Autowired
    DowntimeRepository downtimeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents events;

    /** 설비가 보낸 정지. minutes가 null이면 아직 정지 중 */
    Downtime stop(LocalDateTime startedAt, Integer minutes) {
        Downtime downtime = Downtime.startFromEquipment("CNC01", "BREAKDOWN", "BRK_SPINDLE", startedAt, startedAt);
        if (minutes != null) {
            downtime.end(startedAt.plusMinutes(minutes));
        }
        return downtimeRepository.save(downtime);
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("최근 정지부터 limit건, 설비가 남긴 것만 (부트캠프 과거 실적은 빠진다)")
    void recent() {
        stop(T0, 20);
        stop(T0.plusHours(1), 10);
        Downtime open = stop(T0.plusHours(2), null);
        jdbcTemplate.update("""
                INSERT INTO downtime (equip_code, started_at, ended_at, duration_min, dt_cat, source, created_at)
                VALUES ('CNC01', '2026-10-17 12:00:00', '2026-10-17 12:30:00', 30, 'MATERIAL', 'BOOTCAMP', CURRENT_TIMESTAMP)
                """);

        assertThat(downtimeService.recent("CNC01", 2))
                .extracting(Downtime::getStartedAt)
                .containsExactly(open.getStartedAt(), T0.plusHours(1));
        expectError(ErrorCode.INVALID_INPUT, () -> downtimeService.recent("NOPE", 10));
    }

    @Test
    @DisplayName("사유 확정: 대분류·소분류·메모와 확정한 작업자·시각이 남고, 모니터링에 알린다. 정지 중이어도 된다")
    void confirmReason() {
        Downtime open = stop(T0, null);

        Downtime confirmed = downtimeService.confirmReason(open.getDowntimeId(),
                new ReasonCommand("W1001", "MATERIAL", "MAT_NOT_RECEIVED", "소재 입고 지연"));

        assertThat(confirmed.getDtCat()).isEqualTo("MATERIAL");
        assertThat(confirmed.getDtSub()).isEqualTo("MAT_NOT_RECEIVED");
        assertThat(confirmed.getNote()).isEqualTo("소재 입고 지연");
        assertThat(confirmed.getWorkerId()).isEqualTo("W1001");
        assertThat(confirmed.getReasonAt()).isNotNull();
        assertThat(confirmed.getEndedAt()).isNull();   // 정지는 그대로 진행 중
        assertThat(events.stream(DowntimeReasonChangedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("소분류는 비워도 된다")
    void categoryOnly() {
        Downtime done = stop(T0, 15);

        Downtime confirmed = downtimeService.confirmReason(done.getDowntimeId(), new ReasonCommand("W1001", "MATERIAL", null, null));

        assertThat(confirmed.getDtCat()).isEqualTo("MATERIAL");
        assertThat(confirmed.getDtSub()).isNull();
    }

    @Test
    @DisplayName("입력 확인: 모르는 대분류·소분류, 대분류에 속하지 않는 소분류, 없는 정지, 없는 작업자")
    void validation() {
        long id = stop(T0, 15).getDowntimeId();

        expectError(ErrorCode.INVALID_INPUT, () -> downtimeService.confirmReason(id, new ReasonCommand("W1001", "LUNCH", null, null)));
        expectError(ErrorCode.INVALID_INPUT, () -> downtimeService.confirmReason(id, new ReasonCommand("W1001", "MATERIAL", "MAT_X", null)));
        expectError(ErrorCode.INVALID_INPUT, () -> downtimeService.confirmReason(id, new ReasonCommand("W1001", "MATERIAL", "BRK_SPINDLE", null)));
        expectError(ErrorCode.DOWNTIME_NOT_FOUND, () -> downtimeService.confirmReason(999_999, new ReasonCommand("W1001", "MATERIAL", null, null)));
        expectError(ErrorCode.WORKER_NOT_FOUND, () -> downtimeService.confirmReason(id, new ReasonCommand("W0000", "MATERIAL", null, null)));
        assertThat(downtimeRepository.findById(id).orElseThrow().getDtCat()).isEqualTo("BREAKDOWN");   // 그대로
    }

    @Test
    @DisplayName("부트캠프 과거 실적의 사유는 바꿀 수 없다")
    void keepBootcamp() {
        jdbcTemplate.update("""
                INSERT INTO downtime (equip_code, started_at, ended_at, duration_min, dt_cat, source, created_at)
                VALUES ('CNC01', '2025-10-01 09:00:00', '2025-10-01 09:30:00', 30, 'MATERIAL', 'BOOTCAMP', CURRENT_TIMESTAMP)
                """);
        long id = jdbcTemplate.queryForObject("SELECT MAX(downtime_id) FROM downtime", Long.class);

        expectError(ErrorCode.INVALID_STATUS_TRANSITION,
                () -> downtimeService.confirmReason(id, new ReasonCommand("W1001", "BREAKDOWN", "BRK_SPINDLE", null)));
    }
}
