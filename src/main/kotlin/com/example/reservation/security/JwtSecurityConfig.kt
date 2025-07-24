package com.example.reservation.security

import com.example.reservation.security.jwt.JwtAuthenticationEntryPoint
import com.example.reservation.security.jwt.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import reactor.core.publisher.Mono

/**
 * WebFlux JWT 보안 설정 (Kotlin)
 * 
 * 특징:
 * 1. 리액티브 보안 구성
 * 2. JWT 기반 Stateless 인증
 * 3. RBAC (Role-Based Access Control)
 * 4. CORS 설정 및 보안 헤더
 * 
 * Kotlin vs Java 보안 설정 비교:
 * - DSL 스타일 설정: Kotlin의 함수형 DSL vs Java의 체이닝
 * - 타입 추론: 명시적 타입 vs 타입 추론
 * - Null 안정성: ReactiveUserDetailsService null 처리
 */
@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true)
class JwtSecurityConfig(
    private val jwtAuthenticationEntryPoint: JwtAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val reactiveUserDetailsService: ReactiveUserDetailsService
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            
            // CORS 설정
            .cors { cors ->
                cors.configurationSource { request ->
                    val corsConfiguration = org.springframework.web.cors.CorsConfiguration()
                    corsConfiguration.allowedOriginPatterns = listOf("*")
                    corsConfiguration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    corsConfiguration.allowedHeaders = listOf("*")
                    corsConfiguration.allowCredentials = true
                    corsConfiguration.maxAge = 3600L
                    corsConfiguration
                }
            }
            
            // 보안 헤더 설정
            .headers { headers ->
                headers
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .httpStrictTransportSecurity { hstsConfig ->
                        hstsConfig
                            .maxAgeInSeconds(31536000)
                            .includeSubdomains(true)
                            .preload(true)
                    }
            }
            
            // 예외 처리
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            }
            
            // 경로별 접근 권한 설정
            .authorizeExchange { exchanges ->
                exchanges
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
            }
            
            // JWT 인증 필터 추가
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            
            .build()
    }

    /**
     * 인증 관리자 설정
     * 리액티브 환경에서는 ReactiveAuthenticationManager 사용 권장
     */
    @Bean
    fun authenticationManager(
        authConfig: AuthenticationConfiguration
    ): AuthenticationManager = authConfig.authenticationManager

    /**
     * 사용자 세부 정보 서비스
     * 실제 구현에서는 데이터베이스에서 사용자 정보를 조회
     */
    @Bean
    fun reactiveUserDetailsService(): ReactiveUserDetailsService {
        return ReactiveUserDetailsService { username ->
            // 실제 구현에서는 UserRepository에서 조회
            Mono.empty()
        }
    }
}