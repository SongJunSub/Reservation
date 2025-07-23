package com.example.reservation.domain.reservation_java;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Embeddable
public class ReservationGuestDetails {
    
    @Column(nullable = false, length = 100)
    private String primaryGuestFirstName;
    
    @Column(nullable = false, length = 100)
    private String primaryGuestLastName;
    
    @Column(nullable = false, length = 320)
    private String primaryGuestEmail;
    
    @Column(length = 20)
    private String primaryGuestPhone;
    
    @Column(length = 10)
    private String primaryGuestNationality;
    
    @ElementCollection
    @CollectionTable(name = "reservation_additional_guests_java", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<AdditionalGuest> additionalGuests = new ArrayList<>();

    // 기본 생성자
    public ReservationGuestDetails() {}

    // 전체 생성자
    public ReservationGuestDetails(String primaryGuestFirstName, String primaryGuestLastName,
                                  String primaryGuestEmail, String primaryGuestPhone,
                                  String primaryGuestNationality, List<AdditionalGuest> additionalGuests) {
        this.primaryGuestFirstName = primaryGuestFirstName;
        this.primaryGuestLastName = primaryGuestLastName;
        this.primaryGuestEmail = primaryGuestEmail;
        this.primaryGuestPhone = primaryGuestPhone;
        this.primaryGuestNationality = primaryGuestNationality;
        this.additionalGuests = additionalGuests != null ? additionalGuests : new ArrayList<>();
    }

    // 비즈니스 메서드
    public String getPrimaryGuestFullName() {
        return primaryGuestFirstName + " " + primaryGuestLastName;
    }

    // Getters
    public String getPrimaryGuestFirstName() { return primaryGuestFirstName; }
    public String getPrimaryGuestLastName() { return primaryGuestLastName; }
    public String getPrimaryGuestEmail() { return primaryGuestEmail; }
    public String getPrimaryGuestPhone() { return primaryGuestPhone; }
    public String getPrimaryGuestNationality() { return primaryGuestNationality; }
    public List<AdditionalGuest> getAdditionalGuests() { return additionalGuests; }

    // Setters
    public void setPrimaryGuestFirstName(String primaryGuestFirstName) { 
        this.primaryGuestFirstName = primaryGuestFirstName; 
    }
    public void setPrimaryGuestLastName(String primaryGuestLastName) { 
        this.primaryGuestLastName = primaryGuestLastName; 
    }
    public void setPrimaryGuestEmail(String primaryGuestEmail) { 
        this.primaryGuestEmail = primaryGuestEmail; 
    }
    public void setPrimaryGuestPhone(String primaryGuestPhone) { 
        this.primaryGuestPhone = primaryGuestPhone; 
    }
    public void setPrimaryGuestNationality(String primaryGuestNationality) { 
        this.primaryGuestNationality = primaryGuestNationality; 
    }
    public void setAdditionalGuests(List<AdditionalGuest> additionalGuests) { 
        this.additionalGuests = additionalGuests; 
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ReservationGuestDetails that = (ReservationGuestDetails) obj;
        return Objects.equals(primaryGuestFirstName, that.primaryGuestFirstName) &&
               Objects.equals(primaryGuestLastName, that.primaryGuestLastName) &&
               Objects.equals(primaryGuestEmail, that.primaryGuestEmail) &&
               Objects.equals(primaryGuestPhone, that.primaryGuestPhone) &&
               Objects.equals(primaryGuestNationality, that.primaryGuestNationality) &&
               Objects.equals(additionalGuests, that.additionalGuests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryGuestFirstName, primaryGuestLastName, primaryGuestEmail,
                          primaryGuestPhone, primaryGuestNationality, additionalGuests);
    }

    @Override
    public String toString() {
        return "ReservationGuestDetails{" +
                "primaryGuestFirstName='" + primaryGuestFirstName + '\'' +
                ", primaryGuestLastName='" + primaryGuestLastName + '\'' +
                ", primaryGuestEmail='" + primaryGuestEmail + '\'' +
                '}';
    }
}