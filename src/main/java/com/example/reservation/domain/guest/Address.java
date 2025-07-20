package com.example.reservation.domain.guest;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
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
    
    protected Address() {}
    
    public Address(String street, String city, String state, String postalCode, String countryCode) {
        this.street = street;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }
    
    public String getFullAddress() {
        StringBuilder address = new StringBuilder();
        if (street != null) address.append(street).append(", ");
        if (city != null) address.append(city);
        if (state != null) address.append(", ").append(state);
        if (postalCode != null) address.append(" ").append(postalCode);
        if (countryCode != null) address.append(", ").append(countryCode);
        return address.toString();
    }
    
    // Getters and Setters
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Address address = (Address) obj;
        return Objects.equals(street, address.street) &&
               Objects.equals(city, address.city) &&
               Objects.equals(state, address.state) &&
               Objects.equals(postalCode, address.postalCode) &&
               Objects.equals(countryCode, address.countryCode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(street, city, state, postalCode, countryCode);
    }
}