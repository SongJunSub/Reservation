package com.example.reservation.domain.reservation_java;

public enum ReservationStatus {
    PENDING,     // 대기중
    CONFIRMED,   // 확정
    CHECKED_IN,  // 체크인
    CHECKED_OUT, // 체크아웃
    CANCELLED,   // 취소
    NO_SHOW,     // 노쇼
    COMPLETED    // 완료
}