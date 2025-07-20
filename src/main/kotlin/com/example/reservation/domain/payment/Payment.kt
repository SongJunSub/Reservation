package com.example.reservation.domain.payment

import com.example.reservation.domain.reservation.Reservation
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener::class)
data class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    val reservation: Reservation,
    
    @Column(unique = true, nullable = false, length = 50)
    val transactionId: String,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,
    
    @Column(length = 3, nullable = false)
    val currency: String = "KRW",
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: PaymentType,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val method: PaymentMethod,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus = PaymentStatus.PENDING,
    
    @Embedded
    val paymentDetails: PaymentDetails = PaymentDetails(),
    
    @Column(length = 50)
    val gatewayTransactionId: String? = null,
    
    @Column(length = 50)
    val gatewayProvider: String? = null,
    
    @Column(length = 500)
    val gatewayResponse: String? = null,
    
    @Column(length = 1000)
    val failureReason: String? = null,
    
    @Column
    val processedAt: LocalDateTime? = null,
    
    @Column
    val refundedAt: LocalDateTime? = null,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val refundedAmount: BigDecimal = BigDecimal.ZERO,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isSuccessful(): Boolean = status == PaymentStatus.COMPLETED
    
    fun isFailed(): Boolean = status == PaymentStatus.FAILED
    
    fun isPending(): Boolean = status == PaymentStatus.PENDING
    
    fun isRefunded(): Boolean = status == PaymentStatus.REFUNDED
    
    fun canBeRefunded(): Boolean = status == PaymentStatus.COMPLETED
    
    fun getRefundableAmount(): BigDecimal = amount.subtract(refundedAmount)
    
    fun isPartiallyRefunded(): Boolean = 
        refundedAmount > BigDecimal.ZERO && refundedAmount < amount
    
    fun isFullyRefunded(): Boolean = refundedAmount >= amount
    
    fun markAsCompleted(gatewayTransactionId: String? = null): Payment = copy(
        status = PaymentStatus.COMPLETED,
        processedAt = LocalDateTime.now(),
        gatewayTransactionId = gatewayTransactionId
    )
    
    fun markAsFailed(reason: String? = null): Payment = copy(
        status = PaymentStatus.FAILED,
        failureReason = reason,
        processedAt = LocalDateTime.now()
    )
    
    fun processRefund(refundAmount: BigDecimal): Payment {
        val newRefundedAmount = refundedAmount.add(refundAmount)
        val newStatus = if (newRefundedAmount >= amount) {
            PaymentStatus.REFUNDED
        } else {
            PaymentStatus.PARTIALLY_REFUNDED
        }
        
        return copy(
            status = newStatus,
            refundedAmount = newRefundedAmount,
            refundedAt = LocalDateTime.now()
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payment) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class PaymentDetails(
    @Column(length = 4)
    val cardLastFourDigits: String? = null,
    
    @Column(length = 50)
    val cardBrand: String? = null,
    
    @Column(length = 100)
    val cardHolderName: String? = null,
    
    @Column(length = 100)
    val bankName: String? = null,
    
    @Column(length = 50)
    val bankAccountNumber: String? = null,
    
    @Column(length = 100)
    val digitalWalletType: String? = null,
    
    @Column(length = 200)
    val billingAddress: String? = null,
    
    @Column(length = 100)
    val billingCity: String? = null,
    
    @Column(length = 20)
    val billingPostalCode: String? = null,
    
    @Column(length = 3)
    val billingCountryCode: String? = null
)

enum class PaymentType {
    BOOKING_DEPOSIT,    // 예약금
    FULL_PAYMENT,       // 전액 결제
    BALANCE_PAYMENT,    // 잔금 결제
    ADDITIONAL_CHARGES, // 추가 요금
    REFUND,            // 환불
    CANCELLATION_FEE   // 취소 수수료
}

enum class PaymentMethod {
    CREDIT_CARD,        // 신용카드
    DEBIT_CARD,         // 체크카드
    BANK_TRANSFER,      // 계좌이체
    DIGITAL_WALLET,     // 디지털 지갑 (PayPal, Apple Pay 등)
    CASH,              // 현금
    POINTS,            // 포인트
    GIFT_CARD,         // 상품권
    CRYPTOCURRENCY     // 암호화폐
}

enum class PaymentStatus {
    PENDING,            // 대기중
    PROCESSING,         // 처리중
    COMPLETED,          // 완료
    FAILED,            // 실패
    CANCELLED,         // 취소
    REFUNDED,          // 환불 완료
    PARTIALLY_REFUNDED, // 부분 환불
    DISPUTED           // 분쟁
}