package com.example.reservation.domain.payment_java;

public enum PaymentMethod {
    CREDIT_CARD,        // 신용카드
    DEBIT_CARD,         // 체크카드
    BANK_TRANSFER,      // 계좌이체
    DIGITAL_WALLET,     // 디지털 지갑 (PayPal, Apple Pay 등)
    CASH,              // 현금
    POINTS,            // 포인트
    GIFT_CARD,         // 상품권
    CRYPTOCURRENCY     // 암호화폐
}