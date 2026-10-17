package com.minimes.alarm.service;

import com.minimes.alarm.domain.AlarmType;
import com.minimes.alarm.domain.EquipmentAlarm;
import com.minimes.alarm.domain.EquipmentLimit;
import com.minimes.alarm.domain.LimitMetric;
import com.minimes.alarm.repository.EquipmentAlarmRepository;
import com.minimes.alarm.repository.EquipmentLimitRepository;
import com.minimes.common.error.BusinessException;
import com.minimes.common.error.ErrorCode;
import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.service.EquipmentDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 관리한계 경보 규칙. 수집기와 같은 경로(EquipmentDataService.save → 이벤트 → AlarmService)로 메시지를 넣고,
 * equipment_alarm에 남은 행을 확인한다. 한계는 계산 대신 직접 넣는다: 진동 UCL 1.700, 온도 UCL 39.100.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/equipment-fixture.sql")
class AlarmServiceTest {

    @Autowired
    EquipmentDataService equipmentDataService;

    @Autowired
    AlarmService alarmService;

    @Autowired
    EquipmentAlarmRepository alarmRepository;

    @Autowired
    EquipmentLimitRepository limitRepository;

    int sequence;

    @BeforeEach
    void limits() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 0, 0);
        EquipmentLimit vibration = new EquipmentLimit("CNC01", LimitMetric.VIBRATION);
        vibration.recalculate(new BigDecimal("1.25"), new BigDecimal("0.15"), 300, base, base.plusDays(7), base);
        EquipmentLimit temp = new EquipmentLimit("CNC01", LimitMetric.TEMP);
        temp.recalculate(new BigDecimal("36.5"), new BigDecimal("0.8667"), 300, base, base.plusDays(7), base);
        limitRepository.saveAll(List.of(vibration, temp));
    }

    /** 2026-10-15 hh:mm (한국 시각)에 CNC01이 보낸 메시지 */
    void send(String time, String status, String vibration, String temp) {
        sequence++;
        LocalDateTime local = LocalDateTime.parse("2026-10-15T" + time);
        equipmentDataService.save(new EquipmentMessage("CNC01-ALARM-%04d".formatted(sequence), "CNC01",
                OffsetDateTime.of(local, ZoneOffset.ofHours(9)), sequence, status,
                new BigDecimal(vibration), new BigDecimal(temp), null, null));
    }

    @Test
    @DisplayName("한계(1.700) 이하면 알람 없음, 넘으면 측정값·한계·측정 시각으로 알람 1건")
    void exceed() {
        send("10:00", "RUN", "1.70", "36.0");   // 같으면 넘은 것이 아니다
        send("10:12", "RUN", "2.10", "36.0");

        assertThat(alarmRepository.findAll())
                .extracting(EquipmentAlarm::getAlarmType, a -> a.getMeasuredValue().toPlainString(),
                        a -> a.getLimitValue().toPlainString(), EquipmentAlarm::getOccurredAt)
                .containsExactly(tuple(AlarmType.VIBRATION_UCL, "2.10", "1.700", LocalDateTime.of(2026, 10, 15, 10, 12)));
    }

    @Test
    @DisplayName("확인 전 알람이 있으면 계속 넘어도 새로 만들지 않고, 확인한 뒤 다시 넘으면 새 알람")
    void oneOpenAlarmUntilAck() {
        send("10:00", "RUN", "2.10", "36.0");
        send("10:12", "RUN", "2.60", "36.0");
        send("10:24", "RUN", "3.20", "36.0");
        assertThat(alarmRepository.findAll()).hasSize(1);

        alarmService.acknowledge(alarmRepository.findAll().getFirst().getAlarmId(), "kim");
        send("10:36", "RUN", "3.50", "36.0");

        assertThat(alarmRepository.findAll())
                .extracting(a -> a.getMeasuredValue().toPlainString(), EquipmentAlarm::isOpen)
                .containsExactlyInAnyOrder(tuple("2.10", false), tuple("3.50", true));
    }

    @Test
    @DisplayName("진동과 온도는 따로 판단한다")
    void separateMetrics() {
        send("10:00", "RUN", "1.30", "40.2");
        send("10:12", "RUN", "2.10", "41.0");

        assertThat(alarmRepository.findAll()).extracting(EquipmentAlarm::getAlarmType)
                .containsExactlyInAnyOrder(AlarmType.TEMP_UCL, AlarmType.VIBRATION_UCL);
    }

    @Test
    @DisplayName("가동 중이 아니면 판단하지 않는다 (정지·셋업 중 센서 값은 의미가 다르다)")
    void onlyWhileRunning() {
        send("10:00", "DOWN", "2.10", "45.0");
        send("10:12", "SETUP", "2.10", "45.0");

        assertThat(alarmRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("한계를 아직 정하지 않은 설비는 판단하지 않는다")
    void noLimit() {
        limitRepository.deleteAll();
        send("10:00", "RUN", "9.99", "99.0");

        assertThat(alarmRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("이미 확인한 알람을 다시 확인하면 ALARM_ALREADY_ACKNOWLEDGED")
    void ackTwice() {
        send("10:00", "RUN", "2.10", "36.0");
        long alarmId = alarmRepository.findAll().getFirst().getAlarmId();
        alarmService.acknowledge(alarmId, "kim");

        assertThatThrownBy(() -> alarmService.acknowledge(alarmId, "lee"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALARM_ALREADY_ACKNOWLEDGED);
    }
}
