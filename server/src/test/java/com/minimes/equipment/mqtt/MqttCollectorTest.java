package com.minimes.equipment.mqtt;

import com.minimes.equipment.domain.EquipmentData;
import com.minimes.equipment.repository.EquipmentDataRepository;
import com.minimes.equipment.service.EquipmentDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 수집기가 메시지 한 건을 받았을 때의 처리 (JSON 해석 → 중복 확인 → 저장). 브로커 없이 handle()을 직접 부른다.
 * test 프로필은 mes.mqtt.enabled=false라 MqttCollector 빈이 없으므로, 필요한 빈을 받아 직접 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/equipment-fixture.sql")
class MqttCollectorTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(LocalDateTime.of(2026, 10, 15, 9, 0, 5).atZone(zone).toInstant(), zone);
        }
    }

    // C# 시뮬레이터가 실제로 보내는 형식 그대로 (소수점 아래 7자리 시각, 정지 사유 없음)
    static final String RUN_MESSAGE = """
            {"messageId":"CNC01-261015090000-000001","equipCode":"CNC01","collectedAt":"2026-10-15T09:00:00.1234567+09:00",
             "cycleCount":1,"status":"RUN","spindleVibration":1.21,"spindleTemp":35.4}
            """;

    @Autowired
    EquipmentDataService equipmentDataService;

    @Autowired
    EquipmentDataRepository equipmentDataRepository;

    @Autowired
    JsonMapper jsonMapper;   // Spring Boot가 설정한 JSON 변환기 (운영과 같은 것)

    MqttCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MqttCollector(new MqttProperties(true, "tcp://unused:1883", "test", "test/#"),
                equipmentDataService, jsonMapper);
    }

    @Test
    @DisplayName("메시지를 받으면 equipment_data에 한 행으로 저장된다")
    void save() {
        collector.handle(RUN_MESSAGE);

        assertThat(equipmentDataRepository.findAll()).singleElement().satisfies(data -> {
            assertThat(data.getMessageId()).isEqualTo("CNC01-261015090000-000001");
            assertThat(data.getEquipCode()).isEqualTo("CNC01");
            assertThat(data.getCollectedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 0, 0, 123_456_700));
            assertThat(data.getReceivedAt()).isEqualTo(LocalDateTime.of(2026, 10, 15, 9, 0, 5));
            assertThat(data.getCycleCount()).isEqualTo(1);
            assertThat(data.getStatus()).isEqualTo("RUN");
            assertThat(data.getSpindleVibration()).isEqualByComparingTo(new BigDecimal("1.21"));
            assertThat(data.getSpindleTemp()).isEqualByComparingTo(new BigDecimal("35.4"));
        });
    }

    @Test
    @DisplayName("다른 시간대(UTC)로 온 시각은 한국 시각으로 바꿔 저장한다")
    void convertTimeZone() {
        collector.handle(RUN_MESSAGE.replace("2026-10-15T09:00:00.1234567+09:00", "2026-10-15T00:00:00Z"));

        assertThat(equipmentDataRepository.findAll()).extracting(EquipmentData::getCollectedAt)
                .containsExactly(LocalDateTime.of(2026, 10, 15, 9, 0));
    }

    @Test
    @DisplayName("같은 messageId가 다시 오면 (재전송) 한 번만 저장된다")
    void duplicate() {
        collector.handle(RUN_MESSAGE);
        collector.handle(RUN_MESSAGE);

        assertThat(equipmentDataRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("정지 메시지의 사유 필드도 해석된다 (저장은 다음 단계에서 downtime 테이블에)")
    void downtimeFields() {
        EquipmentMessage message = jsonMapper.readValue("""
                {"messageId":"CNC01-261015090000-000002","equipCode":"CNC01","collectedAt":"2026-10-15T09:12:00+09:00",
                 "cycleCount":1,"status":"DOWN","spindleVibration":0.05,"spindleTemp":28.9,
                 "downtimeCategory":"BREAKDOWN","downtimeReason":"BRK_SPINDLE"}
                """, EquipmentMessage.class);

        assertThat(message.downtimeCategory()).isEqualTo("BREAKDOWN");
        assertThat(message.downtimeReason()).isEqualTo("BRK_SPINDLE");
    }

    @Test
    @DisplayName("깨진 JSON, 필수 값이 없는 메시지, 모르는 설비는 예외 없이 버린다")
    void discardInvalid() {
        collector.handle("not json");
        collector.handle("""
                {"equipCode":"CNC01","collectedAt":"2026-10-15T09:00:00+09:00","status":"RUN"}
                """);
        collector.handle(RUN_MESSAGE.replace("\"equipCode\":\"CNC01\"", "\"equipCode\":\"CNC99\""));

        assertThat(equipmentDataRepository.count()).isZero();
    }

    @Test
    @DisplayName("DB 오류는 밖으로 던진다 → 수신 확인이 안 가서 브로커가 다시 보낸다")
    void rethrowDatabaseError() {
        EquipmentDataService failingService = mock(EquipmentDataService.class);
        when(failingService.save(any())).thenThrow(new DataAccessResourceFailureException("DB 연결 끊김"));
        MqttCollector failing = new MqttCollector(new MqttProperties(true, "tcp://unused:1883", "test", "test/#"),
                failingService, jsonMapper);

        assertThatThrownBy(() -> failing.handle(RUN_MESSAGE))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
