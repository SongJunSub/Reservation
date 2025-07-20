package com.example.reservation.domain.room;

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
public class RoomFeatures {
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasBalcony = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasKitchen = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasLivingRoom = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasBathtub = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasShower = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer numberOfBathrooms = 1;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasDiningArea = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasWorkDesk = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean hasSeatingArea = false;
}