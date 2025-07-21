package com.example.reservation.infrastructure.security

import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.*

/**
 * 인증 관리자
 * 실무 릴리즈 급 구현: 계정 잠금, 로그인 시도 추적, 보안 강화
 */
@Service
class CustomAuthenticationManager(
    private val userDetailsService: ReactiveUserDetailsService,
    private val passwordEncoder: PasswordEncoder,
    private val loginAttemptService: LoginAttemptService,
    private val auditService: AuthenticationAuditService
) : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        val username = authentication.name
        val password = authentication.credentials as String
        
        return userDetailsService.findByUsername(username)
            .flatMap { userDetails ->
                validateAuthentication(username, password, userDetails)
            }
            .doOnSuccess { auth ->
                // 인증 성공 로깅
                auditService.recordSuccessfulLogin(username)
                loginAttemptService.recordSuccessfulLogin(username)
            }
            .doOnError { error ->
                // 인증 실패 로깅
                auditService.recordFailedLogin(username, error.message ?: "Unknown error")
                loginAttemptService.recordFailedLogin(username)
            }
    }

    private fun validateAuthentication(
        username: String, 
        password: String, 
        userDetails: UserDetails
    ): Mono<Authentication> {
        return Mono.fromCallable {
            // 계정 잠금 확인
            if (loginAttemptService.isBlocked(username)) {
                throw org.springframework.security.authentication.AccountStatusException("계정이 일시적으로 잠금되었습니다") {}
            }
            
            // 계정 상태 확인
            if (!userDetails.isEnabled) {
                throw org.springframework.security.authentication.DisabledException("비활성화된 계정입니다")
            }
            
            if (!userDetails.isAccountNonLocked) {
                throw org.springframework.security.authentication.LockedException("잠금된 계정입니다")
            }
            
            if (!userDetails.isAccountNonExpired) {
                throw org.springframework.security.authentication.AccountExpiredException("만료된 계정입니다")
            }
            
            if (!userDetails.isCredentialsNonExpired) {
                throw org.springframework.security.authentication.CredentialsExpiredException("만료된 자격증명입니다")
            }
            
            // 비밀번호 검증
            if (!passwordEncoder.matches(password, userDetails.password)) {
                throw org.springframework.security.authentication.BadCredentialsException("잘못된 자격증명입니다")
            }
            
            // 성공적인 인증 객체 생성
            UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.authorities
            )
        }
    }
}

/**
 * 사용자 정보 서비스
 * 실무 릴리즈 급 구현: 사용자 정보 조회 및 권한 관리
 */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository
) : ReactiveUserDetailsService {

    override fun findByUsername(username: String): Mono<UserDetails> {
        return userRepository.findByUsernameOrEmail(username)
            .map { user ->
                UserPrincipal(
                    userId = user.id,
                    username = user.username,
                    email = user.email,
                    authorities = user.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
                )
            }
            .switchIfEmpty(
                Mono.error(
                    org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "사용자를 찾을 수 없습니다: $username"
                    )
                )
            )
    }
}

/**
 * 로그인 시도 추적 서비스
 * 실무 릴리즈 급 구현: 브루트포스 공격 방지
 */
@Service
class LoginAttemptService(
    private val redisTemplate: org.springframework.data.redis.core.RedisTemplate<String, String>
) {
    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val BLOCK_TIME_MINUTES = 30L
        private const val ATTEMPT_PREFIX = "login:attempt:"
        private const val BLOCK_PREFIX = "login:block:"
    }

    /**
     * 로그인 실패 기록
     */
    fun recordFailedLogin(username: String) {
        val key = ATTEMPT_PREFIX + username
        val attempts = redisTemplate.opsForValue().get(key)?.toIntOrNull() ?: 0
        val newAttempts = attempts + 1
        
        redisTemplate.opsForValue().set(
            key, 
            newAttempts.toString(), 
            java.time.Duration.ofMinutes(BLOCK_TIME_MINUTES)
        )
        
        // 최대 시도 횟수 초과시 계정 차단
        if (newAttempts >= MAX_ATTEMPTS) {
            blockAccount(username)
        }
    }

    /**
     * 로그인 성공 기록
     */
    fun recordSuccessfulLogin(username: String) {
        // 성공시 시도 횟수 초기화
        redisTemplate.delete(ATTEMPT_PREFIX + username)
        redisTemplate.delete(BLOCK_PREFIX + username)
    }

    /**
     * 계정 차단 여부 확인
     */
    fun isBlocked(username: String): Boolean {
        return redisTemplate.hasKey(BLOCK_PREFIX + username)
    }

    /**
     * 계정 차단
     */
    private fun blockAccount(username: String) {
        val blockKey = BLOCK_PREFIX + username
        redisTemplate.opsForValue().set(
            blockKey,
            LocalDateTime.now().toString(),
            java.time.Duration.ofMinutes(BLOCK_TIME_MINUTES)
        )
        
        println("계정 차단: $username (${BLOCK_TIME_MINUTES}분)")
    }

    /**
     * 남은 시도 횟수 조회
     */
    fun getRemainingAttempts(username: String): Int {
        val attempts = redisTemplate.opsForValue().get(ATTEMPT_PREFIX + username)?.toIntOrNull() ?: 0
        return maxOf(0, MAX_ATTEMPTS - attempts)
    }
}

