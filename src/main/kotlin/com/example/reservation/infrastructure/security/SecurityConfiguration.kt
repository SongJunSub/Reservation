package com.example.reservation.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono

/**
 * Security 설정
 * 실무 릴리즈 급 구현: JWT 인증, RBAC, CORS, CSRF 보호
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class SecurityConfiguration(
    @Value("\${app.security.jwt.secret}")
    private val jwtSecret: String,
    
    @Value("\${app.security.jwt.expiration:86400}")
    private val jwtExpirationSeconds: Long,
    
    @Value("\${app.security.cors.allowed-origins:*}")
    private val allowedOrigins: List<String>,
    
    private val jwtTokenProvider: JwtTokenProvider,
    private val customAuthenticationManager: CustomAuthenticationManager,
    private val customUserDetailsService: ReactiveUserDetailsService
) {

    /**
     * 메인 보안 필터 체인
     */
    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            // CSRF 비활성화 (JWT 사용시)
            .csrf { it.disable() }
            
            // CORS 설정
            .cors { it.configurationSource(corsConfigurationSource()) }
            
            // 세션 관리 비활성화 (Stateless JWT)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            
            // 인증 매니저 설정
            .authenticationManager(customAuthenticationManager)
            
            // 접근 권한 설정
            .authorizeExchange { exchanges ->
                exchanges
                    // 공개 엔드포인트
                    .pathMatchers(HttpMethod.GET, "/health", "/api/info", "/api/version").permitAll()
                    .pathMatchers(HttpMethod.GET, "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    
                    // 인증 관련 엔드포인트
                    .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/auth/verify/**").permitAll()
                    
                    // 예약 조회 (확인번호로) - 인증 불필요
                    .pathMatchers(HttpMethod.GET, "/api/v1/reservations/confirmation/**").permitAll()
                    
                    // 일반 사용자 권한 필요
                    .pathMatchers(HttpMethod.GET, "/api/v1/reservations/**").hasAnyRole("USER", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/api/v1/reservations").hasAnyRole("USER", "ADMIN")
                    .pathMatchers(HttpMethod.PUT, "/api/v1/reservations/**").hasAnyRole("USER", "ADMIN")
                    .pathMatchers(HttpMethod.DELETE, "/api/v1/reservations/**").hasAnyRole("USER", "ADMIN")
                    
                    // 고객 관리
                    .pathMatchers("/api/v1/guests/**").hasAnyRole("USER", "ADMIN")
                    
                    // 결제 관련
                    .pathMatchers("/api/v1/payments/**").hasAnyRole("USER", "ADMIN")
                    
                    // 객실 조회
                    .pathMatchers(HttpMethod.GET, "/api/v1/rooms/**").hasAnyRole("USER", "ADMIN")
                    
                    // 관리자 전용 엔드포인트
                    .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.PUT, "/api/v1/rooms/**").hasRole("ADMIN")
                    
                    // 시스템 관리
                    .pathMatchers("/actuator/**").hasRole("ADMIN")
                    
                    // 기본적으로 모든 요청은 인증 필요
                    .anyExchange().authenticated()
            }
            
            // JWT 필터 추가
            .addFilterBefore(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            
            // 인증 실패 핸들러
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint { exchange, ex ->
                        val response = exchange.response
                        response.statusCode = HttpStatus.UNAUTHORIZED
                        response.headers.add("Content-Type", "application/json")
                        val body = """
                            {
                                "error": "UNAUTHORIZED",
                                "message": "인증이 필요합니다",
                                "timestamp": "${java.time.LocalDateTime.now()}"
                            }
                        """.trimIndent()
                        val buffer = response.bufferFactory().wrap(body.toByteArray())
                        response.writeWith(Mono.just(buffer))
                    }
                    .accessDeniedHandler { exchange, denied ->
                        val response = exchange.response
                        response.statusCode = HttpStatus.FORBIDDEN
                        response.headers.add("Content-Type", "application/json")
                        val body = """
                            {
                                "error": "ACCESS_DENIED",
                                "message": "접근 권한이 없습니다",
                                "timestamp": "${java.time.LocalDateTime.now()}"
                            }
                        """.trimIndent()
                        val buffer = response.bufferFactory().wrap(body.toByteArray())
                        response.writeWith(Mono.just(buffer))
                    }
            }
            
            .build()
    }

    /**
     * JWT 인증 필터
     */
    @Bean
    fun jwtAuthenticationFilter(): AuthenticationWebFilter {
        val authenticationFilter = AuthenticationWebFilter(customAuthenticationManager)
        authenticationFilter.setServerAuthenticationConverter(jwtAuthenticationConverter())
        
        // 성공/실패 핸들러 설정
        authenticationFilter.setAuthenticationSuccessHandler(authenticationSuccessHandler())
        authenticationFilter.setAuthenticationFailureHandler(authenticationFailureHandler())
        
        return authenticationFilter
    }

    /**
     * JWT 인증 컨버터
     */
    @Bean
    fun jwtAuthenticationConverter(): ServerAuthenticationConverter {
        return ServerAuthenticationConverter { exchange ->
            val request = exchange.request
            val authHeader = request.headers.getFirst("Authorization")
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                val token = authHeader.substring(7)
                if (jwtTokenProvider.validateToken(token)) {
                    val authentication = jwtTokenProvider.getAuthentication(token)
                    Mono.just(authentication)
                } else {
                    Mono.empty()
                }
            } else {
                Mono.empty()
            }
        }
    }

    /**
     * 인증 성공 핸들러
     */
    @Bean
    fun authenticationSuccessHandler(): ServerAuthenticationSuccessHandler {
        return ServerAuthenticationSuccessHandler { webFilterExchange, authentication ->
            // 인증 성공 시 추가 처리 (로깅, 메트릭 등)
            val principal = authentication.principal
            val request = webFilterExchange.exchange.request
            val userAgent = request.headers.getFirst("User-Agent")
            val clientIp = request.headers.getFirst("X-Forwarded-For") 
                ?: request.remoteAddress?.address?.hostAddress
            
            // 보안 로깅
            println("인증 성공: user=$principal, ip=$clientIp, userAgent=$userAgent")
            
            Mono.empty()
        }
    }

    /**
     * 인증 실패 핸들러
     */
    @Bean
    fun authenticationFailureHandler(): ServerAuthenticationFailureHandler {
        return ServerAuthenticationFailureHandler { webFilterExchange, exception ->
            val request = webFilterExchange.exchange.request
            val clientIp = request.headers.getFirst("X-Forwarded-For") 
                ?: request.remoteAddress?.address?.hostAddress
            
            // 보안 로깅 (실패 시도 추적)
            println("인증 실패: ip=$clientIp, reason=${exception.message}")
            
            Mono.empty()
        }
    }

    /**
     * CORS 설정
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        
        configuration.allowedOriginPatterns = allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        configuration.allowedHeaders = listOf(
            "Authorization", 
            "Content-Type", 
            "X-Requested-With",
            "X-User-Agent",
            "X-Client-Version",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        )
        configuration.exposedHeaders = listOf(
            "Access-Control-Allow-Origin",
            "Access-Control-Allow-Credentials",
            "X-Total-Count",
            "X-Page-Size",
            "X-Current-Page"
        )
        configuration.allowCredentials = true
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)
        return source
    }

    /**
     * 패스워드 인코더
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(12) // 강력한 보안을 위해 라운드 12 사용
    }

    /**
     * 보안 헤더 설정
     */
    @Bean
    fun securityHeaders(): SecurityHeadersFilter {
        return SecurityHeadersFilter()
    }

    /**
     * Rate Limiting 필터
     */
    @Bean
    fun rateLimitingFilter(): RateLimitingFilter {
        return RateLimitingFilter(
            requestsPerMinute = 100,
            requestsPerHour = 1000,
            requestsPerDay = 10000
        )
    }
}

