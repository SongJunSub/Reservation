package com.example.reservation.security.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * JWT 인증 실패 진입점 (Kotlin WebFlux)
 * 
 * 기능:
 * 1. 인증되지 않은 요청에 대한 응답 처리
 * 2. 일관된 에러 응답 형식 제공
 * 3. 보안 로깅 및 모니터링
 * 4. 리액티브 스트림 기반 응답 생성
 * 
 * Kotlin 특징:
 * - data class를 통한 간결한 응답 구조
 * - String 템플릿 문법
 * - 함수형 리액티브 프로그래밍
 * - when 표현식을 통한 조건 처리
 */
@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : ServerAuthenticationEntryPoint {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)
    }

    /**
     * 인증 실패시 호출되는 메서드
     * WebFlux의 리액티브 응답 생성
     */
    override fun commence(
        exchange: ServerWebExchange,
        ex: AuthenticationException
    ): Mono<Void> {
        val request = exchange.request
        val response = exchange.response

        // 보안 로깅
        logger.warn(
            "인증되지 않은 접근 시도 - IP: {}, URI: {}, User-Agent: {}, 오류: {}",
            getClientIpAddress(exchange),
            request.uri,
            request.headers.getFirst("User-Agent"),
            ex.message
        )

        // 응답 헤더 설정
        response.statusCode = HttpStatus.UNAUTHORIZED
        response.headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        response.headers.add("Cache-Control", "no-cache, no-store, must-revalidate")
        response.headers.add("Pragma", "no-cache")
        response.headers.add("Expires", "0")

        // 에러 응답 생성
        val errorResponse = createErrorResponse(request.uri.toString(), ex)
        
        return response.writeWith(
            Mono.fromCallable {
                val jsonBytes = objectMapper.writeValueAsBytes(errorResponse)
                response.bufferFactory().wrap(jsonBytes)
            }
        ).doOnError { error ->
            logger.error("인증 에러 응답 생성 중 오류 발생", error)
        }
    }

    /**
     * 에러 응답 객체 생성
     * Kotlin data class와 when 표현식 활용
     */
    private fun createErrorResponse(path: String, ex: AuthenticationException): ErrorResponse {
        val errorCode = when {
            ex.message?.contains("expired", ignoreCase = true) == true -> "TOKEN_EXPIRED"
            ex.message?.contains("invalid", ignoreCase = true) == true -> "INVALID_TOKEN"
            ex.message?.contains("malformed", ignoreCase = true) == true -> "MALFORMED_TOKEN"
            else -> "AUTHENTICATION_REQUIRED"
        }

        val errorMessage = when (errorCode) {
            "TOKEN_EXPIRED" -> "인증 토큰이 만료되었습니다. 다시 로그인해 주세요."
            "INVALID_TOKEN" -> "유효하지 않은 인증 토큰입니다."
            "MALFORMED_TOKEN" -> "잘못된 형식의 인증 토큰입니다."
            else -> "인증이 필요합니다. 로그인 후 다시 시도해 주세요."
        }

        return ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            code = errorCode,
            message = errorMessage,
            path = path,
            suggestion = getSuggestion(errorCode)
        )
    }

    /**
     * 에러 코드별 해결 방안 제안
     * Kotlin when 표현식의 간결함
     */
    private fun getSuggestion(errorCode: String): String = when (errorCode) {
        "TOKEN_EXPIRED" -> "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받거나, 다시 로그인해 주세요."
        "INVALID_TOKEN", "MALFORMED_TOKEN" -> "올바른 Bearer 토큰을 Authorization 헤더에 포함하여 요청해 주세요."
        else -> "POST /api/auth/login 엔드포인트를 통해 로그인한 후, 발급받은 토큰을 사용해 주세요."
    }

    /**
     * 클라이언트 IP 주소 추출
     * Kotlin의 Elvis 연산자와 let 함수 활용
     */
    private fun getClientIpAddress(exchange: ServerWebExchange): String {
        val request = exchange.request
        
        return listOf(
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Forwarded",
            "X-Cluster-Client-IP"
        ).asSequence()
            .mapNotNull { headerName -> 
                request.headers.getFirst(headerName)
                    ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                    ?.split(",")?.firstOrNull()?.trim()
            }
            .firstOrNull()
            ?: request.remoteAddress?.address?.hostAddress
            ?: "unknown"
    }
}

/**
 * 에러 응답 데이터 클래스
 * 
 * Kotlin data class 특징:
 * - 자동으로 equals, hashCode, toString 생성
 * - JSON 직렬화에 최적화
 * - 불변 객체로 설계
 */
data class ErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val suggestion: String? = null,
    val details: Map<String, Any>? = null
) {
    /**
     * 추가 컨텍스트 정보 포함을 위한 편의 메서드
     */
    fun withDetails(additionalDetails: Map<String, Any>): ErrorResponse {
        return copy(details = (details ?: emptyMap()) + additionalDetails)
    }

    /**
     * 디버그 정보 포함 여부 (프로덕션에서는 제외)
     */
    fun withDebugInfo(debugInfo: Map<String, Any>, includeDebug: Boolean = false): ErrorResponse {
        return if (includeDebug) {
            withDetails(mapOf("debug" to debugInfo))
        } else {
            this
        }
    }
}