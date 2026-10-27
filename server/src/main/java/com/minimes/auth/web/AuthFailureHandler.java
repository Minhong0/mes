package com.minimes.auth.web;

import com.minimes.common.error.ErrorCode;
import com.minimes.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 로그인하지 않았거나(401) 권한이 없을 때(403).
 * API(/api/**)는 다른 오류와 같은 ErrorResponse JSON으로, 화면은 로그인 화면으로 보낸다 (돌아올 주소를 next에 담아).
 * 토큰이 만료됐거나 위조됐으면 쿠키를 지운다.
 */
@Component
@RequiredArgsConstructor
public class AuthFailureHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인하세요.";

    private final JsonMapper jsonMapper;
    private final TokenCookie tokenCookie;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        boolean invalidToken = e instanceof InvalidBearerTokenException;
        if (invalidToken && tokenCookie.read(request) != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.clear(request.isSecure()).toString());
        }
        if (isApi(request)) {
            write(response, ErrorCode.UNAUTHORIZED, invalidToken ? EXPIRED_MESSAGE : ErrorCode.UNAUTHORIZED.getDefaultMessage());
            return;
        }
        String target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        response.sendRedirect("/login?next=" + UriUtils.encode(target, StandardCharsets.UTF_8)
                + (invalidToken ? "&expired" : ""));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        write(response, ErrorCode.NOT_ALLOWED, ErrorCode.NOT_ALLOWED.getDefaultMessage());
    }

    private static boolean isApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/ws");
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
    }
}