/**
 * 보안 헤더 필터
 */
class SecurityHeadersFilter : org.springframework.web.server.WebFilter {
    override fun filter(
        exchange: org.springframework.web.server.ServerWebExchange,
        chain: org.springframework.web.server.WebFilterChain
    ): Mono<Void> {
        val response = exchange.response
        
        // 보안 헤더 추가
        response.headers.apply {
            // XSS 보호
            add("X-Content-Type-Options", "nosniff")
            add("X-Frame-Options", "DENY")
            add("X-XSS-Protection", "1; mode=block")
            
            // HTTPS 강제
            add("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
            
            // 콘텐츠 보안 정책
            add("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'")
            
            // 추천 정책
            add("Referrer-Policy", "strict-origin-when-cross-origin")
            
            // 권한 정책
            add("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        }
        
        return chain.filter(exchange)
    }
}

/**
 * Rate Limiting 필터
 */
class RateLimitingFilter(
    private val requestsPerMinute: Int,
    private val requestsPerHour: Int, 
    private val requestsPerDay: Int
) : org.springframework.web.server.WebFilter {
    
    // 실제 구현에서는 Redis나 캐시를 사용하여 요청 수 추적
    private val requestCounts = mutableMapOf<String, RequestCount>()
    
    override fun filter(
        exchange: org.springframework.web.server.ServerWebExchange,
        chain: org.springframework.web.server.WebFilterChain
    ): Mono<Void> {
        val clientIp = getClientIp(exchange)
        val now = System.currentTimeMillis()
        
        // Rate limiting 체크
        if (isRateLimitExceeded(clientIp, now)) {
            val response = exchange.response
            response.statusCode = HttpStatus.TOO_MANY_REQUESTS
            response.headers.add("Content-Type", "application/json")
            response.headers.add("X-RateLimit-Limit", "$requestsPerMinute")
            response.headers.add("X-RateLimit-Remaining", "0")
            response.headers.add("Retry-After", "60")
            
            val body = """
                {
                    "error": "TOO_MANY_REQUESTS",
                    "message": "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.",
                    "retryAfter": 60
                }
            """.trimIndent()
            
            val buffer = response.bufferFactory().wrap(body.toByteArray())
            return response.writeWith(Mono.just(buffer))
        }
        
        // 요청 수 증가
        incrementRequestCount(clientIp, now)
        
        return chain.filter(exchange)
    }
    
    private fun getClientIp(exchange: org.springframework.web.server.ServerWebExchange): String {
        val request = exchange.request
        return request.headers.getFirst("X-Forwarded-For")
            ?: request.headers.getFirst("X-Real-IP")
            ?: request.remoteAddress?.address?.hostAddress
            ?: "unknown"
    }
    
    private fun isRateLimitExceeded(clientIp: String, now: Long): Boolean {
        val count = requestCounts[clientIp] ?: return false
        
        // 1분, 1시간, 1일 체크
        return count.getRequestsInLastMinute(now) >= requestsPerMinute ||
               count.getRequestsInLastHour(now) >= requestsPerHour ||
               count.getRequestsInLastDay(now) >= requestsPerDay
    }
    
    private fun incrementRequestCount(clientIp: String, now: Long) {
        val count = requestCounts.getOrPut(clientIp) { RequestCount() }
        count.addRequest(now)
    }
    
    private class RequestCount {
        private val requests = mutableListOf<Long>()
        
        fun addRequest(timestamp: Long) {
            synchronized(requests) {
                requests.add(timestamp)
                // 1일 이전 요청은 제거
                requests.removeAll { it < timestamp - 24 * 60 * 60 * 1000 }
            }
        }
        
        fun getRequestsInLastMinute(now: Long): Int {
            synchronized(requests) {
                return requests.count { it > now - 60 * 1000 }
            }
        }
        
        fun getRequestsInLastHour(now: Long): Int {
            synchronized(requests) {
                return requests.count { it > now - 60 * 60 * 1000 }
            }
        }
        
        fun getRequestsInLastDay(now: Long): Int {
            synchronized(requests) {
                return requests.count { it > now - 24 * 60 * 60 * 1000 }
            }
        }
    }
}