package com.example.reservation.domain.room;

public enum PropertyCategory {
    LUXURY("럭셔리"),
    PREMIUM("프리미엄"),
    STANDARD("스탠다드"),
    BUDGET("이코노미"),
    BUSINESS("비즈니스"),
    BOUTIQUE("부티크"),
    FAMILY("패밀리"),
    ROMANTIC("로맨틱");
    
    private final String displayName;
    
    PropertyCategory(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}