package com.minimes.auth.config;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한 작업자의 사번. 토큰의 subject를 컨트롤러 파라미터로 받는다.
 * 요청 본문의 사번을 믿지 않고, 서버가 서명을 확인한 토큰에서 꺼낸다.
 * 예: {@code public ToolResponse change(@LoginWorker String workerId, ...)}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "subject")
@Parameter(hidden = true)
public @interface LoginWorker {
}
