package com.minimes.auth.service;

import com.minimes.auth.config.AuthProperties;
import com.minimes.master.domain.Worker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** 로그인한 작업자에게 줄 토큰을 만든다. 토큰에는 사번(sub), 이름, 역할, 만료 시각이 들어간다. */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String ISSUER = "mini-mes";

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public IssuedToken issue(Worker worker) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(worker.getWorkerId())
                .claim("name", worker.getWorkerName())
                .claim("role", worker.getRole())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
