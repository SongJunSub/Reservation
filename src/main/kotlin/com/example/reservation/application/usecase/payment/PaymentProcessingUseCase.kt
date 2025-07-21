package com.example.reservation.application.usecase.payment

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

/**
 * 결제 처리 유스케이스
 * 실무 릴리즈 급 구현: PCI DSS 준수, 보안, 멱등성 보장
 */
interface PaymentProcessingUseCase {
    
    /**
     * 결제를 처리합니다.
     * 
     * @param command 결제 처리 명령
     * @return 결제 처리 결과
     */
    fun processPayment(command: ProcessPaymentCommand): Mono<PaymentResponse>
    
    /**
     * 결제를 승인합니다 (사전승인 후 확정).
     * 
     * @param paymentId 결제 ID
     * @param amount 최종 승인 금액
     * @return 승인 결과
     */
    fun capturePayment(paymentId: UUID, amount: BigDecimal? = null): Mono<PaymentResponse>
    
    /**
     * 결제를 환불합니다.
     * 
     * @param command 환불 처리 명령
     * @return 환불 처리 결과
     */
    fun refundPayment(command: RefundPaymentCommand): Mono<RefundResponse>
    
    /**
     * 결제를 취소합니다 (처리 전 취소).
     * 
     * @param paymentId 결제 ID
     * @param reason 취소 사유
     * @return 취소 결과
     */
    fun cancelPayment(paymentId: UUID, reason: String): Mono<PaymentResponse>
    
    /**
     * 결제 상태를 조회합니다.
     * 
     * @param paymentId 결제 ID
     * @return 결제 상태 정보
     */
    fun getPaymentStatus(paymentId: UUID): Mono<PaymentStatusResponse>
    
    /**
     * 결제 내역을 조회합니다.
     * 
     * @param criteria 조회 조건
     * @return 결제 내역 목록
     */
    fun getPaymentHistory(criteria: PaymentSearchCriteria): Flux<PaymentHistoryItem>
}

/**
 * 결제 처리 명령
 */
data class ProcessPaymentCommand(
    val reservationId: UUID,
    val paymentType: PaymentType,
    val amount: BigDecimal,
    val currency: String = "KRW",
    val paymentMethod: PaymentMethodInfo,
    val billingAddress: BillingAddressInfo? = null,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val idempotencyKey: String, // 멱등성 보장을 위한 키
    val savePaymentMethod: Boolean = false,
    val merchantData: MerchantData? = null
)

/**
 * 결제 유형
 */
enum class PaymentType {
    FULL_PAYMENT,       // 전액 결제
    DEPOSIT,           // 예치금
    PARTIAL_PAYMENT,   // 부분 결제
    BALANCE_PAYMENT,   // 잔액 결제
    SECURITY_DEPOSIT,  // 보증금
    INCIDENTAL_CHARGES // 부대비용
}

/**
 * 결제수단 정보
 */
data class PaymentMethodInfo(
    val type: String, // CREDIT_CARD, DEBIT_CARD, BANK_TRANSFER, DIGITAL_WALLET, etc.
    val provider: String? = null, // VISA, MASTERCARD, PAYPAL, KAKAOPAY, etc.
    val token: String? = null, // 토큰화된 결제정보
    val maskedNumber: String? = null, // 마스킹된 카드번호 (표시용)
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
    val holderName: String? = null,
    val securityCode: String? = null, // 처리 후 즉시 폐기
    val installments: Int? = null // 할부 개월수
)

/**
 * 청구지 주소 정보
 */
data class BillingAddressInfo(
    val firstName: String,
    val lastName: String,
    val street: String,
    val city: String,
    val state: String? = null,
    val postalCode: String,
    val country: String,
    val phoneNumber: String? = null
)

/**
 * 가맹점 데이터
 */
data class MerchantData(
    val merchantId: String,
    val terminalId: String? = null,
    val storeId: String? = null,
    val cashierId: String? = null,
    val deviceInfo: String? = null
)

/**
 * 환불 처리 명령
 */
