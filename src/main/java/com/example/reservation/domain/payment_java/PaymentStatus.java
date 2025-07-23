package com.example.reservation.domain.payment_java;

public enum PaymentStatus {
    PENDING,            // 대기중
    PROCESSING,         // 처리중
    COMPLETED,          // 완료
    FAILED,            // 실패
    CANCELLED,         // 취소
    REFUNDED,          // 환불 완료
    PARTIALLY_REFUNDED, // 부분 환불
    DISPUTED           // 분쟁
}