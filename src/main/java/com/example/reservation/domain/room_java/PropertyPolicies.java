package com.example.reservation.domain.room_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class PropertyPolicies {
    
    @Column(nullable = false)
    private String cancellationPolicy = "24시간 전 무료 취소";
    
    @Column(nullable = false)
    private String petPolicy = "반려동물 동반 불가";
    
    @Column(nullable = false)
    private String smokingPolicy = "전 객실 금연";
    
    @Column(nullable = false)
    private String childPolicy = "만 18세 미만 투숙 불가";
    
    @Column(nullable = false)
    private String extraBedPolicy = "추가 침대 요청 가능";
    
    @Column(nullable = false)
    private Integer ageRestriction = 18;

    public PropertyPolicies() {}

    public PropertyPolicies(String cancellationPolicy, String petPolicy, String smokingPolicy,
                           String childPolicy, String extraBedPolicy, Integer ageRestriction) {
        this.cancellationPolicy = cancellationPolicy != null ? cancellationPolicy : "24시간 전 무료 취소";
        this.petPolicy = petPolicy != null ? petPolicy : "반려동물 동반 불가";
        this.smokingPolicy = smokingPolicy != null ? smokingPolicy : "전 객실 금연";
        this.childPolicy = childPolicy != null ? childPolicy : "만 18세 미만 투숙 불가";
        this.extraBedPolicy = extraBedPolicy != null ? extraBedPolicy : "추가 침대 요청 가능";
        this.ageRestriction = ageRestriction != null ? ageRestriction : 18;
    }

    // Getters
    public String getCancellationPolicy() { return cancellationPolicy; }
    public String getPetPolicy() { return petPolicy; }
    public String getSmokingPolicy() { return smokingPolicy; }
    public String getChildPolicy() { return childPolicy; }
    public String getExtraBedPolicy() { return extraBedPolicy; }
    public Integer getAgeRestriction() { return ageRestriction; }

    // Setters
    public void setCancellationPolicy(String cancellationPolicy) { this.cancellationPolicy = cancellationPolicy; }
    public void setPetPolicy(String petPolicy) { this.petPolicy = petPolicy; }
    public void setSmokingPolicy(String smokingPolicy) { this.smokingPolicy = smokingPolicy; }
    public void setChildPolicy(String childPolicy) { this.childPolicy = childPolicy; }
    public void setExtraBedPolicy(String extraBedPolicy) { this.extraBedPolicy = extraBedPolicy; }
    public void setAgeRestriction(Integer ageRestriction) { this.ageRestriction = ageRestriction; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PropertyPolicies that = (PropertyPolicies) obj;
        return Objects.equals(cancellationPolicy, that.cancellationPolicy) &&
               Objects.equals(petPolicy, that.petPolicy) &&
               Objects.equals(smokingPolicy, that.smokingPolicy) &&
               Objects.equals(childPolicy, that.childPolicy) &&
               Objects.equals(extraBedPolicy, that.extraBedPolicy) &&
               Objects.equals(ageRestriction, that.ageRestriction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cancellationPolicy, petPolicy, smokingPolicy, 
                          childPolicy, extraBedPolicy, ageRestriction);
    }
}