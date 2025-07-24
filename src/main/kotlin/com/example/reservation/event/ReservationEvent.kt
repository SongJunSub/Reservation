package com.example.reservation.event

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 이벤트 계층 구조 (Kotlin)
 * 
 * 특징:
 * 1. sealed class를 통한 타입 안전한 이벤트 계층
 * 2. data class를 통한 간결한 이벤트 정의
 * 3. when 표현식을 통한 패턴 매칭
 * 4. JSON 다형성 직렬화 지원
 * 
 * Kotlin vs Java 비교:
 * - sealed class vs abstract class + enum
 * - data class vs POJO with builders
 * - copy() 메서드 vs 명시적 builder 패턴
 * - when 표현식 vs switch/if-else
 */

/**
 * 이벤트 기본 인터페이스
 */
interface DomainEvent {
    val eventId: String
    val aggregateId: String
    val eventType: String
    val timestamp: LocalDateTime
    val version: Int
    val correlationId: String?
    val causationId: String?
    val metadata: Map<String, Any>
}

/**
 * 예약 이벤트 기본 클래스
 * Kotlin sealed class를 통한 타입 안전성 보장
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(value = ReservationCreatedEvent::class, name = "RESERVATION_CREATED"),
    JsonSubTypes.Type(value = ReservationUpdatedEvent::class, name = "RESERVATION_UPDATED"),
    JsonSubTypes.Type(value = ReservationCancelledEvent::class, name = "RESERVATION_CANCELLED"),
    JsonSubTypes.Type(value = ReservationConfirmedEvent::class, name = "RESERVATION_CONFIRMED"),
    JsonSubTypes.Type(value = CheckInCompletedEvent::class, name = "CHECK_IN_COMPLETED"),
    JsonSubTypes.Type(value = CheckOutCompletedEvent::class, name = "CHECK_OUT_COMPLETED"),
    JsonSubTypes.Type(value = PaymentProcessedEvent::class, name = "PAYMENT_PROCESSED"),
    JsonSubTypes.Type(value = PaymentFailedEvent::class, name = "PAYMENT_FAILED")
)
sealed class ReservationEvent : DomainEvent {
    abstract val reservationId: Long
    abstract val guestId: Long
    abstract val roomId: Long
}

/**
 * 예약 생성 이벤트
 * Kotlin data class의 간결함과 불변성
 */
data class ReservationCreatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int = 1,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    // 예약 관련 필드
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val confirmationNumber: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val totalAmount: BigDecimal,
    val status: String,
    val specialRequests: List<String> = emptyList(),
    val source: String // "WEB", "MOBILE", "API" 등
) : ReservationEvent {
    override val eventType: String = "RESERVATION_CREATED"
    
    /**
     * 이벤트 정보 요약
     * Kotlin의 String template 활용
     */
    fun getSummary(): String = 
        "예약 생성: $confirmationNumber (객실: $roomId, 금액: $totalAmount)"
    
    /**
     * 숙박 일수 계산
     * Kotlin의 확장 속성 스타일
     */
    val nights: Long
        get() = java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate)
}

/**
 * 예약 수정 이벤트
 */
data class ReservationUpdatedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val changes: Map<String, Any>, // 변경된 필드와 값
    val previousValues: Map<String, Any>, // 이전 값들
    val reason: String? = null
) : ReservationEvent {
    override val eventType: String = "RESERVATION_UPDATED"
    
    /**
     * 변경사항 요약 생성
     * Kotlin 컬렉션 함수 활용
     */
    fun getChangesSummary(): String = 
        changes.entries.joinToString(", ") { (field, newValue) ->
            "$field: ${previousValues[field]} → $newValue"
        }
}

/**
 * 예약 취소 이벤트
 */
data class ReservationCancelledEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val cancellationReason: String,
    val refundAmount: BigDecimal?,
    val cancellationFee: BigDecimal?,
    val cancelledBy: String // "GUEST", "ADMIN", "SYSTEM"
) : ReservationEvent {
    override val eventType: String = "RESERVATION_CANCELLED"
    
    /**
     * 환불 정보 확인
     */
    val hasRefund: Boolean
        get() = refundAmount != null && refundAmount > BigDecimal.ZERO
}

/**
 * 예약 확정 이벤트
 */
data class ReservationConfirmedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val paymentId: String,
    val confirmedBy: String
) : ReservationEvent {
    override val eventType: String = "RESERVATION_CONFIRMED"
}

