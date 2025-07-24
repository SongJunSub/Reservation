package com.example.reservation.security.jwt

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * JWT 인증 필터 (Kotlin WebFlux)
 * 
 * 기능:
 * 1. HTTP 요청에서 JWT 토큰 추출
 * 2. 토큰 유효성 검증
 * 3. Spring Security Context에 인증 정보 설정
 * 4. 리액티브 스트림을 통한 비동기 처리
 * 
 * Kotlin vs Java 비교:
 * - 리액티브 체이닝: flatMap, switchIfEmpty 등의 연산자 사용
 * - Null 안정성: ?. 연산자와 let 함수 활용
 * - 간결한 람다 표현식
 * - when 표현식을 통한 조건 처리
 */
@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : WebFilter {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }

    /**
     * WebFlux 필터 체인 처리
     * Kotlin의 리액티브 스트림 연산자 활용
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        return extractTokenFromRequest(exchange)
            .flatMap { token ->
                when (val validationResult = jwtTokenProvider.validateToken(token)) {
                    TokenValidationResult.VALID_ACCESS -> {
                        // 유효한 액세스 토큰인 경우 인증 정보 생성
                        createAuthentication(token)
                            .flatMap { authentication ->
                                // Security Context에 인증 정보 설정하고 다음 필터로 진행
                                chain.filter(exchange)
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                            }
                    }
                    TokenValidationResult.EXPIRED -> {
                        logger.debug("만료된 토큰으로 요청: ${exchange.request.path}")
                        chain.filter(exchange) // 인증 없이 진행 (401 응답 예상)
                    }
                    else -> {
                        logger.debug("유효하지 않은 토큰: $validationResult")
                        chain.filter(exchange) // 인증 없이 진행
                    }
                }
            }
            .switchIfEmpty(
                // 토큰이 없는 경우 그대로 진행
                chain.filter(exchange)
            )
            .doOnError { error ->
                logger.error("JWT 인증 필터에서 오류 발생", error)
            }
    }

    /**
     * HTTP 요청에서 JWT 토큰 추출
     * Kotlin의 let 함수와 takeIf 함수 활용
     */
    private fun extractTokenFromRequest(exchange: ServerWebExchange): Mono<String> {
        return Mono.fromCallable {
            exchange.request.headers.getFirst(AUTHORIZATION_HEADER)
                ?.takeIf { StringUtils.hasText(it) && it.startsWith(BEARER_PREFIX) }
                ?.substring(BEARER_PREFIX.length)
        }
        .filter { token -> StringUtils.hasText(token) }
        .doOnNext { token -> 
            logger.debug("요청에서 JWT 토큰 추출됨: ${token.take(10)}...")
        }
    }

    /**
     * JWT 토큰으로부터 Spring Security Authentication 객체 생성
     * Kotlin의 함수형 프로그래밍 스타일
     */
    private fun createAuthentication(token: String): Mono<UsernamePasswordAuthenticationToken> {
        return Mono.fromCallable {
            val username = jwtTokenProvider.getUsernameFromToken(token)
            val userId = jwtTokenProvider.getUserIdFromToken(token)
            val authorities = jwtTokenProvider.getAuthoritiesFromToken(token)
                .map { SimpleGrantedAuthority(it) }

            // UserPrincipal 대신 간단한 구조 사용
            val principal = UserPrincipal(
                id = userId ?: 0L,
                username = username ?: "unknown",
                authorities = authorities
            )

            UsernamePasswordAuthenticationToken(
                principal,
                null, // credentials는 필요하지 않음 (이미 토큰으로 검증됨)
                authorities
            ).apply {
                details = mapOf(
                    "tokenType" to "Bearer",
                    "source" to "JWT"
                )
            }
        }
        .doOnNext { authentication ->
            logger.debug("인증 객체 생성 완료: ${authentication.name}")
        }
        .onErrorResume { error ->
            logger.error("인증 객체 생성 중 오류", error)
            Mono.empty()
        }
    }
}

/**
 * 사용자 주체 정보 클래스 (Kotlin data class)
 * 
 * Kotlin 특징:
 * - data class: 자동으로 equals, hashCode, toString 생성
 * - 간결한 생성자 정의
 * - 불변 객체 (val 사용)
 */
data class UserPrincipal(
    val id: Long,
    val username: String,
    val authorities: List<SimpleGrantedAuthority> = emptyList(),
    val email: String? = null,
    val fullName: String? = null,
    val enabled: Boolean = true,
    val accountNonExpired: Boolean = true,
    val accountNonLocked: Boolean = true,
    val credentialsNonExpired: Boolean = true
) {
    /**
     * 권한 확인 편의 메서드
     * Kotlin 확장 함수 스타일
     */
    fun hasRole(role: String): Boolean = authorities.any { 
        it.authority == "ROLE_$role" || it.authority == role 
    }

    fun hasAnyRole(vararg roles: String): Boolean = roles.any { hasRole(it) }

    fun hasAuthority(authority: String): Boolean = authorities.any { 
        it.authority == authority 
    }

    /**
     * 관리자 여부 확인
     */
    val isAdmin: Boolean
        get() = hasRole("ADMIN")

    /**
     * 매니저 여부 확인
     */
    val isManager: Boolean
        get() = hasRole("MANAGER") || isAdmin

    /**
     * 일반 사용자 여부 확인
     */
    val isUser: Boolean
        get() = hasRole("USER") || isManager
}