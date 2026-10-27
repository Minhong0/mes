package com.minimes.monitoring.push;

import com.minimes.equipment.mqtt.EquipmentMessage;
import com.minimes.equipment.service.EquipmentDataService;
import com.minimes.monitoring.service.Dashboard;
import com.minimes.monitoring.service.MonitoringService;
import com.minimes.support.TestLogin;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 실제 서버(임의 포트)를 띄우고 STOMP 클라이언트로 /topic/dashboard를 구독해, 메시지 저장부터 화면 도착까지 확인한다.
 * 대시보드 조회 SQL은 MSSQL 전용이라 MonitoringService만 가짜로 바꾸고, 나머지(저장, 이벤트, 스케줄러, WebSocket)는 진짜다.
 * 이 테스트는 데이터를 커밋하므로 다른 테스트와 겹치지 않게 별도 메모리 DB를 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pushtest;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
        "mes.monitoring.push.enabled=true",
        "mes.monitoring.push.interval=PT0.2S"
})
@Sql(scripts = "/sql/equipment-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class DashboardPushTest {

    @LocalServerPort
    int port;

    @Autowired
    EquipmentDataService equipmentDataService;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoBean
    MonitoringService monitoringService;

    @Autowired
    JwtEncoder jwtEncoder;

    WebSocketStompClient client;
    StompSession session;
    final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void connect() throws Exception {
        when(monitoringService.dashboard()).thenReturn(
                new Dashboard(LocalDateTime.of(2026, 10, 18, 10, 5), LocalDate.of(2026, 10, 18), List.of()));

        client = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();   // 접속(handshake)도 로그인이 필요하다
        headers.setBearerAuth(TestLogin.token(jwtEncoder, "W9001", "ADMIN"));
        session = client.connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        session.subscribe(DashboardPusher.TOPIC, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;   // 본문(JSON)을 변환하지 않고 바이트 그대로 받는다
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(300);   // SUBSCRIBE가 서버에 도착할 시간
        received.clear();
    }

    @AfterEach
    void disconnect() {
        session.disconnect();
        client.stop();
    }

    EquipmentMessage message() {
        return new EquipmentMessage(UUID.randomUUID().toString(), "CNC01", OffsetDateTime.parse("2026-10-18T10:05:00+09:00"),
                10, "RUN", new BigDecimal("1.2"), new BigDecimal("35.0"), null, null);
    }

    @Test
    @DisplayName("메시지를 저장하면 구독 중인 화면에 대시보드가 JSON으로 도착한다")
    void pushAfterSave() throws Exception {
        equipmentDataService.save(message());

        String body = received.poll(3, TimeUnit.SECONDS);
        assertThat(body).isNotNull();
        assertThat(body).contains("\"factoryNow\":\"2026-10-18T10:05:00\"", "\"prodDate\":\"2026-10-18\"");
    }

    @Test
    @DisplayName("저장이 롤백되면 보내지 않는다 (커밋된 뒤에만 알림)")
    void noPushAfterRollback() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            equipmentDataService.save(message());
            status.setRollbackOnly();
        });

        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }
}
