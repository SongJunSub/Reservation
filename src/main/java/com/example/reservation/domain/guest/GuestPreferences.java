package com.example.reservation.domain.guest;

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
    
    public GuestPreferences() {}
    
    public GuestPreferences(String roomTypePreference, String floorPreference, 
                          String bedTypePreference, Boolean smokingPreference,
                          Boolean accessibilityNeeds, String dietaryRestrictions,
                          String specialRequests, Boolean marketingOptIn) {
        this.roomTypePreference = roomTypePreference;
        this.floorPreference = floorPreference;
        this.bedTypePreference = bedTypePreference;
        this.smokingPreference = smokingPreference;
        this.accessibilityNeeds = accessibilityNeeds;
        this.dietaryRestrictions = dietaryRestrictions;
        this.specialRequests = specialRequests;
        this.marketingOptIn = marketingOptIn;
    }
    
    // Getters and Setters
    public String getRoomTypePreference() { return roomTypePreference; }
    public void setRoomTypePreference(String roomTypePreference) { this.roomTypePreference = roomTypePreference; }
    
    public String getFloorPreference() { return floorPreference; }
    public void setFloorPreference(String floorPreference) { this.floorPreference = floorPreference; }
    
    public String getBedTypePreference() { return bedTypePreference; }
    public void setBedTypePreference(String bedTypePreference) { this.bedTypePreference = bedTypePreference; }
    
    public Boolean getSmokingPreference() { return smokingPreference; }
    public void setSmokingPreference(Boolean smokingPreference) { this.smokingPreference = smokingPreference; }
    
    public Boolean getAccessibilityNeeds() { return accessibilityNeeds; }
    public void setAccessibilityNeeds(Boolean accessibilityNeeds) { this.accessibilityNeeds = accessibilityNeeds; }
    
    public String getDietaryRestrictions() { return dietaryRestrictions; }
    public void setDietaryRestrictions(String dietaryRestrictions) { this.dietaryRestrictions = dietaryRestrictions; }
    
    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }
    
    public Boolean getMarketingOptIn() { return marketingOptIn; }
    public void setMarketingOptIn(Boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; }
    
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
}