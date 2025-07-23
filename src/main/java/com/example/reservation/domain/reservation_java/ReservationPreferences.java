package com.example.reservation.domain.reservation_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class ReservationPreferences {
    
    @Column(nullable = false)
    private String bedTypePreference = "";
    
    @Column(nullable = false)
    private String floorPreference = "";
    
    @Column(nullable = false)
    private Boolean smokingPreference = false;
    
    @Column(nullable = false)
    private Boolean quietRoomPreference = false;
    
    @Column(nullable = false)
    private Boolean accessibilityNeeds = false;
    
    @Column(nullable = false)
    private Boolean earlyCheckInRequested = false;
    
    @Column(nullable = false)
    private Boolean lateCheckOutRequested = false;

    // 기본 생성자
    public ReservationPreferences() {}

    // 전체 생성자
    public ReservationPreferences(String bedTypePreference, String floorPreference,
                                 Boolean smokingPreference, Boolean quietRoomPreference,
                                 Boolean accessibilityNeeds, Boolean earlyCheckInRequested,
                                 Boolean lateCheckOutRequested) {
        this.bedTypePreference = bedTypePreference != null ? bedTypePreference : "";
        this.floorPreference = floorPreference != null ? floorPreference : "";
        this.smokingPreference = smokingPreference != null ? smokingPreference : false;
        this.quietRoomPreference = quietRoomPreference != null ? quietRoomPreference : false;
        this.accessibilityNeeds = accessibilityNeeds != null ? accessibilityNeeds : false;
        this.earlyCheckInRequested = earlyCheckInRequested != null ? earlyCheckInRequested : false;
        this.lateCheckOutRequested = lateCheckOutRequested != null ? lateCheckOutRequested : false;
    }

    // Getters
    public String getBedTypePreference() { return bedTypePreference; }
    public String getFloorPreference() { return floorPreference; }
    public Boolean getSmokingPreference() { return smokingPreference; }
    public Boolean getQuietRoomPreference() { return quietRoomPreference; }
    public Boolean getAccessibilityNeeds() { return accessibilityNeeds; }
    public Boolean getEarlyCheckInRequested() { return earlyCheckInRequested; }
    public Boolean getLateCheckOutRequested() { return lateCheckOutRequested; }

    // Setters
    public void setBedTypePreference(String bedTypePreference) { 
        this.bedTypePreference = bedTypePreference; 
    }
    public void setFloorPreference(String floorPreference) { 
        this.floorPreference = floorPreference; 
    }
    public void setSmokingPreference(Boolean smokingPreference) { 
        this.smokingPreference = smokingPreference; 
    }
    public void setQuietRoomPreference(Boolean quietRoomPreference) { 
        this.quietRoomPreference = quietRoomPreference; 
    }
    public void setAccessibilityNeeds(Boolean accessibilityNeeds) { 
        this.accessibilityNeeds = accessibilityNeeds; 
    }
    public void setEarlyCheckInRequested(Boolean earlyCheckInRequested) { 
        this.earlyCheckInRequested = earlyCheckInRequested; 
    }
    public void setLateCheckOutRequested(Boolean lateCheckOutRequested) { 
        this.lateCheckOutRequested = lateCheckOutRequested; 
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ReservationPreferences that = (ReservationPreferences) obj;
        return Objects.equals(bedTypePreference, that.bedTypePreference) &&
               Objects.equals(floorPreference, that.floorPreference) &&
               Objects.equals(smokingPreference, that.smokingPreference) &&
               Objects.equals(quietRoomPreference, that.quietRoomPreference) &&
               Objects.equals(accessibilityNeeds, that.accessibilityNeeds) &&
               Objects.equals(earlyCheckInRequested, that.earlyCheckInRequested) &&
               Objects.equals(lateCheckOutRequested, that.lateCheckOutRequested);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bedTypePreference, floorPreference, smokingPreference,
                          quietRoomPreference, accessibilityNeeds, earlyCheckInRequested,
                          lateCheckOutRequested);
    }

    @Override
    public String toString() {
        return "ReservationPreferences{" +
                "bedTypePreference='" + bedTypePreference + '\'' +
                ", floorPreference='" + floorPreference + '\'' +
                ", smokingPreference=" + smokingPreference +
                ", quietRoomPreference=" + quietRoomPreference +
                ", accessibilityNeeds=" + accessibilityNeeds +
                ", earlyCheckInRequested=" + earlyCheckInRequested +
                ", lateCheckOutRequested=" + lateCheckOutRequested +
                '}';
    }
}