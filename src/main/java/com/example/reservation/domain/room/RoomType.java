package com.example.reservation.domain.room;

public enum RoomType {
    STANDARD("스탠다드"),
    DELUXE("디럭스"),
    SUITE("스위트"),
    JUNIOR_SUITE("주니어 스위트"),
    PRESIDENTIAL_SUITE("프레지덴셜 스위트"),
    FAMILY("패밀리"),
    CONNECTING("커넥팅"),
    STUDIO("스튜디오"),
    PENTHOUSE("펜트하우스"),
    VILLA("빌라");
    
    private final String displayName;
    
    RoomType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}