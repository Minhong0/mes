package com.minimes.auth.web;

import com.minimes.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * 관리자 웹의 토큰 쿠키.
 * HttpOnly: 화면의 JS가 읽지 못한다 (스크립트가 끼어들어도 토큰을 가져가지 못함).
 * SameSite=Strict: 다른 사이트에서 보낸 요청에는 실리지 않는다 (CSRF 방어).
 */
@Component
@RequiredArgsConstructor
public class TokenCookie {

    private final AuthProperties properties;

    public ResponseCookie create(String token, Instant expiresAt, Instant now, boolean secure) {
        return base(token, secure).maxAge(Duration.between(now, expiresAt)).build();
    }

    public ResponseCookie clear(boolean secure) {
        return base("", secure).maxAge(0).build();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(c -> properties.cookieName().equals(c.getName()))
                .map(Cookie::getValue).filter(v -> !v.isBlank()).findFirst().orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value, boolean secure) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .sameSite("Strict")
                .secure(secure)   // HTTPS로 받았을 때만 Secure (로컬 http에서도 쓸 수 있게)
                .path("/");
    }
}
