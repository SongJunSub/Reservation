package com.example.reservation.exception

import java.time.LocalDateTime

/**
 * 예약 시스템 예외 계층
 * 비즈니스 도메인별로 구조화된 예외 처리
 */

/**
 * 최상위 비즈니스 예외
 */
abstract class BusinessException(
    message: String,
    cause: Throwable? = null,
    val errorCode: String,
    val errorDetails: Map<String, Any> = emptyMap()
) : RuntimeException(message, cause) {
    
    val timestamp: LocalDateTime = LocalDateTime.now()
    
    abstract fun getHttpStatus(): Int
}

/**
 * 예약 도메인 예외 기본 클래스
 */
abstract class ReservationException(
    message: String,
    cause: Throwable? = null,
    errorCode: String,
    errorDetails: Map<String, Any> = emptyMap()
) : BusinessException(message, cause, errorCode, errorDetails)

// === 예약 생성 관련 예외 ===

/**
 * 예약 생성 실패 예외
 */
class ReservationCreationException(
    message: String,
    cause: Throwable? = null,
    errorDetails: Map<String, Any> = emptyMap()
) : ReservationException(message, cause, "RESERVATION_CREATION_FAILED", errorDetails) {
    
    override fun getHttpStatus(): Int = 400 // Bad Request
}

/**
 * 객실 가용성 부족 예외
 */
class RoomUnavailableException(
    message: String,
    val roomId: Long,
    val requestedCheckIn: String,
    val requestedCheckOut: String,
    cause: Throwable? = null
) : ReservationException(
    message, 
    cause, 
    "ROOM_UNAVAILABLE",
    mapOf(
        "roomId" to roomId,
        "requestedCheckIn" to requestedCheckIn,
        "requestedCheckOut" to requestedCheckOut
    )
) {
    override fun getHttpStatus(): Int = 409 // Conflict
}

/**
 * 예약 정책 위반 예외
 */
class ReservationPolicyViolationException(
    message: String,
    val policyType: String,
    val violatedRule: String,
    cause: Throwable? = null
) : ReservationException(
    message, 
    cause, 
    "POLICY_VIOLATION",
    mapOf(
        "policyType" to policyType,
        "violatedRule" to violatedRule
    )
) {
    override fun getHttpStatus(): Int = 422 // Unprocessable Entity
}

// === 예약 조회 관련 예외 ===

/**
 * 예약 미발견 예외
 */
class ReservationNotFoundException(
    val searchCriteria: String,
    val searchValue: String
) : ReservationException(
    "예약을 찾을 수 없습니다: $searchCriteria = $searchValue",
    null,
    "RESERVATION_NOT_FOUND",
    mapOf(
        "searchCriteria" to searchCriteria,
        "searchValue" to searchValue
    )
) {
    override fun getHttpStatus(): Int = 404 // Not Found
}

// === 예약 수정 관련 예외 ===

/**
 * 예약 수정 불가 예외
 */
class ReservationNotModifiableException(
    message: String,
    val reservationId: Long,
    val currentStatus: String,
    val attemptedAction: String
) : ReservationException(
    message,
    null,
    "RESERVATION_NOT_MODIFIABLE",
    mapOf(
        "reservationId" to reservationId,
        "currentStatus" to currentStatus,
        "attemptedAction" to attemptedAction
    )
) {
    override fun getHttpStatus(): Int = 409 // Conflict
}

/**
 * 예약 취소 불가 예외
 */
class ReservationNotCancellableException(
    message: String,
    val reservationId: Long,
    val currentStatus: String,
    val cancellationPolicy: String
) : ReservationException(
    message,
    null,
    "RESERVATION_NOT_CANCELLABLE",
    mapOf(
        "reservationId" to reservationId,
        "currentStatus" to currentStatus,
        "cancellationPolicy" to cancellationPolicy
    )
) {
    override fun getHttpStatus(): Int = 409 // Conflict
}

// === 결제 관련 예외 ===

