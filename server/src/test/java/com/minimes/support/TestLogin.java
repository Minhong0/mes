package com.minimes.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 테스트에서 로그인한 상태로 요청하기.
 * - MockMvc: as(사번, 역할)을 요청에 붙이거나, @Import(TestLogin.AsAdmin.class)로 클래스의 모든 요청을 관리자로
 * - 실제 서버(WebSocket 등): token()으로 서버가 쓰는 키로 서명한 토큰을 만든다
 */
public final class TestLogin {

    private TestLogin() {
    }

    /** 토큰의 사번(subject)과 역할을 정한 로그인 */
    public static RequestPostProcessor as(String workerId, String role) {
        return jwt().jwt(j -> j.subject(workerId).claim("name", "테스트 " + workerId).claim("role", role))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static String token(JwtEncoder encoder, String workerId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(workerId).claim("name", "테스트").claim("role", role)
                .issuedAt(now).expiresAt(now.plus(Duration.ofHours(1))).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** 이 설정을 가져온 테스트 클래스는 MockMvc 요청이 모두 관리자 W9001로 간다 */
    @TestConfiguration
    public static class AsAdmin {
        @Bean
        MockMvcBuilderCustomizer loginAsAdmin() {
            return builder -> builder.defaultRequest(get("/").with(as("W9001", "ADMIN")));
        }
    }
}
