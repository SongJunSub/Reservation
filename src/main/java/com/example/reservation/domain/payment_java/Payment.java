package com.example.reservation.domain.payment_java;

import com.example.reservation.domain.reservation_java.Reservation;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "payments_java")
@EntityListeners(AuditingEntityListener.class)
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;
    
    @Column(unique = true, nullable = false, length = 50)
    private String transactionId;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3, nullable = false)
    private String currency = "KRW";
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Embedded
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
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();

    // 기본 생성자
    protected Payment() {}

    // 전체 생성자
    public Payment(Reservation reservation, String transactionId, BigDecimal amount,
                  String currency, PaymentType type, PaymentMethod method,
                  PaymentStatus status, PaymentDetails paymentDetails,
                  String gatewayTransactionId, String gatewayProvider,
                  String gatewayResponse, String failureReason,
                  LocalDateTime processedAt, LocalDateTime refundedAt,
                  BigDecimal refundedAmount) {
        this.reservation = reservation;
        this.transactionId = transactionId;
        this.amount = amount;
        this.currency = currency != null ? currency : "KRW";
        this.type = type;
        this.method = method;
        this.status = status != null ? status : PaymentStatus.PENDING;
        this.paymentDetails = paymentDetails != null ? paymentDetails : new PaymentDetails();
        this.gatewayTransactionId = gatewayTransactionId;
        this.gatewayProvider = gatewayProvider;
        this.gatewayResponse = gatewayResponse;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
        this.refundedAt = refundedAt;
        this.refundedAmount = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
    }

    // 비즈니스 메서드들
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

    public Payment markAsCompleted(String gatewayTransactionId) {
        Payment newPayment = this.copy();
        newPayment.status = PaymentStatus.COMPLETED;
        newPayment.processedAt = LocalDateTime.now();
        newPayment.gatewayTransactionId = gatewayTransactionId;
        return newPayment;
    }

    public Payment markAsFailed(String reason) {
        Payment newPayment = this.copy();
        newPayment.status = PaymentStatus.FAILED;
        newPayment.failureReason = reason;
        newPayment.processedAt = LocalDateTime.now();
        return newPayment;
    }

    public Payment processRefund(BigDecimal refundAmount) {
        Payment newPayment = this.copy();
        BigDecimal newRefundedAmount = this.refundedAmount.add(refundAmount);
        
        PaymentStatus newStatus;
        if (newRefundedAmount.compareTo(amount) >= 0) {
            newStatus = PaymentStatus.REFUNDED;
        } else {
            newStatus = PaymentStatus.PARTIALLY_REFUNDED;
        }
        
        newPayment.status = newStatus;
        newPayment.refundedAmount = newRefundedAmount;
        newPayment.refundedAt = LocalDateTime.now();
        
        return newPayment;
    }

    // Copy method for immutability pattern
    public Payment copy() {
        Payment copy = new Payment();
        copy.id = this.id;
        copy.reservation = this.reservation;
        copy.transactionId = this.transactionId;
        copy.amount = this.amount;
        copy.currency = this.currency;
        copy.type = this.type;
        copy.method = this.method;
        copy.status = this.status;
        copy.paymentDetails = this.paymentDetails; // PaymentDetails should also have copy method
        copy.gatewayTransactionId = this.gatewayTransactionId;
        copy.gatewayProvider = this.gatewayProvider;
        copy.gatewayResponse = this.gatewayResponse;
        copy.failureReason = this.failureReason;
        copy.processedAt = this.processedAt;
        copy.refundedAt = this.refundedAt;
        copy.refundedAmount = this.refundedAmount;
        copy.createdAt = this.createdAt;
        copy.lastModifiedAt = this.lastModifiedAt;
        return copy;
    }

    // Getters
    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public String getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentType getType() { return type; }
    public PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public PaymentDetails getPaymentDetails() { return paymentDetails; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public String getGatewayProvider() { return gatewayProvider; }
    public String getGatewayResponse() { return gatewayResponse; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setType(PaymentType type) { this.type = type; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setPaymentDetails(PaymentDetails paymentDetails) { this.paymentDetails = paymentDetails; }
    public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
    public void setGatewayProvider(String gatewayProvider) { this.gatewayProvider = gatewayProvider; }
    public void setGatewayResponse(String gatewayResponse) { this.gatewayResponse = gatewayResponse; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Payment payment = (Payment) obj;
        return id != null && id != 0L && Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "id=" + id +
                ", transactionId='" + transactionId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", type=" + type +
                ", method=" + method +
                ", status=" + status +
                '}';
    }
}