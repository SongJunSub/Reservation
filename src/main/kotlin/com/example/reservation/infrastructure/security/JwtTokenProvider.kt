package com.example.reservation.infrastructure.security

import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*
import kotlin.collections.HashMap

/**
 * JWT 토큰 제공자
 * 실무 릴리즈 급 구현: 보안 강화, 토큰 갱신, 블랙리스트 관리
 */
@Component
class JwtTokenProvider(
    @Value("\${app.security.jwt.secret}")
    private val jwtSecret: String,
    
    @Value("\${app.security.jwt.expiration:86400}")
    private val jwtExpirationSeconds: Long,
    
    @Value("\${app.security.jwt.refresh-expiration:604800}")
    private val refreshExpirationSeconds: Long,
    
    @Value("\${app.security.jwt.issuer:reservation-system}")
    private val issuer: String,
    
    private val tokenBlacklistService: TokenBlacklistService
) {
    
    companion object {
        private const val AUTHORITIES_KEY = "auth"
        private const val USER_ID_KEY = "userId"
        private const val EMAIL_KEY = "email"
        private const val TOKEN_TYPE_KEY = "tokenType"
        
        // 토큰 타입
        private const val ACCESS_TOKEN = "ACCESS"
        private const val REFRESH_TOKEN = "REFRESH"
    }
    
    private val key: Key by lazy {
        Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret))
    }
    
    /**
     * 액세스 토큰 생성
     */
    fun generateAccessToken(authentication: Authentication): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationSeconds * 1000)
        
        val userDetails = authentication.principal as UserPrincipal
        
        return Jwts.builder()
            .setIssuer(issuer)
            .setSubject(authentication.name)
            .setAudience("reservation-client")
            .claim(AUTHORITIES_KEY, authorities)
            .claim(USER_ID_KEY, userDetails.userId)
            .claim(EMAIL_KEY, userDetails.email)
            .claim(TOKEN_TYPE_KEY, ACCESS_TOKEN)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setNotBefore(now)
            .setId(UUID.randomUUID().toString()) // JTI (JWT ID)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }
    
    /**
     * 리프레시 토큰 생성
     */
    fun generateRefreshToken(authentication: Authentication): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshExpirationSeconds * 1000)
        
        val userDetails = authentication.principal as UserPrincipal
        
        return Jwts.builder()
            .setIssuer(issuer)
            .setSubject(authentication.name)
            .setAudience("reservation-client")
            .claim(USER_ID_KEY, userDetails.userId)
            .claim(EMAIL_KEY, userDetails.email)
            .claim(TOKEN_TYPE_KEY, REFRESH_TOKEN)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setNotBefore(now)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }
    
    /**
     * 토큰으로부터 Authentication 객체 생성
     */
    fun getAuthentication(token: String): Authentication {
        val claims = parseClaims(token)
        
        if (claims[AUTHORITIES_KEY] == null) {
            throw RuntimeException("권한 정보가 없는 토큰입니다")
        }
        
        val authorities = claims[AUTHORITIES_KEY].toString()
            .split(",")
            .filter { it.isNotBlank() }
            .map { SimpleGrantedAuthority(it) }
        
        val userPrincipal = UserPrincipal(
            userId = UUID.fromString(claims[USER_ID_KEY].toString()),
            username = claims.subject,
            email = claims[EMAIL_KEY].toString(),
            authorities = authorities
        )
        
        return UsernamePasswordAuthenticationToken(userPrincipal, "", authorities)
    }
    
    /**
     * 토큰에서 사용자명 추출
     */
    fun getUsernameFromToken(token: String): String {
        return parseClaims(token).subject
    }
    
    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): UUID {
        val claims = parseClaims(token)
        return UUID.fromString(claims[USER_ID_KEY].toString())
    }
    
    /**
     * 토큰 만료 시간 추출
     */
    fun getExpirationDateFromToken(token: String): Date {
        return parseClaims(token).expiration
    }
    
    /**
     * 토큰 검증
     */
    fun validateToken(token: String): Boolean {
        try {
            // 블랙리스트 확인
            if (tokenBlacklistService.isBlacklisted(token)) {
                return false
            }
            
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .requireAudience("reservation-client")
                .build()
                .parseClaimsJws(token)
                .body
                
            // 토큰 타입 검증 (액세스 토큰인지 확인)
            val tokenType = claims[TOKEN_TYPE_KEY]?.toString()
            if (tokenType != ACCESS_TOKEN) {
                return false
            }
            
            // 만료 시간 검증
            if (claims.expiration.before(Date())) {
                return false
            }
            
            // Not Before 검증
            if (claims.notBefore?.after(Date()) == true) {
                return false
            }
            
            return true
            
        } catch (e: SignatureException) {
            println("잘못된 JWT 서명입니다")
        } catch (e: MalformedJwtException) {
            println("잘못된 JWT 토큰입니다")
        } catch (e: ExpiredJwtException) {
            println("만료된 JWT 토큰입니다")
        } catch (e: UnsupportedJwtException) {
            println("지원되지 않는 JWT 토큰입니다")
        } catch (e: IllegalArgumentException) {
            println("JWT 토큰이 잘못되었습니다")
        } catch (e: Exception) {
            println("JWT 토큰 검증 중 오류 발생: ${e.message}")
        }
        
        return false
    }
    
    /**
     * 리프레시 토큰 검증
     */
    fun validateRefreshToken(token: String): Boolean {
        try {
            if (tokenBlacklistService.isBlacklisted(token)) {
                return false
            }
            
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .requireAudience("reservation-client")
                .build()
                .parseClaimsJws(token)
                .body
                
            val tokenType = claims[TOKEN_TYPE_KEY]?.toString()
            return tokenType == REFRESH_TOKEN && claims.expiration.after(Date())
            
        } catch (e: Exception) {
            println("리프레시 토큰 검증 실패: ${e.message}")
            return false
        }
    }
    
    /**
     * 토큰 무효화 (로그아웃시)
     */
    fun invalidateToken(token: String) {
        try {
            val claims = parseClaims(token)
            val jti = claims.id
            val expiration = claims.expiration
            
            tokenBlacklistService.addToBlacklist(jti, expiration)
        } catch (e: Exception) {
            println("토큰 무효화 실패: ${e.message}")
        }
    }
    
    /**
     * 토큰 갱신
     */
    fun refreshToken(refreshToken: String): TokenPair? {
        if (!validateRefreshToken(refreshToken)) {
            return null
        }
        
        return try {
            val claims = parseClaims(refreshToken)
            val userId = UUID.fromString(claims[USER_ID_KEY].toString())
            val username = claims.subject
            val email = claims[EMAIL_KEY].toString()
            
            // 기본 권한으로 새로운 토큰 생성 (실제로는 DB에서 최신 권한 조회)
            val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
            val userPrincipal = UserPrincipal(userId, username, email, authorities)
            val authentication = UsernamePasswordAuthenticationToken(userPrincipal, "", authorities)
            
            TokenPair(
                accessToken = generateAccessToken(authentication),
                refreshToken = generateRefreshToken(authentication),
                expiresIn = jwtExpirationSeconds
            )
            
        } catch (e: Exception) {
            println("토큰 갱신 실패: ${e.message}")
            null
        }
    }
    
    /**
     * Claims 파싱
     */
    private fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
        } catch (e: ExpiredJwtException) {
            // 만료된 토큰의 클레임도 파싱 가능하도록
            e.claims
        }
    }
    
    /**
     * 토큰 메타데이터 조회
     */
    fun getTokenMetadata(token: String): TokenMetadata? {
        return try {
            val claims = parseClaims(token)
            TokenMetadata(
                jti = claims.id,
                subject = claims.subject,
                issuer = claims.issuer,
                audience = claims.audience,
                issuedAt = claims.issuedAt,
                expiration = claims.expiration,
                notBefore = claims.notBefore,
                tokenType = claims[TOKEN_TYPE_KEY]?.toString(),
                userId = claims[USER_ID_KEY]?.toString()?.let { UUID.fromString(it) },
                email = claims[EMAIL_KEY]?.toString()
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * 사용자 주체 정보
 */
data class UserPrincipal(
    val userId: UUID,
    val username: String,
    val email: String,
    val authorities: Collection<GrantedAuthority> = emptyList()
) : org.springframework.security.core.userdetails.UserDetails {
    
    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
    override fun getPassword(): String = "" // JWT에서는 사용하지 않음
    override fun getUsername(): String = username
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}

/**
 * 토큰 쌍 (액세스 + 리프레시)
 */
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer"
)

/**
 * 토큰 메타데이터
 */
data class TokenMetadata(
    val jti: String?,
    val subject: String?,
    val issuer: String?,
    val audience: String?,
    val issuedAt: Date?,
    val expiration: Date?,
    val notBefore: Date?,
    val tokenType: String?,
    val userId: UUID?,
    val email: String?
)

/**
 * 토큰 블랙리스트 서비스 인터페이스
 */
interface TokenBlacklistService {
    fun addToBlacklist(jti: String, expiration: Date)
    fun isBlacklisted(token: String): Boolean
    fun cleanupExpired()
}

/**
 * Redis 기반 토큰 블랙리스트 구현
 */
@Component
class RedisTokenBlacklistService(
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, String>
) : TokenBlacklistService {
    
    companion object {
        private const val BLACKLIST_PREFIX = "token:blacklist:"
        private const val TOKEN_JTI_PREFIX = "token:jti:"
    }
    
    override fun addToBlacklist(jti: String, expiration: Date) {
        val key = BLACKLIST_PREFIX + jti
        val ttl = (expiration.time - System.currentTimeMillis()) / 1000
        
        if (ttl > 0) {
            redisTemplate.opsForValue().set(key, "blacklisted", java.time.Duration.ofSeconds(ttl))
        }
    }
    
    override fun isBlacklisted(token: String): Boolean {
        return try {
            // JWT에서 JTI 추출
            val jti = extractJtiFromToken(token)
            val key = BLACKLIST_PREFIX + jti
            redisTemplate.hasKey(key)
        } catch (e: Exception) {
            // 토큰 파싱 실패시 블랙리스트된 것으로 간주
            true
        }
    }
    
    override fun cleanupExpired() {
        // Redis TTL로 자동 정리되므로 별도 구현 불필요
    }
    
    private fun extractJtiFromToken(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return null
            
            val payload = String(Base64.getUrlDecoder().decode(parts[1]))
            val claims = com.fasterxml.jackson.databind.ObjectMapper().readTree(payload)
            claims.get("jti")?.asText()
        } catch (e: Exception) {
            null
        }
    }
}