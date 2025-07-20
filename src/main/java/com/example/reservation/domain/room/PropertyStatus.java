package com.example.reservation.domain.room;

public enum PropertyStatus {
    ACTIVE("운영중"),
    INACTIVE("비활성"),
    MAINTENANCE("정비중"),
    CLOSED_TEMPORARILY("임시 휴업"),
    CLOSED_PERMANENTLY("영구 휴업");
    
    private final String displayName;
    
    PropertyStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}