package com.minimes.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 화면으로 실시간 알림을 보내는 WebSocket(STOMP) 설정.
 *
 *   /ws          브라우저가 WebSocket으로 접속하는 주소
 *   /topic/...   서버가 보내는 주제. 구독한 화면 모두에게 같은 메시지가 간다
 *
 * 브로커는 Spring에 들어 있는 메모리 브로커를 쓴다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");   // 같은 주소(origin)의 화면만 접속할 수 있다 (기본값)
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
