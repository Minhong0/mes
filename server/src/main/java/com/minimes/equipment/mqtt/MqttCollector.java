package com.minimes.equipment.mqtt;

import com.minimes.equipment.service.EquipmentDataService;
import com.minimes.equipment.service.SaveResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 브로커에서 설비 메시지를 받아 DB에 저장하는 수집기.
 *
 *   시뮬레이터 ──QoS 1──▶ 브로커 ──QoS 1──▶ MqttCollector ──▶ EquipmentDataService ──▶ equipment_data
 *
 * - 영속 세션(cleanSession=false): 서버가 꺼져 있는 동안 온 메시지를 브로커가 보관했다가, 다시 접속하면 보내 준다.
 * - 수신 확인(PUBACK)은 messageArrived가 정상으로 끝난 뒤에 브로커로 간다. 즉 DB에 저장한 뒤에야 "받았다"고 답한다.
 *   저장 중 DB 오류가 나면 예외를 밖으로 던져 확인을 보내지 않고, 브로커가 그 메시지를 다시 보내게 한다.
 * - 다시 받은 메시지는 EquipmentDataService가 messageId로 걸러 한 번만 저장한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mes.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MqttCollector implements SmartLifecycle, MqttCallbackExtended {

    private static final int QOS_AT_LEAST_ONCE = 1;

    // 처음 접속 재시도 대기: 1초에서 시작해 실패할 때마다 두 배, 최대 30초 (시뮬레이터와 같은 지수 백오프)
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final MqttProperties properties;
    private final EquipmentDataService equipmentDataService;
    private final JsonMapper jsonMapper;

    private MqttClient client;
    private Thread connectThread;
    private volatile boolean running;   // volatile: 다른 스레드(접속 스레드)가 바뀐 값을 바로 보도록

    // ── 시작과 종료: Spring이 모든 빈을 만든 뒤 start(), 종료할 때 stop()을 부른다 (SmartLifecycle) ──

    @Override
    public void start() {
        try {
            client = new MqttClient(properties.brokerUrl(), properties.clientId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT 클라이언트를 만들 수 없습니다: " + properties.brokerUrl(), e);
        }
        client.setCallback(this);
        running = true;
        // 브로커가 꺼져 있어도 서버(API, 화면)는 떠야 하므로, 접속은 별도 스레드에서 성공할 때까지 재시도한다.
        connectThread = Thread.ofVirtual().name("mqtt-connect").start(this::connectWithRetry);
    }

    @Override
    public void stop() {
        running = false;
        connectThread.interrupt();   // 재시도 대기 중이면 깨워서 끝낸다
        try {
            if (client.isConnected()) {
                client.disconnect();   // 처리 중인 메시지가 끝나기를 기다린 뒤 끊는다
            }
            client.close();
        } catch (MqttException e) {
            log.warn("[MQTT] 종료 중 오류: {}", e.getMessage());
        }
        log.info("[MQTT] 수집기 종료");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void connectWithRetry() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(false);                                   // 영속 세션
        options.setAutomaticReconnect(true);                              // 한 번 접속한 뒤 끊기면 Paho가 알아서 다시 접속
        options.setMaxReconnectDelay((int) MAX_RETRY_DELAY.toMillis());
        options.setConnectionTimeout(5);                                  // 초

        Duration delay = FIRST_RETRY_DELAY;
        while (running) {
            try {
                client.connect(options);
                return;   // 이후의 재접속은 Paho의 automaticReconnect가 맡는다
            } catch (MqttException e) {
                log.warn("[MQTT] 접속 실패 ({}): {} → {}초 후 재시도", properties.brokerUrl(), e.getMessage(), delay.toSeconds());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    return;   // stop()이 깨웠다
                }
                Duration doubled = delay.multipliedBy(2);
                delay = doubled.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : doubled;
            }
        }
    }

    // ── Paho 콜백: Paho의 스레드가 부른다 ──

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[MQTT] {} ({})", reconnect ? "재접속" : "접속", serverURI);
        // 콜백 안에서 응답을 기다리는 호출(구독)을 하면 Paho 스레드끼리 서로 기다릴 수 있어 별도 스레드에서 한다.
        // 영속 세션이면 브로커가 구독을 기억하지만, 브로커가 세션을 잃은 경우(재시작 등)에 대비해 매번 다시 구독한다.
        Thread.ofVirtual().name("mqtt-subscribe").start(this::subscribe);
    }

    private void subscribe() {
        try {
            client.subscribe(properties.topic(), QOS_AT_LEAST_ONCE);
            log.info("[MQTT] 구독: {}", properties.topic());
        } catch (MqttException e) {
            log.error("[MQTT] 구독 실패: {}", e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] 연결 끊김: {} → 자동 재접속", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        handle(new String(message.getPayload(), StandardCharsets.UTF_8));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 서버는 발행하지 않으므로 쓸 일이 없다
    }

    /**
     * 메시지 한 건 처리. 테스트에서 브로커 없이 부를 수 있도록 Paho 콜백과 분리했다.
     *
     * 오류를 두 종류로 나눈다.
     * - 메시지 자체가 잘못됨(JSON 깨짐, 필수 값 없음): 다시 받아도 똑같이 실패하므로 기록만 하고 버린다.
     *   예외를 던지면 브로커가 같은 메시지를 계속 다시 보내 수집이 멈춘다 (poison message).
     * - DB 오류: 잠깐의 문제일 수 있으므로 예외를 그대로 던져 브로커가 다시 보내게 한다.
     */
    void handle(String payload) {
        EquipmentMessage message;
        try {
            message = jsonMapper.readValue(payload, EquipmentMessage.class);
        } catch (JacksonException e) {
            log.warn("[MQTT] JSON 해석 실패, 버림: {} / {}", e.getOriginalMessage(), payload);
            return;
        }
        if (message.messageId() == null || message.equipCode() == null || message.collectedAt() == null) {
            log.warn("[MQTT] 필수 값 없음, 버림: {}", payload);
            return;
        }

        SaveResult result;
        try {
            result = equipmentDataService.save(message);
        } catch (DataIntegrityViolationException e) {
            result = SaveResult.DUPLICATE;   // 중복 검사를 동시에 통과한 경우 UNIQUE 제약이 막는다 (2차 방어)
        }

        switch (result) {
            case SAVED -> log.debug("[MQTT] 저장 {} {} 누적 {}", message.messageId(), message.status(), message.cycleCount());
            case DUPLICATE -> log.info("[MQTT] 중복 메시지, 건너뜀: {}", message.messageId());
            case UNKNOWN_EQUIPMENT -> log.warn("[MQTT] 기준정보에 없는 설비, 버림: {}", message.equipCode());
        }
    }
}
