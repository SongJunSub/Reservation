package com.example.reservation.domain.guest;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "guests")
@EntityListeners(AuditingEntityListener.class)
public class Guest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
    @Column(nullable = false, length = 100)
    private String firstName;
    
    @Column(nullable = false, length = 100)
    private String lastName;
    
    @Column(unique = true, nullable = false, length = 320)
    private String email;
    
    @Column(length = 20)
    private String phoneNumber;
    
    @Column(length = 10)
    private String nationality;
    
    @Column
    private LocalDate dateOfBirth;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender = Gender.NOT_SPECIFIED;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language preferredLanguage = Language.ENGLISH;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoyaltyTier loyaltyTier = LoyaltyTier.STANDARD;
    
    @Column(nullable = false)
    private Integer loyaltyPoints = 0;
    
    @Embedded
    private Address address;
    
    @Embedded
    private GuestPreferences preferences = new GuestPreferences();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GuestStatus status = GuestStatus.ACTIVE;
    
    @Column(nullable = false)
    private Boolean isEmailVerified = false;
    
    @Column(nullable = false)
    private Boolean isPhoneVerified = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime lastLoginAt;

    // 기본 생성자 (JPA 필수)
    protected Guest() {}

    // 전체 생성자
    public Guest(String firstName, String lastName, String email, String phoneNumber, 
                 String nationality, LocalDate dateOfBirth, Gender gender, 
                 Language preferredLanguage, LoyaltyTier loyaltyTier, Integer loyaltyPoints,
                 Address address, GuestPreferences preferences, GuestStatus status,
                 Boolean isEmailVerified, Boolean isPhoneVerified) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.nationality = nationality;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender != null ? gender : Gender.NOT_SPECIFIED;
        this.preferredLanguage = preferredLanguage != null ? preferredLanguage : Language.ENGLISH;
        this.loyaltyTier = loyaltyTier != null ? loyaltyTier : LoyaltyTier.STANDARD;
        this.loyaltyPoints = loyaltyPoints != null ? loyaltyPoints : 0;
        this.address = address;
        this.preferences = preferences != null ? preferences : new GuestPreferences();
        this.status = status != null ? status : GuestStatus.ACTIVE;
        this.isEmailVerified = isEmailVerified != null ? isEmailVerified : false;
        this.isPhoneVerified = isPhoneVerified != null ? isPhoneVerified : false;
    }

    // 비즈니스 메서드
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean canMakeReservation() {
        return status == GuestStatus.ACTIVE;
    }

    public Guest addLoyaltyPoints(int points) {
        int newPoints = this.loyaltyPoints + points;
        LoyaltyTier newTier = LoyaltyTier.calculateTier(newPoints);
        
        Guest updatedGuest = new Guest(
            this.firstName, this.lastName, this.email, this.phoneNumber,
            this.nationality, this.dateOfBirth, this.gender, this.preferredLanguage,
            newTier, newPoints, this.address, this.preferences, this.status,
            this.isEmailVerified, this.isPhoneVerified
        );
        updatedGuest.id = this.id;
        updatedGuest.createdAt = this.createdAt;
        return updatedGuest;
    }

    public Guest updateLastLogin() {
        Guest updatedGuest = new Guest(
            this.firstName, this.lastName, this.email, this.phoneNumber,
            this.nationality, this.dateOfBirth, this.gender, this.preferredLanguage,
            this.loyaltyTier, this.loyaltyPoints, this.address, this.preferences, 
            this.status, this.isEmailVerified, this.isPhoneVerified
        );
        updatedGuest.id = this.id;
        updatedGuest.createdAt = this.createdAt;
        updatedGuest.lastLoginAt = LocalDateTime.now();
        return updatedGuest;
    }

    // Getters
    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getNationality() { return nationality; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Gender getGender() { return gender; }
    public Language getPreferredLanguage() { return preferredLanguage; }
    public LoyaltyTier getLoyaltyTier() { return loyaltyTier; }
    public Integer getLoyaltyPoints() { return loyaltyPoints; }
    public Address getAddress() { return address; }
    public GuestPreferences getPreferences() { return preferences; }
    public GuestStatus getStatus() { return status; }
    public Boolean getIsEmailVerified() { return isEmailVerified; }
    public Boolean getIsPhoneVerified() { return isPhoneVerified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }

    // Setters (필요한 경우만)
    public void setId(Long id) { this.id = id; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setGender(Gender gender) { this.gender = gender; }
    public void setPreferredLanguage(Language preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public void setLoyaltyTier(LoyaltyTier loyaltyTier) { this.loyaltyTier = loyaltyTier; }
    public void setLoyaltyPoints(Integer loyaltyPoints) { this.loyaltyPoints = loyaltyPoints; }
    public void setAddress(Address address) { this.address = address; }
    public void setPreferences(GuestPreferences preferences) { this.preferences = preferences; }
    public void setStatus(GuestStatus status) { this.status = status; }
    public void setIsEmailVerified(Boolean isEmailVerified) { this.isEmailVerified = isEmailVerified; }
    public void setIsPhoneVerified(Boolean isPhoneVerified) { this.isPhoneVerified = isPhoneVerified; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Guest guest = (Guest) obj;
        return id != null && id != 0L && Objects.equals(id, guest.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Guest{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", loyaltyTier=" + loyaltyTier +
                ", status=" + status +
                '}';
    }
}