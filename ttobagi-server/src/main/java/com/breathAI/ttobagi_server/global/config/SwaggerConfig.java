package com.breathAI.ttobagi_server.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Swagger 문서 설정, 접속 경로 /swagger-ui.html
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${swagger.contact-name}")
    private String contactName;

    @Value("${swagger.contact-email}")
    private String contactEmail;

    @Value("${swagger.production-url}")
    private String productionUrl;

    // API 문서 기본 정보 및 JWT 인증 스키마 구성
    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("또타24 현행화 시스템 API Documentation")
                .version("1.0.0")
                .description("'또타24 현행화 시스템'의 백엔드 API 명세서입니다.")
                .contact(new Contact()
                        .name(contactName)
                        .email(contactEmail));

        List<Server> servers = getServers();

        String jwtScheme = "bearerAuth";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtScheme);
        Components components = new Components()
                .addSecuritySchemes(jwtScheme, new SecurityScheme()
                        .name("Authorization")
                        .type(SecurityScheme.Type.HTTP)
                        .in(SecurityScheme.In.HEADER)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(info)
                .servers(servers)
                .components(components)
                .addSecurityItem(securityRequirement);
    }

    // Swagger UI 상단 드롭다운에 표시할 서버 목록 (로컬, 운영)
    private List<Server> getServers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("로컬 개발 서버"),
                
                new Server()
                        .url(productionUrl)
                        .description("운영 서버")
        );
    }
}
