package com.example.reservation.domain.guest;

public enum LoyaltyTier {
    STANDARD, SILVER, GOLD, PLATINUM, DIAMOND;
    
    public static LoyaltyTier calculateTier(int points) {
        if (points >= 50000) return DIAMOND;
        if (points >= 25000) return PLATINUM;
        if (points >= 10000) return GOLD;
        if (points >= 5000) return SILVER;
        return STANDARD;
    }
    
    public double getDiscountPercentage() {
        return switch (this) {
            case STANDARD -> 0.0;
            case SILVER -> 5.0;
            case GOLD -> 10.0;
            case PLATINUM -> 15.0;
            case DIAMOND -> 20.0;
        };
    }
    
    public int getPointsRequiredForNext() {
        return switch (this) {
            case STANDARD -> 5000;
            case SILVER -> 10000;
            case GOLD -> 25000;
            case PLATINUM -> 50000;
            case DIAMOND -> 0; // 최고 등급
        };
    }
}