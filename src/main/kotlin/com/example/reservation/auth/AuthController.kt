package com.example.reservation.auth

import com.example.reservation.security.jwt.JwtTokenProvider
import com.example.reservation.security.jwt.TokenPair
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/**
 * 인증 컨트롤러 (Kotlin)
 * 
 * 기능:
 * 1. 사용자 로그인/로그아웃
 * 2. 회원가입
 * 3. 토큰 갱신
 * 4. 토큰 검증
 * 
 * Kotlin 특징:
 * - data class를 통한 간결한 DTO 정의
 * - when 표현식을 통한 조건 처리
 * - null 안전성과 Elvis 연산자
 * - 확장 함수와 스코프 함수 활용
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val userService: UserService // 실제 구현에서는 UserService 주입
) {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }

    /**
     * 로그인
     * Kotlin의 간결한 함수 정의와 when 표현식 활용
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return try {
            logger.info("로그인 시도: {}", request.username)

            // 사용자 인증
            val authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password)
            )

            // JWT 토큰 생성
            val tokenPair = jwtTokenProvider.generateTokenPair(authentication)

            logger.info("로그인 성공: {}", request.username)
            
            ResponseEntity.ok(
                AuthResponse.success(
                    message = "로그인이 성공적으로 완료되었습니다.",
                    tokenPair = tokenPair,
                    userInfo = UserInfo(
                        username = authentication.name,
                        authorities = authentication.authorities.map { it.authority }
                    )
                )
            )
        } catch (ex: Exception) {
            logger.warn("로그인 실패: {} - {}", request.username, ex.message)
            
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                    AuthResponse.failure(
                        message = "로그인에 실패했습니다. 사용자명과 비밀번호를 확인해 주세요.",
                        error = "AUTHENTICATION_FAILED"
                    )
                )
        }
    }

    /**
     * 회원가입
     * Kotlin의 스코프 함수(let, run 등) 활용
     */
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return try {
            logger.info("회원가입 시도: {}", request.username)

            // 사용자 존재 여부 확인 (실제 구현에서는 UserService 사용)
            if (isUsernameTaken(request.username)) {
                return ResponseEntity.badRequest()
                    .body(
                        AuthResponse.failure(
                            message = "이미 사용 중인 사용자명입니다.",
                            error = "USERNAME_ALREADY_EXISTS"
                        )
                    )
            }

            // 비밀번호 암호화 및 사용자 생성
            val encodedPassword = passwordEncoder.encode(request.password)
            val authorities = determineUserAuthorities(request.role)

            // 임시 Authentication 객체 생성 (실제로는 사용자 생성 후 인증)
            val authentication = UsernamePasswordAuthenticationToken(
                request.username,
                null,
                authorities
            )

            // JWT 토큰 생성
            val tokenPair = jwtTokenProvider.generateTokenPair(authentication)

            logger.info("회원가입 성공: {}", request.username)

            ResponseEntity.status(HttpStatus.CREATED)
                .body(
                    AuthResponse.success(
                        message = "회원가입이 성공적으로 완료되었습니다.",
                        tokenPair = tokenPair,
                        userInfo = UserInfo(
                            username = request.username,
                            authorities = authorities.map { it.authority }
                        )
                    )
                )
        } catch (ex: Exception) {
            logger.error("회원가입 실패: {} - {}", request.username, ex.message)
            
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    AuthResponse.failure(
                        message = "회원가입 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
                        error = "REGISTRATION_FAILED"
                    )
                )
        }
    }

    /**
     * 토큰 갱신
     * Kotlin의 안전한 호출 연산자 활용
     */
    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<AuthResponse> {
        return try {
            val validationResult = jwtTokenProvider.validateToken(request.refreshToken)
            
            when {
                validationResult.isRefreshToken -> {
                    val username = jwtTokenProvider.getUsernameFromToken(request.refreshToken)
                    val authorities = jwtTokenProvider.getAuthoritiesFromToken(request.refreshToken)
                        .map { SimpleGrantedAuthority(it) }

                    // 새로운 Authentication 객체 생성
                    val authentication = UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        authorities
                    )

                    // 새로운 토큰 쌍 생성
                    val tokenPair = jwtTokenProvider.generateTokenPair(authentication)

                    logger.debug("토큰 갱신 성공: {}", username)

                    ResponseEntity.ok(
                        AuthResponse.success(
                            message = "토큰이 성공적으로 갱신되었습니다.",
                            tokenPair = tokenPair
                        )
                    )
                }
                else -> {
                    logger.warn("유효하지 않은 리프레시 토큰: {}", validationResult)
                    
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(
                            AuthResponse.failure(
                                message = "유효하지 않은 리프레시 토큰입니다.",
                                error = "INVALID_REFRESH_TOKEN"
                            )
                        )
                }
            }
        } catch (ex: Exception) {
            logger.error("토큰 갱신 실패: {}", ex.message)
            
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                    AuthResponse.failure(
                        message = "토큰 갱신에 실패했습니다.",
                        error = "TOKEN_REFRESH_FAILED"
                    )
                )
        }
    }

    /**
     * 토큰 검증
     */
    @PostMapping("/validate")
    fun validateToken(@Valid @RequestBody request: ValidateTokenRequest): ResponseEntity<TokenValidationResponse> {
        val validationResult = jwtTokenProvider.validateToken(request.token)
        
        return ResponseEntity.ok(
            TokenValidationResponse(
                valid = validationResult.isValid,
                expired = validationResult == com.example.reservation.security.jwt.TokenValidationResult.EXPIRED,
                type = when {
                    validationResult.isAccessToken -> "access"
                    validationResult.isRefreshToken -> "refresh"
                    else -> "unknown"
                },
                username = if (validationResult.isValid) jwtTokenProvider.getUsernameFromToken(request.token) else null,
                authorities = if (validationResult.isValid) jwtTokenProvider.getAuthoritiesFromToken(request.token) else emptyList(),
                expiresAt = jwtTokenProvider.getExpirationFromToken(request.token)
            )
        )
    }

    /**
     * 로그아웃 (토큰 무효화)
     * 실제 구현에서는 블랙리스트에 토큰 추가
     */
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<AuthResponse> {
        try {
            // 실제 구현에서는 토큰을 블랙리스트에 추가
            logger.info("로그아웃 요청")
            
            return ResponseEntity.ok(
                AuthResponse.success(
                    message = "로그아웃이 성공적으로 완료되었습니다."
                )
            )
        } catch (ex: Exception) {
            logger.error("로그아웃 실패: {}", ex.message)
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    AuthResponse.failure(
                        message = "로그아웃 중 오류가 발생했습니다.",
                        error = "LOGOUT_FAILED"
                    )
                )
        }
    }

    // === 헬퍼 메서드들 ===

    private fun isUsernameTaken(username: String): Boolean = false // 임시 구현

    private fun determineUserAuthorities(role: String?): List<SimpleGrantedAuthority> {
        return when (role?.uppercase()) {
            "ADMIN" -> listOf(
                SimpleGrantedAuthority("ROLE_USER"),
                SimpleGrantedAuthority("ROLE_MANAGER"), 
                SimpleGrantedAuthority("ROLE_ADMIN")
            )
            "MANAGER" -> listOf(
                SimpleGrantedAuthority("ROLE_USER"),
                SimpleGrantedAuthority("ROLE_MANAGER")
            )
            else -> listOf(SimpleGrantedAuthority("ROLE_USER"))
        }
    }
}

