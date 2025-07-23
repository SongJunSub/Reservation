package com.example.reservation.domain.reservation_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class AdditionalGuest {
    
    @Column(nullable = false, length = 100)
    private String firstName;
    
    @Column(nullable = false, length = 100)
    private String lastName;
    
    @Column(nullable = false)
    private Boolean isAdult = true;

    // 기본 생성자
    public AdditionalGuest() {}

    // 전체 생성자
    public AdditionalGuest(String firstName, String lastName, Boolean isAdult) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.isAdult = isAdult != null ? isAdult : true;
    }

    // 비즈니스 메서드
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Getters
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Boolean getIsAdult() { return isAdult; }

    // Setters
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setIsAdult(Boolean isAdult) { this.isAdult = isAdult; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AdditionalGuest that = (AdditionalGuest) obj;
        return Objects.equals(firstName, that.firstName) &&
               Objects.equals(lastName, that.lastName) &&
               Objects.equals(isAdult, that.isAdult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName, lastName, isAdult);
    }

    @Override
    public String toString() {
        return "AdditionalGuest{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", isAdult=" + isAdult +
                '}';
    }
}