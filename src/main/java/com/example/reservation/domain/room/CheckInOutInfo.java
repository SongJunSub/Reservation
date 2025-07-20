package com.example.reservation.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class CheckInOutInfo {
    
    @Column(nullable = false)
    @Builder.Default
    private LocalTime checkInTime = LocalTime.of(15, 0);
    
    @Column(nullable = false)
    @Builder.Default
    private LocalTime checkOutTime = LocalTime.of(11, 0);
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean lateCheckInAllowed = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean earlyCheckInAllowed = true;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean lateCheckOutAllowed = true;
}