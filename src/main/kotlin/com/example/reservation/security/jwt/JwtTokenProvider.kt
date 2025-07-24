package com.example.reservation.security.jwt

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import java.security.Key
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * JWT 토큰 제공자 (Kotlin)
 * 
 * 기능:
 * 1. JWT 토큰 생성 및 검증
 * 2. 리프레시 토큰 관리
 * 3. 토큰에서 사용자 정보 추출
 * 4. 토큰 만료 시간 관리
 * 
 * Kotlin 특징:
 * - String 템플릿: "$variable" 문법
 * - 확장 함수: Date.toInstant() 등
 * - Null 안정성: ?. 연산자 활용
 * - 간결한 함수 정의
 */
@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret:reservation-secret-key-for-jwt-token-generation-and-validation-2024}")
    private val jwtSecret: String,
    
    @Value("\${app.jwt.expiration:86400}") // 24시간 (초)
    private val jwtExpirationInSeconds: Long,
    
    @Value("\${app.jwt.refresh-expiration:604800}") // 7일 (초)
    private val refreshExpirationInSeconds: Long
) {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)
        private const val AUTHORITIES_KEY = "auth"
        private const val USER_ID_KEY = "userId"
        private const val TOKEN_TYPE_KEY = "type"
        private const val ACCESS_TOKEN_TYPE = "access"
        private const val REFRESH_TOKEN_TYPE = "refresh"
    }

    private val key: Key = Keys.hmacShaKeyFor(jwtSecret.toByteArray())

    /**
     * 액세스 토큰 생성
     * Kotlin의 간결한 함수 정의와 String 템플릿 활용
     */
    fun generateAccessToken(authentication: Authentication): String {
        val authorities = authentication.authorities
            .joinToString(",") { it.authority }

        val now = Instant.now()
        val validity = now.plus(jwtExpirationInSeconds, ChronoUnit.SECONDS)

        return Jwts.builder()
            .setSubject(authentication.name)
            .claim(AUTHORITIES_KEY, authorities)
            .claim(USER_ID_KEY, getUserIdFromAuthentication(authentication))
            .claim(TOKEN_TYPE_KEY, ACCESS_TOKEN_TYPE)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(validity))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * 리프레시 토큰 생성
     * 더 긴 만료 시간과 최소한의 정보만 포함
     */
    fun generateRefreshToken(authentication: Authentication): String {
        val now = Instant.now()
        val validity = now.plus(refreshExpirationInSeconds, ChronoUnit.SECONDS)

        return Jwts.builder()
            .setSubject(authentication.name)
            .claim(USER_ID_KEY, getUserIdFromAuthentication(authentication))
            .claim(TOKEN_TYPE_KEY, REFRESH_TOKEN_TYPE)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(validity))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * 토큰 쌍 생성 (액세스 + 리프레시)
     * Kotlin data class 활용으로 간편한 반환
     */
    fun generateTokenPair(authentication: Authentication): TokenPair {
        return TokenPair(
            accessToken = generateAccessToken(authentication),
            refreshToken = generateRefreshToken(authentication),
            tokenType = "Bearer",
            expiresIn = jwtExpirationInSeconds,
            refreshExpiresIn = refreshExpirationInSeconds
        )
    }

    /**
     * 토큰에서 사용자명 추출
     * Kotlin의 안전한 호출 연산자 활용
     */
    fun getUsernameFromToken(token: String): String? {
        return try {
            val claims = parseClaimsFromToken(token)
            claims?.subject
        } catch (ex: Exception) {
            logger.warn("사용자명 추출 실패: ${ex.message}")
            null
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): Long? {
        return try {
            val claims = parseClaimsFromToken(token)
            claims?.get(USER_ID_KEY, java.lang.Long::class.java)?.toLong()
        } catch (ex: Exception) {
            logger.warn("사용자 ID 추출 실패: ${ex.message}")
            null
        }
    }

    /**
     * 토큰에서 권한 정보 추출
     * Kotlin의 컬렉션 함수 활용
     */
    fun getAuthoritiesFromToken(token: String): List<String> {
        return try {
            val claims = parseClaimsFromToken(token)
            val authorities = claims?.get(AUTHORITIES_KEY, String::class.java)
            authorities?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        } catch (ex: Exception) {
            logger.warn("권한 정보 추출 실패: ${ex.message}")
            emptyList()
        }
    }

    /**
     * 토큰 유효성 검증
     * Kotlin의 when 표현식과 간결한 예외 처리
     */
    fun validateToken(token: String): TokenValidationResult {
        return try {
            val claims = parseClaimsFromToken(token)
            when {
                claims == null -> TokenValidationResult.INVALID
                claims.expiration.before(Date()) -> TokenValidationResult.EXPIRED
                else -> {
                    val tokenType = claims.get(TOKEN_TYPE_KEY, String::class.java)
                    when (tokenType) {
                        ACCESS_TOKEN_TYPE -> TokenValidationResult.VALID_ACCESS
                        REFRESH_TOKEN_TYPE -> TokenValidationResult.VALID_REFRESH
                        else -> TokenValidationResult.INVALID
                    }
                }
            }
        } catch (ex: SecurityException) {
            logger.warn("JWT 보안 위반: ${ex.message}")
            TokenValidationResult.SECURITY_ERROR
        } catch (ex: MalformedJwtException) {
            logger.warn("잘못된 JWT 토큰: ${ex.message}")
            TokenValidationResult.MALFORMED
        } catch (ex: ExpiredJwtException) {
            logger.debug("만료된 JWT 토큰: ${ex.message}")
            TokenValidationResult.EXPIRED
        } catch (ex: UnsupportedJwtException) {
            logger.warn("지원되지 않는 JWT 토큰: ${ex.message}")
            TokenValidationResult.UNSUPPORTED
        } catch (ex: IllegalArgumentException) {
            logger.warn("잘못된 JWT 클레임: ${ex.message}")
            TokenValidationResult.INVALID
        } catch (ex: Exception) {
            logger.error("JWT 토큰 검증 중 오류: ${ex.message}")
            TokenValidationResult.ERROR
        }
    }

    /**
     * 토큰에서 만료 시간 추출
     */
    fun getExpirationFromToken(token: String): Date? {
        return try {
            parseClaimsFromToken(token)?.expiration
        } catch (ex: Exception) {
            logger.warn("만료 시간 추출 실패: ${ex.message}")
            null
        }
    }

    /**
     * 토큰이 곧 만료되는지 확인 (30분 이내)
     */
    fun isTokenExpiringSoon(token: String): Boolean {
        val expiration = getExpirationFromToken(token) ?: return true
        val now = Date()
        val thirtyMinutesFromNow = Date(now.time + 30 * 60 * 1000) // 30분
        return expiration.before(thirtyMinutesFromNow)
    }

    /**
     * 토큰에서 Claims 파싱
     * private 함수로 공통 로직 추출
     */
    private fun parseClaimsFromToken(token: String): Claims? {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (ex: Exception) {
            logger.debug("토큰 파싱 실패: ${ex.message}")
            null
        }
    }

    /**
     * Authentication에서 사용자 ID 추출
     * 실제 구현에서는 UserPrincipal 또는 CustomUserDetails 사용
     */
    private fun getUserIdFromAuthentication(authentication: Authentication): Long {
        // 실제 구현에서는 authentication.principal에서 사용자 ID 추출
        return 1L // 임시 값
    }
}

/**
 * 토큰 쌍 데이터 클래스
 * Kotlin data class의 간편함 보여줌
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshExpiresIn: Long
)

/**
 * 토큰 검증 결과 열거형
 * Kotlin enum class 활용
 */
enum class TokenValidationResult {
    VALID_ACCESS,
    VALID_REFRESH,
    EXPIRED,
    INVALID,
    MALFORMED,
    UNSUPPORTED,
    SECURITY_ERROR,
    ERROR;

    val isValid: Boolean
        get() = this == VALID_ACCESS || this == VALID_REFRESH

    val isAccessToken: Boolean
        get() = this == VALID_ACCESS

    val isRefreshToken: Boolean
        get() = this == VALID_REFRESH
}