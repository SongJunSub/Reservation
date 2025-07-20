package com.example.reservation.domain.guest;

public enum Gender {
    MALE("남성"),
    FEMALE("여성"), 
    OTHER("기타"),
    NOT_SPECIFIED("미지정");
    
    private final String displayName;
    
    Gender(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}