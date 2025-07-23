package com.example.reservation.domain.guest_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class GuestPreferences {
    
    @Column(nullable = false)
    private String roomTypePreference = "";
    
    @Column(nullable = false)
    private String floorPreference = "";
    
    @Column(nullable = false)
    private String bedTypePreference = "";
    
    @Column(nullable = false)
    private Boolean smokingPreference = false;
    
    @Column(nullable = false)
    private Boolean accessibilityNeeds = false;
    
    @Column(nullable = false)
    private String dietaryRestrictions = "";
    
    @Column(nullable = false)
    private String specialRequests = "";
    
    @Column(nullable = false)
    private Boolean marketingOptIn = false;

    // 기본 생성자
    public GuestPreferences() {}

    // 전체 생성자
    public GuestPreferences(String roomTypePreference, String floorPreference, 
                           String bedTypePreference, Boolean smokingPreference,
                           Boolean accessibilityNeeds, String dietaryRestrictions,
                           String specialRequests, Boolean marketingOptIn) {
        this.roomTypePreference = roomTypePreference != null ? roomTypePreference : "";
        this.floorPreference = floorPreference != null ? floorPreference : "";
        this.bedTypePreference = bedTypePreference != null ? bedTypePreference : "";
        this.smokingPreference = smokingPreference != null ? smokingPreference : false;
        this.accessibilityNeeds = accessibilityNeeds != null ? accessibilityNeeds : false;
        this.dietaryRestrictions = dietaryRestrictions != null ? dietaryRestrictions : "";
        this.specialRequests = specialRequests != null ? specialRequests : "";
        this.marketingOptIn = marketingOptIn != null ? marketingOptIn : false;
    }

    // Getters
    public String getRoomTypePreference() { return roomTypePreference; }
    public String getFloorPreference() { return floorPreference; }
    public String getBedTypePreference() { return bedTypePreference; }
    public Boolean getSmokingPreference() { return smokingPreference; }
    public Boolean getAccessibilityNeeds() { return accessibilityNeeds; }
    public String getDietaryRestrictions() { return dietaryRestrictions; }
    public String getSpecialRequests() { return specialRequests; }
    public Boolean getMarketingOptIn() { return marketingOptIn; }

    // Setters
    public void setRoomTypePreference(String roomTypePreference) { 
        this.roomTypePreference = roomTypePreference; 
    }
    public void setFloorPreference(String floorPreference) { 
        this.floorPreference = floorPreference; 
    }
    public void setBedTypePreference(String bedTypePreference) { 
        this.bedTypePreference = bedTypePreference; 
    }
    public void setSmokingPreference(Boolean smokingPreference) { 
        this.smokingPreference = smokingPreference; 
    }
    public void setAccessibilityNeeds(Boolean accessibilityNeeds) { 
        this.accessibilityNeeds = accessibilityNeeds; 
    }
    public void setDietaryRestrictions(String dietaryRestrictions) { 
        this.dietaryRestrictions = dietaryRestrictions; 
    }
    public void setSpecialRequests(String specialRequests) { 
        this.specialRequests = specialRequests; 
    }
    public void setMarketingOptIn(Boolean marketingOptIn) { 
        this.marketingOptIn = marketingOptIn; 
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GuestPreferences that = (GuestPreferences) obj;
        return Objects.equals(roomTypePreference, that.roomTypePreference) &&
               Objects.equals(floorPreference, that.floorPreference) &&
               Objects.equals(bedTypePreference, that.bedTypePreference) &&
               Objects.equals(smokingPreference, that.smokingPreference) &&
               Objects.equals(accessibilityNeeds, that.accessibilityNeeds) &&
               Objects.equals(dietaryRestrictions, that.dietaryRestrictions) &&
               Objects.equals(specialRequests, that.specialRequests) &&
               Objects.equals(marketingOptIn, that.marketingOptIn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomTypePreference, floorPreference, bedTypePreference, 
                          smokingPreference, accessibilityNeeds, dietaryRestrictions,
                          specialRequests, marketingOptIn);
    }

    @Override
    public String toString() {
        return "GuestPreferences{" +
                "roomTypePreference='" + roomTypePreference + '\'' +
                ", floorPreference='" + floorPreference + '\'' +
                ", bedTypePreference='" + bedTypePreference + '\'' +
                ", smokingPreference=" + smokingPreference +
                ", accessibilityNeeds=" + accessibilityNeeds +
                ", dietaryRestrictions='" + dietaryRestrictions + '\'' +
                ", specialRequests='" + specialRequests + '\'' +
                ", marketingOptIn=" + marketingOptIn +
                '}';
    }
}