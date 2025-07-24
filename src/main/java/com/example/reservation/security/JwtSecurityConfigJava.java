package com.example.reservation.security;

import com.example.reservation.security.jwt.JwtAuthenticationEntryPointJava;
import com.example.reservation.security.jwt.JwtAuthenticationFilterJava;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * WebFlux JWT 보안 설정 (Java)
 * 
 * 특징:
 * 1. 명시적 타입 선언 (vs Kotlin 타입 추론)
 * 2. 체이닝 메서드 패턴 (vs Kotlin DSL)
 * 3. 람다 표현식의 verbose한 문법
 * 4. Builder 패턴 활용
 * 
 * Java vs Kotlin 보안 설정 비교:
 * - 설정 방식: 체이닝 vs DSL
 * - 타입 안정성: 명시적 타입 vs 타입 추론
 * - Null 처리: Optional vs ? 연산자
 * - 컬렉션 생성: Arrays.asList vs listOf
 */
@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class JwtSecurityConfigJava {

    private final JwtAuthenticationEntryPointJava jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilterJava jwtAuthenticationFilter;
    private final ReactiveUserDetailsService reactiveUserDetailsService;

    public JwtSecurityConfigJava(
            JwtAuthenticationEntryPointJava jwtAuthenticationEntryPoint,
            JwtAuthenticationFilterJava jwtAuthenticationFilter,
            ReactiveUserDetailsService reactiveUserDetailsService) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.reactiveUserDetailsService = reactiveUserDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .logout(logout -> logout.disable())
                
                // CORS 설정 - Java의 더 verbose한 설정
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // 보안 헤더 설정
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                                .maxAgeInSeconds(31536000L)
                                .includeSubdomains(true)
                                .preload(true)
                        )
                )
                
                // 예외 처리
                .exceptionHandling(exceptions -> 
                        exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint)
                )
                
                // 경로별 접근 권한 설정 - Java의 배열 문법
                .authorizeExchange(exchanges -> exchanges
                        // 공개 API
                        .pathMatchers(
                                "/api/auth/**",
                                "/api/public/**", 
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        
                        // 관리자 전용 API
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        
                        // 매니저 이상 권한
                        .pathMatchers("/api/management/**").hasAnyRole("MANAGER", "ADMIN")
                        
                        // 인증된 사용자 API
                        .pathMatchers("/api/reservations/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .pathMatchers("/api/profile/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        
                        // 그 외 모든 요청은 인증 필요
                        .anyExchange().authenticated()
                )
                
                // JWT 인증 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                
                .build();
    }

    /**
     * CORS 설정 - Java의 명시적 Bean 생성 방식
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration corsConfiguration = new CorsConfiguration();
            corsConfiguration.setAllowedOriginPatterns(List.of("*"));
            corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            corsConfiguration.setAllowedHeaders(List.of("*"));
            corsConfiguration.setAllowCredentials(true);
            corsConfiguration.setMaxAge(3600L);
            return corsConfiguration;
        };
    }

    /**
     * 인증 관리자 설정
     * Java의 명시적 예외 처리
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 사용자 세부 정보 서비스
     * Java의 람다 표현식 vs Kotlin의 간결한 문법
     */
    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService() {
        return username -> {
            // 실제 구현에서는 UserRepository에서 조회
            return Mono.empty();
        };
    }
}