package com.example.reservation.domain.reservation;

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
public class ReservationPreferences {
    
    @Column(nullable = false)
    @Builder.Default
    private String bedTypePreference = "";
    
    @Column(nullable = false)
    @Builder.Default
    private String floorPreference = "";
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean smokingPreference = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean quietRoomPreference = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean accessibilityNeeds = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean earlyCheckInRequested = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean lateCheckOutRequested = false;
}