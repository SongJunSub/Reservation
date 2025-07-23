package com.example.reservation.domain.reservation_java;

public enum PaymentStatus {
    PENDING,        // 결제 대기
    PARTIALLY_PAID, // 부분 결제
    PAID,           // 결제 완료
    REFUNDED,       // 환불 완료
    FAILED          // 결제 실패
}