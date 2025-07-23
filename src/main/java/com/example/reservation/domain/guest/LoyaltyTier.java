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
        switch (this) {
            case STANDARD: return 0.0;
            case SILVER: return 5.0;
            case GOLD: return 10.0;
            case PLATINUM: return 15.0;
            case DIAMOND: return 20.0;
            default: return 0.0;
        }
    }
}