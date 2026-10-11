package com.breathAI.ttobagi_server.global.config;

import com.breathAI.ttobagi_server.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

// JWT 기반 무상태 인증 및 경로별 접근 권한 설정
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 허용 오리진 목록 (환경변수 CORS_ALLOWED_ORIGINS)
    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 비밀번호 단방향 암호화 인코더
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 보안 필터 체인 구성
    // CSRF 비활성화, CORS 허용, 무상태 세션, 경로별 권한, JWT 필터 등록 순
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(allowedOrigins);
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                // 다운로드 파일 이름을 프론트엔드가 읽을 수 있게 한다
                config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
                config.setAllowCredentials(true);
                return config;
            }))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                       if (response.isCommitted()) return;
                       response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                       response.setContentType("application/json;charset=UTF-8");
                       response.getWriter().write("{\"message\": \"ADMIN 계정만 접근 가능합니다.\"}");
                })
            )
            .authorizeHttpRequests(authz -> authz
                // SSE 응답을 마무리하는 내부 재요청은 최초 요청에서 이미 인증을 거쳤으므로 다시 검사하지 않는다
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/dashboard/analyze/callback/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/faq/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/faq/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/faq/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/faq/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
