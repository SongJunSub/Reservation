package com.example.reservation.security.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT 인증 필터 (Java WebFlux)
 * 
 * 기능:
 * 1. HTTP 요청에서 JWT 토큰 추출
 * 2. 토큰 유효성 검증
 * 3. Spring Security Context에 인증 정보 설정
 * 4. 리액티브 스트림을 통한 비동기 처리
 * 
 * Java vs Kotlin 비교:
 * - 명시적 타입 선언 vs 타입 추론
 * - 전통적인 조건문 vs when 표현식
 * - Stream API vs 컬렉션 함수
 * - try-catch vs 안전한 호출 연산자
 */
@Component
public class JwtAuthenticationFilterJava implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilterJava.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProviderJava jwtTokenProvider;

    public JwtAuthenticationFilterJava(JwtTokenProviderJava jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * WebFlux 필터 체인 처리
     * Java의 명시적 타입과 전통적인 조건문 사용
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return extractTokenFromRequest(exchange)
                .flatMap(token -> {
                    TokenValidationResultJava validationResult = jwtTokenProvider.validateToken(token);
                    
                    if (validationResult == TokenValidationResultJava.VALID_ACCESS) {
                        // 유효한 액세스 토큰인 경우 인증 정보 생성
                        return createAuthentication(token)
                                .flatMap(authentication -> 
                                        // Security Context에 인증 정보 설정하고 다음 필터로 진행
                                        chain.filter(exchange)
                                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                                );
                    } else if (validationResult == TokenValidationResultJava.EXPIRED) {
                        logger.debug("만료된 토큰으로 요청: {}", exchange.getRequest().getPath());
                        return chain.filter(exchange); // 인증 없이 진행 (401 응답 예상)
                    } else {
                        logger.debug("유효하지 않은 토큰: {}", validationResult);
                        return chain.filter(exchange); // 인증 없이 진행
                    }
                })
                .switchIfEmpty(
                        // 토큰이 없는 경우 그대로 진행
                        chain.filter(exchange)
                )
                .doOnError(error -> 
                        logger.error("JWT 인증 필터에서 오류 발생", error)
                );
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출
     * Java의 명시적 null 체크와 조건문
     */
    private Mono<String> extractTokenFromRequest(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            String bearerToken = exchange.getRequest().getHeaders().getFirst(AUTHORIZATION_HEADER);
            
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
                return bearerToken.substring(BEARER_PREFIX.length());
            }
            return null;
        })
        .filter(token -> StringUtils.hasText(token))
        .doOnNext(token -> 
                logger.debug("요청에서 JWT 토큰 추출됨: {}...", 
                        token.length() > 10 ? token.substring(0, 10) : token)
        );
    }

    /**
     * JWT 토큰으로부터 Spring Security Authentication 객체 생성
     * Java의 명시적 예외 처리와 객체 생성
     */
    private Mono<UsernamePasswordAuthenticationToken> createAuthentication(String token) {
        return Mono.fromCallable(() -> {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            List<String> authoritiesStrings = jwtTokenProvider.getAuthoritiesFromToken(token);
            
            List<SimpleGrantedAuthority> authorities = authoritiesStrings.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // UserPrincipal 대신 간단한 구조 사용
            UserPrincipalJava principal = new UserPrincipalJava(
                    userId != null ? userId : 0L,
                    username != null ? username : "unknown",
                    authorities
            );

            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            
            // Details 설정
            Map<String, Object> details = new HashMap<>();
            details.put("tokenType", "Bearer");
            details.put("source", "JWT");
            authentication.setDetails(details);
            
            return authentication;
        })
        .doOnNext(authentication -> 
                logger.debug("인증 객체 생성 완료: {}", authentication.getName())
        )
        .onErrorResume(error -> {
            logger.error("인증 객체 생성 중 오류", error);
            return Mono.empty();
        });
    }
}

/**
 * 사용자 주체 정보 클래스 (Java)
 * 
 * Java 특징:
 * - 전통적인 클래스 정의 방식
 * - 명시적 getter/setter 메서드
 * - final 필드를 통한 불변성
 * - 생성자 오버로딩
 */
class UserPrincipalJava {
    private final Long id;
    private final String username;
    private final List<SimpleGrantedAuthority> authorities;
    private final String email;
    private final String fullName;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean accountNonLocked; 
    private final boolean credentialsNonExpired;

    // 기본 생성자
    public UserPrincipalJava(Long id, String username, List<SimpleGrantedAuthority> authorities) {
        this(id, username, authorities, null, null, true, true, true, true);
    }

    // 전체 매개변수 생성자
    public UserPrincipalJava(Long id, String username, List<SimpleGrantedAuthority> authorities,
                            String email, String fullName, boolean enabled, 
                            boolean accountNonExpired, boolean accountNonLocked, 
                            boolean credentialsNonExpired) {
        this.id = id;
        this.username = username;
        this.authorities = authorities != null ? authorities : java.util.Collections.emptyList();
        this.email = email;
        this.fullName = fullName;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
    }

    /**
     * 권한 확인 편의 메서드
     * Java Stream API 활용
     */
    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(auth -> 
                        auth.getAuthority().equals("ROLE_" + role) || 
                        auth.getAuthority().equals(role)
                );
    }

    public boolean hasAnyRole(String... roles) {
        return java.util.Arrays.stream(roles)
                .anyMatch(this::hasRole);
    }

    public boolean hasAuthority(String authority) {
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals(authority));
    }

    /**
     * 관리자 여부 확인
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * 매니저 여부 확인
     */
    public boolean isManager() {
        return hasRole("MANAGER") || isAdmin();
    }

    /**
     * 일반 사용자 여부 확인
     */
    public boolean isUser() {
        return hasRole("USER") || isManager();
    }

    // Getter 메서드들
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public List<SimpleGrantedAuthority> getAuthorities() { return authorities; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public boolean isEnabled() { return enabled; }
    public boolean isAccountNonExpired() { return accountNonExpired; }
    public boolean isAccountNonLocked() { return accountNonLocked; }
    public boolean isCredentialsNonExpired() { return credentialsNonExpired; }

    @Override
    public String toString() {
        return "UserPrincipalJava{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", authorities=" + authorities +
                ", enabled=" + enabled +
                '}';
    }
}