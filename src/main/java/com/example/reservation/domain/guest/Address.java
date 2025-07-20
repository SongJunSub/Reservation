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
public class Address {
    
    @Column(length = 200)
    private String street;
    
    @Column(length = 100)
    private String city;
    
    @Column(length = 100)
    private String state;
    
    @Column(length = 20)
    private String postalCode;
    
    @Column(length = 3)
    private String countryCode;
    
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (street != null) address.append(street).append(", ");
        if (city != null) address.append(city);
        if (state != null) address.append(", ").append(state);
        if (postalCode != null) address.append(" ").append(postalCode);
        if (countryCode != null) address.append(", ").append(countryCode);
        return address.toString();
    }
}