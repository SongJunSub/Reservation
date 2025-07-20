package com.example.reservation.domain.guest;

public enum GuestStatus {
    ACTIVE("활성"),
    INACTIVE("비활성"),
    SUSPENDED("정지"),
    BLACKLISTED("블랙리스트");
    
    private final String displayName;
    
    GuestStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean canMakeReservation() {
        return this == ACTIVE;
    }
    
    public boolean canLogin() {
        return this == ACTIVE || this == INACTIVE;
    }
}