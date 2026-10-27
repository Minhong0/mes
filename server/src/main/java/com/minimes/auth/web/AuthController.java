package com.minimes.auth.web;

import com.minimes.auth.service.AuthService;
import com.minimes.auth.service.AuthService.LoginResult;
import com.minimes.master.web.MasterResponses.WorkerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@Tag(name = "로그인", description = "사번·비밀번호로 토큰 받기. POP는 Authorization: Bearer 헤더로, 관리자 웹은 쿠키로 보낸다")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenCookie tokenCookie;
    private final Clock clock;

    @Operation(summary = "로그인", description = "토큰(12시간)과 작업자 정보. 관리자 웹을 위해 같은 토큰을 HttpOnly 쿠키로도 준다. "
            + "사번이 없거나 비밀번호가 틀리면 401 LOGIN_FAILED (어느 쪽이 틀렸는지는 알려 주지 않는다)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                               HttpServletResponse response) {
        LoginResult result = authService.login(req.workerId().trim(), req.password());
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.create(result.token().value(),
                result.token().expiresAt(), clock.instant(), request.isSecure()).toString());
        return new LoginResponse(result.token().value(), "Bearer", result.token().expiresAt(),
                WorkerResponse.from(result.worker()));
    }

    @Operation(summary = "로그아웃", description = "관리자 웹의 토큰 쿠키를 지운다. POP는 들고 있던 토큰을 버리면 된다")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, tokenCookie.clear(request.isSecure()).toString());
    }

    @Operation(summary = "로그인한 작업자", description = "토큰에 담긴 사번, 이름, 역할, 만료 시각")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(jwt);
    }

    public record LoginRequest(@NotBlank(message = "사번은 필수입니다.") String workerId,
                               @NotBlank(message = "비밀번호는 필수입니다.") String password) {
    }

    /** accessToken: 요청마다 Authorization: Bearer {accessToken}으로 보낸다. expiresAt이 지나면 다시 로그인 */
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, WorkerResponse worker) {
    }

    public record MeResponse(String workerId, String workerName, String role, Instant expiresAt) {
        public static MeResponse from(Jwt jwt) {
            return new MeResponse(jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("role"),
                    jwt.getExpiresAt());
        }
    }
}
