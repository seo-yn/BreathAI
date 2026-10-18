package com.breathAI.ttobagi_server.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// @Async 활성화 설정 (미설정 시 어노테이션 무효)
@Configuration
@EnableAsync
public class AsyncConfig {
}
