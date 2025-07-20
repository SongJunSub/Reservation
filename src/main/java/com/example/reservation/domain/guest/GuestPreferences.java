package com.example.reservation.domain.guest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class GuestPreferences {
    
    @Column(nullable = false)
    @Builder.Default
    private String roomTypePreference = "";
    
    @Column(nullable = false)
    @Builder.Default
    private String floorPreference = "";
    
    @Column(nullable = false)
    @Builder.Default
    private String bedTypePreference = "";
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean smokingPreference = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessibilityNeeds = false;
    
    @Column(nullable = false)
    @Builder.Default
    private String dietaryRestrictions = "";
    
    @Column(nullable = false)
    @Builder.Default
    private String specialRequests = "";
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean marketingOptIn = false;
}