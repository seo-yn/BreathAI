package com.breathAI.ttobagi_server.global.util;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// JWT 발급 및 검증 유틸
@Component
public class JwtUtil {
    
    // 서명 키, HS256 요구사항에 따라 32자 이상 필요
    @Value("${jwt.secret}")
    private String secret;

    // 액세스 토큰 유효기간(ms)
    @Value("${jwt.expiration}")
    private Long expiration;

    // 리프레시 토큰 유효기간(ms)
    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 로그인 시 발급하는 액세스 토큰 생성
    public String generateAccessToken(User user) {
        return generateToken(user, expiration);
    }

    // 액세스 토큰 재발급용 리프레시 토큰 생성
    public String generateRefreshToken(User user) {
        return generateToken(user, refreshExpiration);
    }

    // 사용자 정보를 claim에 담은 토큰 생성
    private String generateToken(User user, long expirationTime) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("email", user.getEmail());
        claims.put("role", user.getRole().name());

        return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(user.getEmail())
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();
    }

    // 토큰 파싱 및 서명 검증 후 claim 반환
    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                        .setSigningKey(getSigningKey())
                        .build().parseClaimsJws(token)
                        .getBody();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    // 토큰 주체에 담긴 이메일 추출
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // 토큰에 담긴 사용자 PK 추출
    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    // 토큰에 담긴 권한 추출
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // 토큰 만료 시각 추출
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // 토큰 만료 여부 확인, 파싱 실패 시에도 만료로 간주
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (CustomException e) {
            return true;
        }
    }

    // 서명과 만료를 함께 검사, 예외 대신 boolean 반환
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
