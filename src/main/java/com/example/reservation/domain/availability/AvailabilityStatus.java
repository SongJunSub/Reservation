package com.example.reservation.domain.availability;

public enum AvailabilityStatus {
    AVAILABLE("예약 가능"),
    SOLD_OUT("매진"),
    BLOCKED("차단됨"),
    MAINTENANCE("정비중"),
    OUT_OF_ORDER("고장"),
    RESERVED("예약됨");
    
    private final String displayName;
    
    AvailabilityStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}