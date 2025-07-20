package com.example.reservation.domain.room;

public enum PropertyType {
    HOTEL("호텔"),
    RESORT("리조트"),
    MOTEL("모텔"),
    PENSION("펜션"),
    GUESTHOUSE("게스트하우스"),
    HOSTEL("호스텔"),
    APARTMENT("아파트"),
    VILLA("빌라"),
    CAMPING("캠핑장");
    
    private final String displayName;
    
    PropertyType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}