package com.minimes.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @Scheduled 메서드를 실행하는 스케줄러를 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
