package com.example.reservation.domain.room_java;

public enum RoomStatus {
    AVAILABLE,    // 이용 가능
    OCCUPIED,     // 투숙중
    OUT_OF_ORDER, // 고장/수리중
    MAINTENANCE,  // 정기 점검
    CLEANING,     // 청소중
    RESERVED      // 예약됨
}