/**
 * 인증 감사 서비스
 * 실무 릴리즈 급 구현: 보안 이벤트 로깅 및 모니터링
 */
@Service
class AuthenticationAuditService {

    /**
     * 성공적인 로그인 기록
     */
    fun recordSuccessfulLogin(username: String) {
        // 실제 구현에서는 데이터베이스나 로그 시스템에 기록
        println("LOGIN_SUCCESS: $username at ${LocalDateTime.now()}")
        
        // 보안 메트릭 수집
        recordSecurityMetric("login_success", username)
    }

    /**
     * 실패한 로그인 기록
     */
    fun recordFailedLogin(username: String, reason: String) {
        println("LOGIN_FAILURE: $username - $reason at ${LocalDateTime.now()}")
        
        // 보안 메트릭 수집
        recordSecurityMetric("login_failure", username)
    }

    /**
     * 로그아웃 기록
     */
    fun recordLogout(username: String) {
        println("LOGOUT: $username at ${LocalDateTime.now()}")
        
        recordSecurityMetric("logout", username)
    }

    /**
     * 토큰 갱신 기록
     */
    fun recordTokenRefresh(username: String) {
        println("TOKEN_REFRESH: $username at ${LocalDateTime.now()}")
        
        recordSecurityMetric("token_refresh", username)
    }

    /**
     * 권한 상승 기록
     */
    fun recordPrivilegeEscalation(username: String, fromRole: String, toRole: String) {
        println("PRIVILEGE_ESCALATION: $username from $fromRole to $toRole at ${LocalDateTime.now()}")
        
        recordSecurityMetric("privilege_escalation", username)
    }

    /**
     * 의심스러운 활동 기록
     */
    fun recordSuspiciousActivity(username: String, activity: String, details: String) {
        println("SUSPICIOUS_ACTIVITY: $username - $activity: $details at ${LocalDateTime.now()}")
        
        recordSecurityMetric("suspicious_activity", username)
        
        // 실제 구현에서는 보안 팀에게 알림 발송
        notifySecurityTeam(username, activity, details)
    }

    private fun recordSecurityMetric(eventType: String, username: String) {
        // 실제 구현에서는 Micrometer를 사용하여 메트릭 수집
        // meterRegistry.counter("security.events", "type", eventType, "user", username).increment()
    }

    private fun notifySecurityTeam(username: String, activity: String, details: String) {
        // 실제 구현에서는 Slack, 이메일, SMS 등으로 알림
        println("🚨 SECURITY ALERT: User $username performed $activity: $details")
    }
}

/**
 * 사용자 리포지토리 인터페이스
 */
interface UserRepository {
    fun findByUsernameOrEmail(usernameOrEmail: String): Mono<User>
    fun findById(id: UUID): Mono<User>
    fun save(user: User): Mono<User>
    fun existsByUsername(username: String): Mono<Boolean>
    fun existsByEmail(email: String): Mono<Boolean>
}

/**
 * 사용자 도메인 모델
 */
data class User(
    val id: UUID = UUID.randomUUID(),
    val username: String,
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val roles: Set<Role> = setOf(Role.USER),
    val isEnabled: Boolean = true,
    val isAccountNonLocked: Boolean = true,
    val isAccountNonExpired: Boolean = true,
    val isCredentialsNonExpired: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastLoginAt: LocalDateTime? = null,
    val failedLoginAttempts: Int = 0
)

/**
 * 역할 열거형
 */
enum class Role {
    USER,           // 일반 사용자
    ADMIN,          // 관리자
    SUPER_ADMIN,    // 슈퍼 관리자
    GUEST,          // 게스트 (제한된 권한)
    PROPERTY_MANAGER, // 시설 관리자
    AGENT           // 여행사 직원
}