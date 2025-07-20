package com.example.reservation.domain.payment;

import com.example.reservation.domain.reservation.Reservation;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"reservation"})
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;
    
    @Column(unique = true, nullable = false, length = 50)
    private String transactionId;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3, nullable = false)
    @Builder.Default
    private String currency = "KRW";
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Embedded
    @Builder.Default
    private PaymentDetails paymentDetails = new PaymentDetails();
    
    @Column(length = 50)
    private String gatewayTransactionId;
    
    @Column(length = 50)
    private String gatewayProvider;
    
    @Column(length = 500)
    private String gatewayResponse;
    
    @Column(length = 1000)
    private String failureReason;
    
    @Column
    private LocalDateTime processedAt;
    
    @Column
    private LocalDateTime refundedAt;
    
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    // 비즈니스 메서드
    public boolean isSuccessful() {
        return status == PaymentStatus.COMPLETED;
    }
    
    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }
    
    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }
    
    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }
    
    public boolean canBeRefunded() {
        return status == PaymentStatus.COMPLETED;
    }
    
    public BigDecimal getRefundableAmount() {
        return amount.subtract(refundedAmount);
    }
    
    public boolean isPartiallyRefunded() {
        return refundedAmount.compareTo(BigDecimal.ZERO) > 0 && 
               refundedAmount.compareTo(amount) < 0;
    }
    
    public boolean isFullyRefunded() {
        return refundedAmount.compareTo(amount) >= 0;
    }
    
    public void markAsCompleted(String gatewayTransactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
        this.gatewayTransactionId = gatewayTransactionId;
    }
    
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.processedAt = LocalDateTime.now();
    }
    
    public void processRefund(BigDecimal refundAmount) {
        this.refundedAmount = this.refundedAmount.add(refundAmount);
        
        if (this.refundedAmount.compareTo(amount) >= 0) {
            this.status = PaymentStatus.REFUNDED;
        } else {
            this.status = PaymentStatus.PARTIALLY_REFUNDED;
        }
        
        this.refundedAt = LocalDateTime.now();
    }
    
    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }
    
    public void markAsProcessing() {
        this.status = PaymentStatus.PROCESSING;
    }
    
    public void dispute() {
        this.status = PaymentStatus.DISPUTED;
    }
    
    public boolean isCreditCard() {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }
    
    public boolean isDigitalPayment() {
        return method == PaymentMethod.DIGITAL_WALLET || method == PaymentMethod.CRYPTOCURRENCY;
    }
    
    public String getPaymentMethodDisplay() {
        if (isCreditCard() && paymentDetails.getCardLastFourDigits() != null) {
            return paymentDetails.getCardBrand() + " ****" + paymentDetails.getCardLastFourDigits();
        }
        return method.name();
    }
}