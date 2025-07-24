package com.example.reservation.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT 토큰 제공자 (Java)
 * 
 * 기능:
 * 1. JWT 토큰 생성 및 검증
 * 2. 리프레시 토큰 관리
 * 3. 토큰에서 사용자 정보 추출
 * 4. 토큰 만료 시간 관리
 * 
 * Java 특징:
 * - 명시적 타입 선언
 * - Stream API 사용
 * - try-catch 예외 처리
 * - Builder 패턴 활용
 * - final 키워드로 불변성 보장
 */
@Component
public class JwtTokenProviderJava {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProviderJava.class);
    private static final String AUTHORITIES_KEY = "auth";
    private static final String USER_ID_KEY = "userId";
    private static final String TOKEN_TYPE_KEY = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final String jwtSecret;
    private final long jwtExpirationInSeconds;
    private final long refreshExpirationInSeconds;
    private final Key key;

    public JwtTokenProviderJava(
            @Value("${app.jwt.secret:reservation-secret-key-for-jwt-token-generation-and-validation-2024}")
            String jwtSecret,
            @Value("${app.jwt.expiration:86400}") // 24시간 (초)
            long jwtExpirationInSeconds,
            @Value("${app.jwt.refresh-expiration:604800}") // 7일 (초)  
            long refreshExpirationInSeconds) {
        
        this.jwtSecret = jwtSecret;
        this.jwtExpirationInSeconds = jwtExpirationInSeconds;
        this.refreshExpirationInSeconds = refreshExpirationInSeconds;
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 액세스 토큰 생성
     * Java의 Stream API와 명시적 타입 변환
     */
    public String generateAccessToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        Instant now = Instant.now();
        Instant validity = now.plus(jwtExpirationInSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim(AUTHORITIES_KEY, authorities)
                .claim(USER_ID_KEY, getUserIdFromAuthentication(authentication))
                .claim(TOKEN_TYPE_KEY, ACCESS_TOKEN_TYPE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(validity))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 리프레시 토큰 생성
     * Java의 Builder 패턴 활용
     */
    public String generateRefreshToken(Authentication authentication) {
        Instant now = Instant.now();
        Instant validity = now.plus(refreshExpirationInSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim(USER_ID_KEY, getUserIdFromAuthentication(authentication))
                .claim(TOKEN_TYPE_KEY, REFRESH_TOKEN_TYPE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(validity))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 토큰 쌍 생성 (액세스 + 리프레시)
     * Java의 생성자 체이닝
     */
    public TokenPairJava generateTokenPair(Authentication authentication) {
        return new TokenPairJava(
                generateAccessToken(authentication),
                generateRefreshToken(authentication),
                "Bearer",
                jwtExpirationInSeconds,
                refreshExpirationInSeconds
        );
    }

    /**
     * 토큰에서 사용자명 추출
     * Java의 null 체크와 try-catch 예외 처리
     */
    public String getUsernameFromToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            return claims != null ? claims.getSubject() : null;
        } catch (Exception ex) {
            logger.warn("사용자명 추출 실패: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     * Java의 명시적 타입 캐스팅
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            if (claims != null) {
                Object userIdClaim = claims.get(USER_ID_KEY);
                if (userIdClaim instanceof Number) {
                    return ((Number) userIdClaim).longValue();
                }
            }
            return null;
        } catch (Exception ex) {
            logger.warn("사용자 ID 추출 실패: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 권한 정보 추출
     * Java Stream API와 Optional 사용
     */
    public java.util.List<String> getAuthoritiesFromToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            if (claims != null) {
                String authorities = claims.get(AUTHORITIES_KEY, String.class);
                if (authorities != null && !authorities.trim().isEmpty()) {
                    return java.util.Arrays.stream(authorities.split(","))
                            .map(String::trim)
                            .filter(auth -> !auth.isEmpty())
                            .collect(Collectors.toList());
                }
            }
            return java.util.Collections.emptyList();
        } catch (Exception ex) {
            logger.warn("권한 정보 추출 실패: {}", ex.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 토큰 유효성 검증
     * Java의 if-else 조건문과 명시적 예외 처리
     */
    public TokenValidationResultJava validateToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            if (claims == null) {
                return TokenValidationResultJava.INVALID;
            }
            
            if (claims.getExpiration().before(new Date())) {
                return TokenValidationResultJava.EXPIRED;
            }
            
            String tokenType = claims.get(TOKEN_TYPE_KEY, String.class);
            if (ACCESS_TOKEN_TYPE.equals(tokenType)) {
                return TokenValidationResultJava.VALID_ACCESS;
            } else if (REFRESH_TOKEN_TYPE.equals(tokenType)) {
                return TokenValidationResultJava.VALID_REFRESH;
            } else {
                return TokenValidationResultJava.INVALID;
            }
            
        } catch (SecurityException ex) {
            logger.warn("JWT 보안 위반: {}", ex.getMessage());
            return TokenValidationResultJava.SECURITY_ERROR;
        } catch (MalformedJwtException ex) {
            logger.warn("잘못된 JWT 토큰: {}", ex.getMessage());
            return TokenValidationResultJava.MALFORMED;
        } catch (ExpiredJwtException ex) {
            logger.debug("만료된 JWT 토큰: {}", ex.getMessage());
            return TokenValidationResultJava.EXPIRED;
        } catch (UnsupportedJwtException ex) {
            logger.warn("지원되지 않는 JWT 토큰: {}", ex.getMessage());
            return TokenValidationResultJava.UNSUPPORTED;
        } catch (IllegalArgumentException ex) {
            logger.warn("잘못된 JWT 클레임: {}", ex.getMessage());
            return TokenValidationResultJava.INVALID;
        } catch (Exception ex) {
            logger.error("JWT 토큰 검증 중 오류: {}", ex.getMessage());
            return TokenValidationResultJava.ERROR;
        }
    }

    /**
     * 토큰에서 만료 시간 추출
     */
    public Date getExpirationFromToken(String token) {
        try {
            Claims claims = parseClaimsFromToken(token);
            return claims != null ? claims.getExpiration() : null;
        } catch (Exception ex) {
            logger.warn("만료 시간 추출 실패: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 토큰이 곧 만료되는지 확인 (30분 이내)
     */
    public boolean isTokenExpiringSoon(String token) {
        Date expiration = getExpirationFromToken(token);
        if (expiration == null) {
            return true;
        }
        
        Date now = new Date();
        Date thirtyMinutesFromNow = new Date(now.getTime() + 30 * 60 * 1000); // 30분
        return expiration.before(thirtyMinutesFromNow);
    }

    /**
     * 토큰에서 Claims 파싱
     * private 메서드로 공통 로직 추출
     */
    private Claims parseClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception ex) {
            logger.debug("토큰 파싱 실패: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Authentication에서 사용자 ID 추출
     * 실제 구현에서는 UserPrincipal 또는 CustomUserDetails 사용
     */
    private Long getUserIdFromAuthentication(Authentication authentication) {
        // 실제 구현에서는 authentication.getPrincipal()에서 사용자 ID 추출
        return 1L; // 임시 값
    }
}

/**
 * 토큰 쌍 클래스 (Java)
 * Java의 전통적인 클래스 정의 방식
 */
class TokenPairJava {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final long refreshExpiresIn;

    public TokenPairJava(String accessToken, String refreshToken, String tokenType, 
                        long expiresIn, long refreshExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }

    // Getter 메서드들
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getTokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public long getRefreshExpiresIn() { return refreshExpiresIn; }

    @Override
    public String toString() {
        return "TokenPairJava{" +
                "accessToken='" + (accessToken != null ? accessToken.substring(0, Math.min(10, accessToken.length())) + "..." : "null") + '\'' +
                ", refreshToken='" + (refreshToken != null ? refreshToken.substring(0, Math.min(10, refreshToken.length())) + "..." : "null") + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", refreshExpiresIn=" + refreshExpiresIn +
                '}';
    }
}

/**
 * 토큰 검증 결과 열거형 (Java)
 * Java enum의 메서드 정의 방식
 */
enum TokenValidationResultJava {
    VALID_ACCESS,
    VALID_REFRESH, 
    EXPIRED,
    INVALID,
    MALFORMED,
    UNSUPPORTED,
    SECURITY_ERROR,
    ERROR;

    public boolean isValid() {
        return this == VALID_ACCESS || this == VALID_REFRESH;
    }

    public boolean isAccessToken() {
        return this == VALID_ACCESS;
    }

    public boolean isRefreshToken() {
        return this == VALID_REFRESH;
    }
}