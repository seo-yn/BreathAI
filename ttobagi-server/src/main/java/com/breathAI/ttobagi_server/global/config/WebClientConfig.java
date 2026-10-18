package com.breathAI.ttobagi_server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

// AI 서버 호출용 WebClient 설정
@Configuration
public class WebClientConfig {

    // AI 서버 주소 (환경변수 TTOBAGI_AI_SERVER)
    @Value("${ai.server.url}")
    private String aiServerUrl;

    // AI 서버 요청 인증 토큰 (환경변수 TTOBAGI_AI_API_TOKEN)
    @Value("${ai.api-token}")
    private String aiApiToken;

    // AI 서버를 기본 주소로 갖고 모든 요청에 인증 토큰을 붙이는 WebClient 생성
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(aiServerUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiApiToken)
                .build();
    }
}
