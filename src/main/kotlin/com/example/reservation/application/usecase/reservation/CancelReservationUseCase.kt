package com.example.reservation.application.usecase.reservation

import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 취소 유스케이스
 * 실무 릴리즈 급 구현: 취소 정책 적용 및 환불 계산
 */
interface CancelReservationUseCase {
    
    /**
     * 예약을 취소합니다.
     * 
     * @param command 예약 취소 명령
     * @return 취소 처리 결과
     */
    fun execute(command: CancelReservationCommand): Mono<CancellationResponse>
    
    /**
     * 예약 취소시 환불 금액을 계산합니다.
     * 
     * @param reservationId 예약 ID
     * @param cancellationReason 취소 사유
     * @return 환불 계산 결과
     */
    fun calculateRefund(reservationId: UUID, cancellationReason: CancellationReason): Mono<RefundCalculationResult>
}

/**
 * 예약 취소 명령
 */
data class CancelReservationCommand(
    val reservationId: UUID,
    val cancellationReason: CancellationReason,
    val reasonDetails: String? = null,
    val requestedBy: UUID? = null, // 취소 요청자
    val adminOverride: Boolean = false, // 관리자 권한으로 정책 무시
    val notifyGuest: Boolean = true, // 고객에게 알림 발송 여부
    val processRefund: Boolean = true // 환불 처리 여부
)

/**
 * 취소 사유
 */
enum class CancellationReason {
    GUEST_REQUEST,          // 고객 요청
    PROPERTY_MAINTENANCE,   // 시설 정비
    OVERBOOKING,           // 초과예약
    WEATHER,               // 날씨
    EMERGENCY,             // 응급상황
    FORCE_MAJEURE,         // 천재지변
    PAYMENT_FAILURE,       // 결제 실패
    POLICY_VIOLATION,      // 정책 위반
    ADMIN_DECISION,        // 관리자 판단
    SYSTEM_ERROR           // 시스템 오류
}

/**
 * 취소 처리 응답
 */
data class CancellationResponse(
    val reservationId: UUID,
    val cancellationId: UUID,
    val status: String, // CANCELLED, PENDING_REFUND, REFUND_PROCESSED
    val cancellationDate: LocalDateTime,
    val refundAmount: BigDecimal,
    val cancellationFee: BigDecimal,
    val processingFee: BigDecimal,
    val netRefund: BigDecimal,
    val refundMethod: String? = null,
    val estimatedRefundDate: LocalDateTime? = null,
    val cancellationPolicy: String,
    val confirmationNumber: String? = null
)

/**
 * 환불 계산 결과
 */
data class RefundCalculationResult(
    val originalAmount: BigDecimal,
    val refundableAmount: BigDecimal,
    val cancellationFee: BigDecimal,
    val processingFee: BigDecimal,
    val taxRefund: BigDecimal,
    val netRefund: BigDecimal,
    val refundPercentage: Double,
    val cancellationPolicy: CancellationPolicyInfo,
    val breakdown: List<RefundBreakdownItem> = emptyList()
)

/**
 * 취소 정책 정보
 */
data class CancellationPolicyInfo(
    val policyName: String,
    val description: String,
    val deadlines: List<CancellationDeadline>,
    val nonRefundableAmount: BigDecimal = BigDecimal.ZERO,
    val minimumFee: BigDecimal = BigDecimal.ZERO
)

/**
 * 취소 마감일 정보
 */
data class CancellationDeadline(
    val hoursBeforeCheckIn: Int,
    val refundPercentage: Double,
    val description: String
)

/**
 * 환불 내역 항목
 */
data class RefundBreakdownItem(
    val category: String,
    val description: String,
    val amount: BigDecimal,
    val isRefundable: Boolean
)