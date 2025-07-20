package com.example.reservation.domain.room;

public enum BedType {
    SINGLE("싱글베드"),
    TWIN("트윈베드"),
    DOUBLE("더블베드"),
    QUEEN("퀸베드"),
    KING("킹베드"),
    SOFA_BED("소파베드"),
    BUNK_BED("이층베드");
    
    private final String displayName;
    
    BedType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}