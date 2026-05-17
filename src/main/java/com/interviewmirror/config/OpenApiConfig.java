package com.interviewmirror.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger에 토큰 인증 추가하는 메서드

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openAPI() {
    String securitySchemeName = "bearerAuth";

    return new OpenAPI()
        .info(
            new Info()
                .title("모의면접 스마트거울 API")
                .description("JWT 인증이 포함된 API 명세서입니다.")
                .version("v1.0.0"))
        // 모든 API 요청에 보안 설정(자물쇠)을 기본으로 적용
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        // JWT Bearer 방식의 보안 스키마 정의
        .components(
            new Components()
                .addSecuritySchemes(
                    securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