/**
 * 결제 처리 예외
 */
class PaymentProcessingException(
    message: String,
    val paymentId: String? = null,
    val paymentMethod: String? = null,
    cause: Throwable? = null
) : BusinessException(
    message,
    cause,
    "PAYMENT_PROCESSING_FAILED",
    mapOf(
        "paymentId" to (paymentId ?: "unknown"),
        "paymentMethod" to (paymentMethod ?: "unknown")
    )
) {
    override fun getHttpStatus(): Int = 402 // Payment Required
}

/**
 * 환불 처리 예외
 */
class RefundProcessingException(
    message: String,
    val refundAmount: String,
    val originalPaymentId: String? = null,
    cause: Throwable? = null
) : BusinessException(
    message,
    cause,
    "REFUND_PROCESSING_FAILED",
    mapOf(
        "refundAmount" to refundAmount,
        "originalPaymentId" to (originalPaymentId ?: "unknown")
    )
) {
    override fun getHttpStatus(): Int = 500 // Internal Server Error
}

// === 검증 관련 예외 ===

/**
 * 입력 검증 예외
 */
class ValidationException(
    message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val globalErrors: List<String> = emptyList()
) : BusinessException(
    message,
    null,
    "VALIDATION_FAILED",
    mapOf(
        "fieldErrors" to fieldErrors,
        "globalErrors" to globalErrors
    )
) {
    override fun getHttpStatus(): Int = 400 // Bad Request
    
    companion object {
        /**
         * 단일 필드 검증 실패
         */
        fun fieldError(field: String, error: String): ValidationException {
            return ValidationException(
                "검증 실패: $field",
                fieldErrors = mapOf(field to error)
            )
        }
        
        /**
         * 다중 필드 검증 실패
         */
        fun multipleFieldErrors(errors: Map<String, String>): ValidationException {
            return ValidationException(
                "다중 필드 검증 실패",
                fieldErrors = errors
            )
        }
        
        /**
         * 글로벌 검증 실패
         */
        fun globalError(error: String): ValidationException {
            return ValidationException(
                "글로벌 검증 실패",
                globalErrors = listOf(error)
            )
        }
    }
}

// === 외부 시스템 연동 예외 ===

/**
 * 외부 서비스 호출 예외
 */
class ExternalServiceException(
    message: String,
    val serviceName: String,
    val operation: String,
    cause: Throwable? = null
) : BusinessException(
    message,
    cause,
    "EXTERNAL_SERVICE_ERROR",
    mapOf(
        "serviceName" to serviceName,
        "operation" to operation
    )
) {
    override fun getHttpStatus(): Int = 503 // Service Unavailable
}

/**
 * 알림 발송 실패 예외
 */
class NotificationFailureException(
    message: String,
    val notificationType: String,
    val recipient: String,
    cause: Throwable? = null
) : BusinessException(
    message,
    cause,
    "NOTIFICATION_FAILED",
    mapOf(
        "notificationType" to notificationType,
        "recipient" to recipient
    )
) {
    override fun getHttpStatus(): Int = 500 // Internal Server Error
}

// === 동시성 관련 예외 ===

/**
 * 낙관적 락 실패 예외
 */
class OptimisticLockException(
    message: String,
    val entityId: String,
    val entityType: String
) : BusinessException(
    message,
    null,
    "OPTIMISTIC_LOCK_FAILED",
    mapOf(
        "entityId" to entityId,
        "entityType" to entityType
    )
) {
    override fun getHttpStatus(): Int = 409 // Conflict
}

/**
 * 리소스 경합 예외
 */
class ResourceContentionException(
    message: String,
    val resourceId: String,
    val resourceType: String
) : BusinessException(
    message,
    null,
    "RESOURCE_CONTENTION",
    mapOf(
        "resourceId" to resourceId,
        "resourceType" to resourceType
    )
) {
    override fun getHttpStatus(): Int = 429 // Too Many Requests
}