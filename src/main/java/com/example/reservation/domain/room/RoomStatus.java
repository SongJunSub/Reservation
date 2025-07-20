package com.example.reservation.domain.room;

public enum RoomStatus {
    AVAILABLE("예약 가능"),
    OCCUPIED("투숙중"),
    OUT_OF_ORDER("고장"),
    MAINTENANCE("정비중"),
    CLEANING("청소중"),
    RESERVED("예약됨");
    
    private final String displayName;
    
    RoomStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}