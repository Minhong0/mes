package com.minimes.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger에서 토큰을 넣어 API를 부를 수 있게 한다.
 * POST /api/auth/login으로 받은 accessToken을 [Authorize]에 넣으면 요청마다 Authorization: Bearer 헤더가 붙는다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Mini-MES API")
                        .description("로그인(POST /api/auth/login)으로 받은 토큰을 [Authorize]에 넣고 부르세요. 시연용 계정: W9001 / 1234"))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
