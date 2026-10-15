package com.minimes.equipment.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 mes.mqtt.* 설정을 타입이 있는 객체로 받는다.
 * @Value("${...}")를 여기저기 쓰는 대신 한곳에 모아 두면, 어떤 설정이 있는지 이 파일만 보면 된다.
 *
 * @param enabled   false면 수집기를 아예 만들지 않는다 (브로커 없이 돌리는 테스트용)
 * @param brokerUrl 예: tcp://localhost:1883
 * @param clientId  브로커가 세션을 기억하는 이름. 고정해야 재접속했을 때 같은 세션을 이어받는다.
 * @param topic     구독할 토픽. +는 한 단계 와일드카드 (설비 코드 자리)
 */
@ConfigurationProperties(prefix = "mes.mqtt")
public record MqttProperties(boolean enabled, String brokerUrl, String clientId, String topic) {
}
