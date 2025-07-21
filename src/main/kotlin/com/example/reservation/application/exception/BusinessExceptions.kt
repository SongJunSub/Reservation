package com.example.reservation.application.exception

import java.time.LocalDateTime
import java.util.*

/**
 * 비즈니스 예외 기본 클래스
 */
abstract class BusinessException(
    message: String,
    val errorCode: String,
    val details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 예약 관련 비즈니스 예외
 */
sealed class ReservationException(
    message: String,
    errorCode: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : BusinessException(message, errorCode, details, cause)

/**
 * 예약 비즈니스 규칙 예외
 */
class ReservationBusinessException(
    message: String,
    errorCode: String = "RESERVATION_BUSINESS_ERROR",
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : ReservationException(message, errorCode, details, cause)

/**
 * 예약 검증 예외
 */
class ReservationValidationException(
    message: String,
    val validationErrors: List<ValidationError>,
    errorCode: String = "RESERVATION_VALIDATION_ERROR",
    details: Map<String, Any> = emptyMap()
) : ReservationException(message, errorCode, details + mapOf("validationErrors" to validationErrors))

/**
 * 예약 찾기 실패 예외
 */
class ReservationNotFoundException(
    reservationId: UUID,
    errorCode: String = "RESERVATION_NOT_FOUND"
) : ReservationException(
    "예약을 찾을 수 없습니다: $reservationId",
    errorCode,
    mapOf("reservationId" to reservationId)
)

/**
 * 예약 상태 변경 불가 예외
 */
class ReservationStatusException(
    currentStatus: String,
    targetStatus: String,
    reservationId: UUID,
    errorCode: String = "INVALID_STATUS_TRANSITION"
) : ReservationException(
    "예약 상태를 $currentStatus 에서 $targetStatus 로 변경할 수 없습니다",
    errorCode,
    mapOf("currentStatus" to currentStatus, "targetStatus" to targetStatus, "reservationId" to reservationId)
)

/**
 * 취소 정책 위반 예외
 */
class CancellationPolicyException(
    message: String,
    val cancellationDeadline: LocalDateTime,
    val currentTime: LocalDateTime = LocalDateTime.now(),
    errorCode: String = "CANCELLATION_POLICY_VIOLATION"
) : ReservationException(
    message,
    errorCode,
    mapOf("cancellationDeadline" to cancellationDeadline, "currentTime" to currentTime)
)

/**
 * 고객 관련 예외
 */
sealed class GuestException(
    message: String,
    errorCode: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : BusinessException(message, errorCode, details, cause)

/**
 * 고객 찾기 실패 예외
 */
class GuestNotFoundException(
    guestId: UUID,
    errorCode: String = "GUEST_NOT_FOUND"
) : GuestException(
    "고객을 찾을 수 없습니다: $guestId",
    errorCode,
    mapOf("guestId" to guestId)
)

/**
 * 중복 고객 등록 예외
 */
class DuplicateGuestException(
    email: String,
    existingGuestId: UUID,
    errorCode: String = "DUPLICATE_GUEST"
) : GuestException(
    "이미 등록된 이메일입니다: $email",
    errorCode,
    mapOf("email" to email, "existingGuestId" to existingGuestId)
)

/**
 * 고객 계정 상태 예외
 */
class GuestStatusException(
    guestId: UUID,
    currentStatus: String,
    requiredStatus: String,
    errorCode: String = "INVALID_GUEST_STATUS"
) : GuestException(
    "고객 계정 상태가 올바르지 않습니다. 현재: $currentStatus, 필요: $requiredStatus",
    errorCode,
    mapOf("guestId" to guestId, "currentStatus" to currentStatus, "requiredStatus" to requiredStatus)
)

/**
 * 결제 관련 예외
 */
sealed class PaymentException(
    message: String,
    errorCode: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : BusinessException(message, errorCode, details, cause)

/**
 * 결제 처리 실패 예외
 */
class PaymentProcessingException(
    message: String,
    val gatewayErrorCode: String? = null,
    val gatewayMessage: String? = null,
    errorCode: String = "PAYMENT_PROCESSING_ERROR",
    cause: Throwable? = null
) : PaymentException(
    message,
    errorCode,
    mapOf(
        "gatewayErrorCode" to gatewayErrorCode,
        "gatewayMessage" to gatewayMessage
    ).filterValues { it != null },
    cause
)

/**
 * 결제 승인 실패 예외
 */
class PaymentAuthorizationException(
    message: String,
    val reason: String,
    errorCode: String = "PAYMENT_AUTHORIZATION_FAILED"
) : PaymentException(
    message,
    errorCode,
    mapOf("reason" to reason)
)

/**
 * 환불 처리 불가 예외
 */
class RefundNotAllowedException(
    paymentId: UUID,
    reason: String,
    errorCode: String = "REFUND_NOT_ALLOWED"
) : PaymentException(
    "환불 처리가 불가능합니다: $reason",
    errorCode,
    mapOf("paymentId" to paymentId, "reason" to reason)
)

/**
 * 잘못된 결제 금액 예외
 */
class InvalidPaymentAmountException(
    requestedAmount: String,
    validRange: String,
    errorCode: String = "INVALID_PAYMENT_AMOUNT"
) : PaymentException(
    "잘못된 결제 금액입니다. 요청: $requestedAmount, 유효범위: $validRange",
    errorCode,
    mapOf("requestedAmount" to requestedAmount, "validRange" to validRange)
)

/**
 * 객실 관련 예외
 */
sealed class RoomException(
    message: String,
    errorCode: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : BusinessException(message, errorCode, details, cause)

/**
 * 객실 가용성 부족 예외
 */
class RoomUnavailableException(
    roomTypeId: UUID,
    checkInDate: String,
    checkOutDate: String,
    requestedRooms: Int,
    availableRooms: Int,
    errorCode: String = "ROOM_UNAVAILABLE"
) : RoomException(
    "요청하신 객실이 가용하지 않습니다. 객실유형: $roomTypeId, 기간: $checkInDate - $checkOutDate",
    errorCode,
    mapOf(
        "roomTypeId" to roomTypeId,
        "checkInDate" to checkInDate,
        "checkOutDate" to checkOutDate,
        "requestedRooms" to requestedRooms,
        "availableRooms" to availableRooms
    )
)

/**
 * 객실 찾기 실패 예외
 */
class RoomNotFoundException(
    roomId: UUID,
    errorCode: String = "ROOM_NOT_FOUND"
) : RoomException(
    "객실을 찾을 수 없습니다: $roomId",
    errorCode,
    mapOf("roomId" to roomId)
)

/**
 * 요금 계획 찾기 실패 예외
 */
class RatePlanNotFoundException(
    ratePlanCode: String,
    errorCode: String = "RATE_PLAN_NOT_FOUND"
) : RoomException(
    "요금 계획을 찾을 수 없습니다: $ratePlanCode",
    errorCode,
    mapOf("ratePlanCode" to ratePlanCode)
)

/**
 * 재고 관리 예외
 */
class InventoryManagementException(
    message: String,
    val operation: String,
    errorCode: String = "INVENTORY_MANAGEMENT_ERROR"
) : RoomException(
    message,
    errorCode,
    mapOf("operation" to operation)
)

/**
 * 시스템 예외
 */
sealed class SystemException(
    message: String,
    errorCode: String,
    details: Map<String, Any> = emptyMap(),
    cause: Throwable? = null
) : BusinessException(message, errorCode, details, cause)

/**
 * 외부 서비스 호출 실패 예외
 */
class ExternalServiceException(
    serviceName: String,
    operation: String,
    message: String,
    errorCode: String = "EXTERNAL_SERVICE_ERROR",
    cause: Throwable? = null
) : SystemException(
    "외부 서비스 호출 실패: $serviceName.$operation - $message",
    errorCode,
    mapOf("serviceName" to serviceName, "operation" to operation),
    cause
)

/**
 * 동시성 처리 예외
 */
class ConcurrencyException(
    resource: String,
    operation: String,
    errorCode: String = "CONCURRENCY_ERROR"
) : SystemException(
    "동시성 처리 중 충돌이 발생했습니다: $resource.$operation",
    errorCode,
    mapOf("resource" to resource, "operation" to operation)
)

/**
 * 데이터 무결성 예외
 */
class DataIntegrityException(
    message: String,
    val constraint: String,
    errorCode: String = "DATA_INTEGRITY_ERROR",
    cause: Throwable? = null
) : SystemException(
    message,
    errorCode,
    mapOf("constraint" to constraint),
    cause
)

/**
 * 검증 오류 상세 정보
 */
data class ValidationError(
    val field: String,
    val code: String,
    val message: String,
    val rejectedValue: Any? = null,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
)

/**
 * 검증 심각도
 */
enum class ValidationSeverity {
    WARNING, ERROR, CRITICAL
}