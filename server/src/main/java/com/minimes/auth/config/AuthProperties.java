package com.minimes.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로그인 토큰 설정 (application.yml의 mes.auth).
 *
 * @param jwtSecret  토큰 서명 키 (HS256, 32바이트 이상). 비우면 서버가 켤 때마다 임시 키를 만든다 (다시 켜면 다시 로그인)
 * @param tokenTtl   토큰 유효 시간. 한 교대(12시간)를 넘지 않게 잡아, 교대가 바뀌면 새로 로그인하게 한다
 * @param cookieName 관리자 웹이 토큰을 담는 쿠키 이름 (POP는 Authorization 헤더로 보낸다)
 */
@ConfigurationProperties(prefix = "mes.auth")
public record AuthProperties(String jwtSecret, Duration tokenTtl, String cookieName) {
}
