package com.minimes.tool.service;

import com.minimes.alarm.domain.AlarmType;
import com.minimes.alarm.domain.EquipmentAlarm;
import com.minimes.alarm.repository.EquipmentAlarmRepository;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.tool.domain.Tool;
import com.minimes.tool.domain.Tool.ToolState;
import com.minimes.tool.domain.ToolChange;
import com.minimes.tool.domain.ToolType;
import com.minimes.tool.repository.ToolRepository;
import com.minimes.tool.service.ToolLifeService.ChangeCommand;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공구 수명: CNC01 인서트(수명 5개)를 설비 메시지의 누적 카운트로 세고, 수명에 이르면 알람, 바꾸면 0부터.
 * 시각은 모두 설비의 공장 시계(메시지의 collectedAt)다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql({"/sql/work-order-fixture.sql", "/sql/pop-fixture.sql"})
class ToolLifeServiceTest {

    static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 3, 9, 0);

    @Autowired
    ToolLifeService toolLifeService;

    @Autowired
    ToolRepository toolRepository;

    @Autowired
    EquipmentDataRepository dataRepository;

    @Autowired
    EquipmentAlarmRepository alarmRepository;

    @BeforeEach
    void insertTool() {
        toolRepository.save(Tool.of("T-CNC01-INS", "CNC01 선삭 인서트", ToolType.INSERT, "CNC01", 5));
    }

    /** CNC01 메시지: T0 + minute분, 누적 카운트 count */
    void message(int minute, int count) {
        dataRepository.save(EquipmentData.of(UUID.randomUUID().toString(), "CNC01", T0.plusMinutes(minute),
                LocalDateTime.now(), count, "RUN", null, null));
    }

    Tool tool() {
        return toolRepository.findById("T-CNC01-INS").orElseThrow();
    }

    List<EquipmentAlarm> toolAlarms() {
        return alarmRepository.findAll().stream().filter(a -> a.getAlarmType() == AlarmType.TOOL_LIFE).toList();
    }

    void expectError(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("설비 데이터가 없으면 세지 않고, 처음 데이터가 오면 그 시각(공장 시계)부터 0개로 센다")
    void startCounting() {
        toolLifeService.refresh();
        assertThat(tool().isCounting()).isFalse();

        message(0, 10);
        toolLifeService.refresh();

        assertThat(tool().getInstalledAt()).isEqualTo(T0);
        assertThat(tool().getUsedCount()).isZero();
    }

    @Test
    @DisplayName("설치 뒤 카운트가 늘어난 만큼 센다. 90%면 교체 임박, 수명이면 알람 한 번 (다시 세도, 확인해도 다시 안 생김)")
    void countAndAlarm() {
        message(0, 10);
        toolLifeService.refresh();
        for (int i = 1; i <= 4; i++) {
            message(i * 12, 10 + i);
        }
        toolLifeService.refresh();
        assertThat(tool().getUsedCount()).isEqualTo(4);
        assertThat(tool().state()).isEqualTo(ToolState.OK);   // 4 / 5 = 80%
        assertThat(toolAlarms()).isEmpty();

        message(60, 15);
        assertThat(toolLifeService.refresh()).isEqualTo(1);

        assertThat(tool().state()).isEqualTo(ToolState.REPLACE);
        assertThat(toolAlarms()).singleElement().satisfies(a -> {
            assertThat(a.getToolCode()).isEqualTo("T-CNC01-INS");
            assertThat(a.getEquipCode()).isEqualTo("CNC01");
            assertThat(a.getMeasuredValue()).isEqualByComparingTo("5");
            assertThat(a.getLimitValue()).isEqualByComparingTo("5");
            assertThat(a.getOccurredAt()).isEqualTo(T0.plusMinutes(60));
        });

        message(72, 16);
        assertThat(toolLifeService.refresh()).isZero();
        toolAlarms().getFirst().acknowledge("MONITOR", LocalDateTime.now());
        message(84, 17);
        assertThat(toolLifeService.refresh()).isZero();
        assertThat(toolAlarms()).hasSize(1);
        assertThat(tool().getUsedCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("시뮬레이터를 다시 켜 카운트가 0부터 시작해도 이어서 센다. 늦게 도착한 메시지가 있어도 결과가 같다")
    void restartAndLateMessage() {
        message(0, 30);
        toolLifeService.refresh();
        message(10, 31);
        message(20, 0);    // 재시작
        message(30, 2);
        toolLifeService.refresh();
        assertThat(tool().getUsedCount()).isEqualTo(3);   // 1 + 0 + 2

        message(25, 1);    // 25분 메시지가 늦게 도착
        toolLifeService.refresh();
        assertThat(tool().getUsedCount()).isEqualTo(3);   // 1 + 0 + 1 + 1
    }

    @Test
    @DisplayName("교체: 바꾼 공구의 가공 수량을 이력에 남기고 0부터 다시 센다. 열린 알람은 바꾼 사람이 확인한 것으로 닫히고, 새 공구도 수명이면 다시 알람")
    void change() {
        message(0, 0);
        toolLifeService.refresh();
        message(10, 6);
        toolLifeService.refresh();
        assertThat(toolAlarms()).singleElement().extracting(EquipmentAlarm::isOpen).isEqualTo(true);

        Tool changed = toolLifeService.change("T-CNC01-INS", new ChangeCommand("W1001", "수명 도달")).tool();

        assertThat(changed.getUsedCount()).isZero();
        assertThat(changed.getInstalledAt()).isEqualTo(T0.plusMinutes(10));   // 마지막 메시지 시각
        assertThat(toolLifeService.changes(null)).singleElement().satisfies(c -> {
            assertThat(c.getUsedCount()).isEqualTo(6);
            assertThat(c.getReason()).isEqualTo("수명 도달");
            assertThat(c.getWorkerId()).isEqualTo("W1001");
            assertThat(c.getChangedAt()).isEqualTo(T0.plusMinutes(10));
        });
        assertThat(toolAlarms()).singleElement().satisfies(a -> {
            assertThat(a.isOpen()).isFalse();
            assertThat(a.getAckBy()).isEqualTo("W1001");
        });

        message(20, 9);
        toolLifeService.refresh();
        assertThat(tool().getUsedCount()).isEqualTo(3);
        message(30, 11);
        assertThat(toolLifeService.refresh()).isEqualTo(1);   // 새 공구의 알람 (바꾸기 전 알람과 같은 시각이어도 따로)
        assertThat(toolAlarms()).hasSize(2);
        assertThat(toolLifeService.changes("T-CNC01-INS")).extracting(ToolChange::getUsedCount).containsExactly(6);
    }

    @Test
    @DisplayName("없는 공구는 404, 없는(퇴사한) 작업자는 404")
    void notFound() {
        expectError(ErrorCode.TOOL_NOT_FOUND, () -> toolLifeService.change("T-NONE", new ChangeCommand("W1001", null)));
        expectError(ErrorCode.WORKER_NOT_FOUND, () -> toolLifeService.change("T-CNC01-INS", new ChangeCommand("W1099", null)));
        expectError(ErrorCode.TOOL_NOT_FOUND, () -> toolLifeService.changes("T-NONE"));
    }
}