// === DTO 클래스들 (Kotlin data class) ===

data class LoginRequest(
    @field:NotBlank(message = "사용자명은 필수입니다")
    @field:Size(min = 3, max = 50, message = "사용자명은 3-50자 사이여야 합니다")
    val username: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다")
    val password: String
)

data class RegisterRequest(
    @field:NotBlank(message = "사용자명은 필수입니다")
    @field:Size(min = 3, max = 50, message = "사용자명은 3-50자 사이여야 합니다")
    val username: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다")
    @field:Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
    val password: String,
    
    @field:Email(message = "올바른 이메일 형식이어야 합니다")
    val email: String?,
    
    val fullName: String?,
    val role: String? = "USER"
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "리프레시 토큰은 필수입니다")
    val refreshToken: String
)

data class ValidateTokenRequest(
    @field:NotBlank(message = "토큰은 필수입니다")
    val token: String
)

data class LogoutRequest(
    @field:NotBlank(message = "액세스 토큰은 필수입니다")
    val accessToken: String,
    val refreshToken: String?
)

data class AuthResponse(
    val success: Boolean,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val data: AuthData? = null,
    val error: String? = null
) {
    companion object {
        fun success(message: String, tokenPair: TokenPair? = null, userInfo: UserInfo? = null) = 
            AuthResponse(
                success = true,
                message = message,
                data = tokenPair?.let { AuthData(it, userInfo) }
            )

        fun failure(message: String, error: String) = 
            AuthResponse(
                success = false,
                message = message,
                error = error
            )
    }
}

data class AuthData(
    val tokenPair: TokenPair,
    val userInfo: UserInfo? = null
)

data class UserInfo(
    val username: String,
    val authorities: List<String>,
    val email: String? = null,
    val fullName: String? = null
)

data class TokenValidationResponse(
    val valid: Boolean,
    val expired: Boolean,
    val type: String,
    val username: String?,
    val authorities: List<String>,
    val expiresAt: java.util.Date?
)

// 임시 UserService 인터페이스
interface UserService {
    fun existsByUsername(username: String): Boolean
    fun createUser(username: String, password: String, email: String?, fullName: String?): Any
}