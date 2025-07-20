package com.example.reservation.domain.reservation;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
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
    @CollectionTable(name = "reservation_additional_guests", joinColumns = @JoinColumn(name = "reservation_id"))
    @Builder.Default
    private List<AdditionalGuest> additionalGuests = new ArrayList<>();
    
    public String getPrimaryGuestFullName() {
        return primaryGuestFirstName + " " + primaryGuestLastName;
    }
}