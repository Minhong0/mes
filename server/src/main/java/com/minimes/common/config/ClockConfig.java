package com.minimes.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각을 Clock 빈으로 주입받는다.
 * LocalDateTime.now()를 직접 부르면 테스트에서 시각을 고정할 수 없어서, 시간에 따라 달라지는 로직(작업지시 번호 등)을 검증하기 어렵다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
