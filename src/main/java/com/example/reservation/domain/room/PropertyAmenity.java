package com.example.reservation.domain.room;

public enum PropertyAmenity {
    WIFI("무선 인터넷"),
    PARKING("주차장"),
    POOL("수영장"),
    GYM("피트니스센터"),
    SPA("스파"),
    RESTAURANT("레스토랑"),
    BAR("바"),
    ROOM_SERVICE("룸서비스"),
    CONCIERGE("컨시어지"),
    BUSINESS_CENTER("비즈니스센터"),
    MEETING_ROOM("회의실"),
    AIRPORT_SHUTTLE("공항셔틀"),
    LAUNDRY("세탁서비스"),
    DRY_CLEANING("드라이클리닝"),
    SAFE_DEPOSIT_BOX("금고"),
    CURRENCY_EXCHANGE("환전"),
    ELEVATOR("엘리베이터"),
    WHEELCHAIR_ACCESS("휠체어 접근"),
    PET_FRIENDLY("반려동물 동반"),
    NON_SMOKING("금연"),
    AIR_CONDITIONING("에어컨"),
    HEATING("난방"),
    BALCONY("발코니"),
    GARDEN("정원"),
    BEACH_ACCESS("해변 접근");
    
    private final String displayName;
    
    PropertyAmenity(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}