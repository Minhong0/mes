package com.minimes.auth.web;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/** 화면마다 맨 위 메뉴(fragments/nav.html)에 로그인한 사람을 보여 주려고, 모든 화면 모델에 넣는다. */
@ControllerAdvice(annotations = Controller.class)
public class LoginWorkerAdvice {

    private static final Map<String, String> ROLE_NAMES = Map.of("ADMIN", "관리자", "QC", "품질", "OPERATOR", "작업자");

    @ModelAttribute("loginWorker")
    public LoginWorkerView loginWorker(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String role = jwt.getClaimAsString("role");
        return new LoginWorkerView(jwt.getSubject(), jwt.getClaimAsString("name"), role, ROLE_NAMES.getOrDefault(role, role));
    }

    public record LoginWorkerView(String workerId, String workerName, String role, String roleName) {
    }
}
