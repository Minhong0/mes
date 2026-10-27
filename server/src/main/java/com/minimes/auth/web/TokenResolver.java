package com.minimes.auth.web;

import com.minimes.auth.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * 요청에서 토큰을 꺼낸다. Authorization: Bearer 헤더(POP, Swagger)가 먼저, 없으면 쿠키(관리자 웹).
 * 로그인 없이 열리는 주소에서는 꺼내지 않는다: 만료된 쿠키 때문에 로그인 화면까지 401이 나지 않게.
 */
@Component
@RequiredArgsConstructor
public class TokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver header = new DefaultBearerTokenResolver();
    private final TokenCookie tokenCookie;

    @Override
    public String resolve(HttpServletRequest request) {
        if (SecurityConfig.PUBLIC.matches(request)) {
            return null;
        }
        String token = header.resolve(request);
        return token != null ? token : tokenCookie.read(request);
    }
}
