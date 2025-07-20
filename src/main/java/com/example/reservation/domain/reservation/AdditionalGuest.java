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
public class AdditionalGuest {
    
    @Column(nullable = false, length = 100)
    private String firstName;
    
    @Column(nullable = false, length = 100)
    private String lastName;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isAdult = true;
    
    public String getFullName() {
        return firstName + " " + lastName;
    }
}