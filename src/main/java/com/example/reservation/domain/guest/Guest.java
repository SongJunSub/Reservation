package com.example.reservation.domain.guest;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "guests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"preferences", "address"})
public class Guest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
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
    @Builder.Default
    private Gender gender = Gender.NOT_SPECIFIED;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Language preferredLanguage = Language.ENGLISH;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoyaltyTier loyaltyTier = LoyaltyTier.STANDARD;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer loyaltyPoints = 0;
    
    @Embedded
    private Address address;
    
    @Embedded
    @Builder.Default
    private GuestPreferences preferences = new GuestPreferences();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GuestStatus status = GuestStatus.ACTIVE;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPhoneVerified = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    @Column
    private LocalDateTime lastLoginAt;
    
    // 비즈니스 메서드
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    public boolean canMakeReservation() {
        return status == GuestStatus.ACTIVE;
    }
    
    public void addLoyaltyPoints(int points) {
        this.loyaltyPoints += points;
        this.loyaltyTier = LoyaltyTier.calculateTier(this.loyaltyPoints);
    }
    
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
    
    public void verifyEmail() {
        this.isEmailVerified = true;
    }
    
    public void verifyPhone() {
        this.isPhoneVerified = true;
    }
    
    public void suspend() {
        this.status = GuestStatus.SUSPENDED;
    }
    
    public void activate() {
        this.status = GuestStatus.ACTIVE;
    }
    
    public void blacklist() {
        this.status = GuestStatus.BLACKLISTED;
    }
    
    public void deactivate() {
        this.status = GuestStatus.INACTIVE;
    }
    
    public double getApplicableDiscount() {
        return loyaltyTier.getDiscountPercentage();
    }
    
    public boolean isVip() {
        return loyaltyTier.ordinal() >= LoyaltyTier.GOLD.ordinal();
    }
}