package com.example.reservation.domain.availability_java;

public enum AvailabilityStatus {
    AVAILABLE,      // 예약 가능
    SOLD_OUT,       // 매진
    BLOCKED,        // 차단됨 (관리자에 의해)
    MAINTENANCE,    // 정비중
    OUT_OF_ORDER,   // 고장
    RESERVED        // 예약됨
}