/**
 * 체크인 완료 이벤트
 */
data class CheckInCompletedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val actualCheckInTime: LocalDateTime,
    val keyCardIssued: Boolean,
    val additionalGuests: Int = 0,
    val staffId: String
) : ReservationEvent {
    override val eventType: String = "CHECK_IN_COMPLETED"
    
    /**
     * 지연 체크인 여부 확인
     */
    fun isLateCheckIn(scheduledCheckInDate: LocalDate): Boolean =
        actualCheckInTime.toLocalDate().isAfter(scheduledCheckInDate)
}

/**
 * 체크아웃 완료 이벤트
 */
data class CheckOutCompletedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val actualCheckOutTime: LocalDateTime,
    val finalBill: BigDecimal,
    val additionalCharges: Map<String, BigDecimal> = emptyMap(),
    val roomCondition: String, // "CLEAN", "DAMAGED", "REQUIRES_MAINTENANCE"
    val staffId: String
) : ReservationEvent {
    override val eventType: String = "CHECK_OUT_COMPLETED"
    
    /**
     * 추가 요금 총액 계산
     */
    val totalAdditionalCharges: BigDecimal
        get() = additionalCharges.values.fold(BigDecimal.ZERO) { acc, charge -> acc + charge }
}

/**
 * 결제 처리 이벤트
 */
data class PaymentProcessedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val paymentId: String,
    val amount: BigDecimal,
    val paymentMethod: String,
    val transactionId: String,
    val paymentGateway: String
) : ReservationEvent {
    override val eventType: String = "PAYMENT_PROCESSED"
}

/**
 * 결제 실패 이벤트
 */
data class PaymentFailedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val timestamp: LocalDateTime = LocalDateTime.now(),
    override val version: Int,
    override val correlationId: String? = null,
    override val causationId: String? = null,
    override val metadata: Map<String, Any> = emptyMap(),
    
    override val reservationId: Long,
    override val guestId: Long,
    override val roomId: Long,
    val paymentId: String,
    val amount: BigDecimal,
    val paymentMethod: String,
    val failureReason: String,
    val errorCode: String?,
    val retryAttempt: Int = 0
) : ReservationEvent {
    override val eventType: String = "PAYMENT_FAILED"
    
    /**
     * 재시도 가능 여부 확인
     */
    fun canRetry(): Boolean = retryAttempt < 3 && 
        !listOf("CARD_DECLINED", "INSUFFICIENT_FUNDS", "INVALID_CARD").contains(errorCode)
}

/**
 * 이벤트 유틸리티 함수들
 * Kotlin의 확장 함수 활용
 */

/**
 * 이벤트 심각도 판단
 */
fun ReservationEvent.getSeverity(): EventSeverity = when (this) {
    is PaymentFailedEvent -> EventSeverity.HIGH
    is ReservationCancelledEvent -> EventSeverity.MEDIUM
    is ReservationCreatedEvent, is ReservationConfirmedEvent -> EventSeverity.LOW
    else -> EventSeverity.LOW
}

/**
 * 이벤트가 알림 발송 대상인지 확인
 */
fun ReservationEvent.requiresNotification(): Boolean = when (this) {
    is ReservationCreatedEvent, 
    is ReservationConfirmedEvent,
    is ReservationCancelledEvent,
    is CheckInCompletedEvent,
    is CheckOutCompletedEvent -> true
    else -> false
}

/**
 * 이벤트 메타데이터에 추가 정보 포함
 */
fun ReservationEvent.withMetadata(additionalMetadata: Map<String, Any>): ReservationEvent {
    val updatedMetadata = this.metadata + additionalMetadata
    
    return when (this) {
        is ReservationCreatedEvent -> copy(metadata = updatedMetadata)
        is ReservationUpdatedEvent -> copy(metadata = updatedMetadata)
        is ReservationCancelledEvent -> copy(metadata = updatedMetadata)
        is ReservationConfirmedEvent -> copy(metadata = updatedMetadata)
        is CheckInCompletedEvent -> copy(metadata = updatedMetadata)
        is CheckOutCompletedEvent -> copy(metadata = updatedMetadata)
        is PaymentProcessedEvent -> copy(metadata = updatedMetadata)
        is PaymentFailedEvent -> copy(metadata = updatedMetadata)
    }
}

/**
 * 이벤트 심각도 열거형
 */
enum class EventSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}