data class RefundPaymentCommand(
    val paymentId: UUID,
    val refundAmount: BigDecimal,
    val reason: RefundReason,
    val reasonDetails: String? = null,
    val notifyCustomer: Boolean = true,
    val idempotencyKey: String
)

/**
 * 환불 사유
 */
enum class RefundReason {
    CANCELLATION,      // 예약 취소
    OVERBOOKING,      // 초과예약
    SERVICE_FAILURE,   // 서비스 실패
    CUSTOMER_REQUEST,  // 고객 요청
    BILLING_ERROR,     // 청구 오류
    DUPLICATE_CHARGE,  // 중복 결제
    FRAUD_PREVENTION,  // 사기 방지
    MERCHANT_ERROR,    // 가맹점 오류
    SYSTEM_ERROR,      // 시스템 오류
    DISPUTE_RESOLUTION // 분쟁 해결
}

/**
 * 결제 검색 조건
 */
data class PaymentSearchCriteria(
    val reservationId: UUID? = null,
    val guestId: UUID? = null,
    val status: Set<String> = emptySet(),
    val paymentType: PaymentType? = null,
    val amountFrom: BigDecimal? = null,
    val amountTo: BigDecimal? = null,
    val processedDateFrom: LocalDateTime? = null,
    val processedDateTo: LocalDateTime? = null,
    val paymentMethod: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "processedAt",
    val sortDirection: SortDirection = SortDirection.DESC
)

/**
 * 정렬 방향
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * 결제 응답
 */
data class PaymentResponse(
    val paymentId: UUID,
    val reservationId: UUID,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
    val amount: BigDecimal,
    val currency: String,
    val paymentType: PaymentType,
    val processedAt: LocalDateTime? = null,
    val transactionId: String? = null,
    val authorizationCode: String? = null,
    val gatewayResponse: GatewayResponse? = null,
    val receiptUrl: String? = null,
    val estimatedSettlementDate: LocalDateTime? = null
)

/**
 * 게이트웨이 응답
 */
data class GatewayResponse(
    val gatewayId: String,
    val responseCode: String,
    val responseMessage: String,
    val approvalNumber: String? = null,
    val processorTransactionId: String? = null,
    val networkTransactionId: String? = null,
    val riskScore: Double? = null,
    val avsResult: String? = null, // Address Verification Service
    val cvvResult: String? = null  // Card Verification Value
)

/**
 * 환불 응답
 */
data class RefundResponse(
    val refundId: UUID,
    val paymentId: UUID,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    val refundAmount: BigDecimal,
    val currency: String,
    val reason: RefundReason,
    val processedAt: LocalDateTime? = null,
    val transactionId: String? = null,
    val gatewayResponse: GatewayResponse? = null,
    val estimatedRefundDate: LocalDateTime? = null,
    val refundMethod: String? = null
)

/**
 * 결제 상태 응답
 */
data class PaymentStatusResponse(
    val paymentId: UUID,
    val status: String,
    val amount: BigDecimal,
    val currency: String,
    val lastUpdated: LocalDateTime,
    val statusHistory: List<PaymentStatusHistory> = emptyList(),
    val canCancel: Boolean = false,
    val canRefund: Boolean = false,
    val refundableAmount: BigDecimal = BigDecimal.ZERO,
    val settlementInfo: SettlementInfo? = null
)

/**
 * 결제 상태 이력
 */
data class PaymentStatusHistory(
    val status: String,
    val timestamp: LocalDateTime,
    val reason: String? = null,
    val updatedBy: String? = null
)

/**
 * 정산 정보
 */
data class SettlementInfo(
    val settlementDate: LocalDateTime? = null,
    val settlementAmount: BigDecimal,
    val fees: BigDecimal,
    val netAmount: BigDecimal,
    val settlementStatus: String
)

/**
 * 결제 내역 항목
 */
data class PaymentHistoryItem(
    val paymentId: UUID,
    val reservationId: UUID,
    val guestName: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val paymentType: PaymentType,
    val paymentMethod: String,
    val processedAt: LocalDateTime,
    val transactionId: String? = null
)