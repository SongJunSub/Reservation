package com.example.reservation.domain.room_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;

@Embeddable
public class CheckInOutInfo {
    
    @Column(nullable = false)
    private LocalTime checkInTime = LocalTime.of(15, 0);
    
    @Column(nullable = false)
    private LocalTime checkOutTime = LocalTime.of(11, 0);
    
    @Column(nullable = false)
    private Boolean lateCheckInAllowed = true;
    
    @Column(nullable = false)
    private Boolean earlyCheckInAllowed = true;
    
    @Column(nullable = false)
    private Boolean lateCheckOutAllowed = true;

    public CheckInOutInfo() {}

    public CheckInOutInfo(LocalTime checkInTime, LocalTime checkOutTime, 
                         Boolean lateCheckInAllowed, Boolean earlyCheckInAllowed, 
                         Boolean lateCheckOutAllowed) {
        this.checkInTime = checkInTime != null ? checkInTime : LocalTime.of(15, 0);
        this.checkOutTime = checkOutTime != null ? checkOutTime : LocalTime.of(11, 0);
        this.lateCheckInAllowed = lateCheckInAllowed != null ? lateCheckInAllowed : true;
        this.earlyCheckInAllowed = earlyCheckInAllowed != null ? earlyCheckInAllowed : true;
        this.lateCheckOutAllowed = lateCheckOutAllowed != null ? lateCheckOutAllowed : true;
    }

    // Getters
    public LocalTime getCheckInTime() { return checkInTime; }
    public LocalTime getCheckOutTime() { return checkOutTime; }
    public Boolean getLateCheckInAllowed() { return lateCheckInAllowed; }
    public Boolean getEarlyCheckInAllowed() { return earlyCheckInAllowed; }
    public Boolean getLateCheckOutAllowed() { return lateCheckOutAllowed; }

    // Setters
    public void setCheckInTime(LocalTime checkInTime) { this.checkInTime = checkInTime; }
    public void setCheckOutTime(LocalTime checkOutTime) { this.checkOutTime = checkOutTime; }
    public void setLateCheckInAllowed(Boolean lateCheckInAllowed) { this.lateCheckInAllowed = lateCheckInAllowed; }
    public void setEarlyCheckInAllowed(Boolean earlyCheckInAllowed) { this.earlyCheckInAllowed = earlyCheckInAllowed; }
    public void setLateCheckOutAllowed(Boolean lateCheckOutAllowed) { this.lateCheckOutAllowed = lateCheckOutAllowed; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CheckInOutInfo that = (CheckInOutInfo) obj;
        return Objects.equals(checkInTime, that.checkInTime) &&
               Objects.equals(checkOutTime, that.checkOutTime) &&
               Objects.equals(lateCheckInAllowed, that.lateCheckInAllowed) &&
               Objects.equals(earlyCheckInAllowed, that.earlyCheckInAllowed) &&
               Objects.equals(lateCheckOutAllowed, that.lateCheckOutAllowed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkInTime, checkOutTime, lateCheckInAllowed, 
                          earlyCheckInAllowed, lateCheckOutAllowed);
    }
}