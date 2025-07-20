package com.example.reservation.domain.reservation;

public enum ReservationStatus {
    PENDING("예약 대기"),
    CONFIRMED("예약 확정"),
    CHECKED_IN("체크인 완료"),
    CHECKED_OUT("체크아웃 완료"),
    CANCELLED("예약 취소"),
    NO_SHOW("노쇼"),
    COMPLETED("예약 완료");
    
    private final String displayName;
    
    ReservationStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}