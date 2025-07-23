package com.example.reservation.domain.payment_java;

public enum PaymentType {
    BOOKING_DEPOSIT,    // 예약금
    FULL_PAYMENT,       // 전액 결제
    BALANCE_PAYMENT,    // 잔금 결제
    ADDITIONAL_CHARGES, // 추가 요금
    REFUND,            // 환불
    CANCELLATION_FEE   // 취소 수수료
}