package com.minimes.auth.config;

import com.minimes.auth.web.AuthFailureHandler;
import com.minimes.auth.web.TokenResolver;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 로그인과 권한.
 *
 * - 로그인하면 서버가 JWT(사번, 이름, 역할, 만료 시각)를 서명해 준다. 서버는 세션을 두지 않고 요청마다 서명과 만료만 확인한다.
 * - POP는 Authorization: Bearer 헤더로, 관리자 웹은 HttpOnly 쿠키로 같은 토큰을 보낸다 (TokenResolver).
 * - 역할: OPERATOR(작업자), QC(품질), ADMIN(관리자). 작업지시·집계는 관리자, 보류·해제는 품질·관리자만.
 *   보류·해제는 서비스(LotHoldService)도 DB의 역할을 한 번 더 본다 (토큰을 받은 뒤 역할이 바뀐 경우).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /** 로그인 없이 열리는 주소. 이 주소에서는 토큰을 읽지도 않는다 (만료된 쿠키가 남아 있어도 로그인 화면은 열리게) */
    public static final RequestMatcher PUBLIC = new OrRequestMatcher(
            path("/login"), path("/css/**"), path("/js/**"), path("/webjars/**"), path("/favicon.ico"), path("/error"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"),
            path("/actuator/health/**"), path("/swagger-ui.html"), path("/swagger-ui/**"), path("/v3/api-docs/**"));

    private static final int MIN_SECRET_BYTES = 32;   // HS256은 256비트 이상의 키

    private static RequestMatcher path(String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(pattern);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenResolver tokenResolver,
                                            AuthFailureHandler failureHandler) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");      // 토큰의 role(예: QC) → 권한 ROLE_QC
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        http
                // 쿠키는 SameSite=Strict라 다른 사이트에서 보낸 요청에는 실리지 않고, POP는 쿠키 대신 헤더를 쓴다.
                // 그래서 CSRF 토큰을 따로 두지 않는다 (decisions.md)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/work-orders", "/api/work-orders/*/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/equipment-limits/calculate", "/api/production-daily/aggregate").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/lots/*/hold", "/api/lots/*/release").hasAnyRole("QC", "ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(failureHandler)
                        .accessDeniedHandler(failureHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(failureHandler).accessDeniedHandler(failureHandler));
        return http.build();
    }

    @Bean
    SecretKey jwtKey(AuthProperties properties) {
        String secret = properties.jwtSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET이 없어 임시 서명 키를 만들었습니다. 서버를 다시 켜면 모두 다시 로그인해야 합니다.");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return new SecretKeySpec(random, "HmacSHA256");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다 (지금 " + bytes.length + "바이트).");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /** 서명(HS256)과 만료 시각을 확인한다 */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        return NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
