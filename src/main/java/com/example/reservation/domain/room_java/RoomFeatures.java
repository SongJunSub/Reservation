package com.example.reservation.domain.room_java;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class RoomFeatures {
    
    @Column(nullable = false)
    private Boolean hasBalcony = false;
    
    @Column(nullable = false)
    private Boolean hasKitchen = false;
    
    @Column(nullable = false)
    private Boolean hasLivingRoom = false;
    
    @Column(nullable = false)
    private Boolean hasBathtub = false;
    
    @Column(nullable = false)
    private Boolean hasShower = true;
    
    @Column(nullable = false)
    private Integer numberOfBathrooms = 1;
    
    @Column(nullable = false)
    private Boolean hasDiningArea = false;
    
    @Column(nullable = false)
    private Boolean hasWorkDesk = true;
    
    @Column(nullable = false)
    private Boolean hasSeatingArea = false;

    public RoomFeatures() {}

    public RoomFeatures(Boolean hasBalcony, Boolean hasKitchen, Boolean hasLivingRoom,
                       Boolean hasBathtub, Boolean hasShower, Integer numberOfBathrooms,
                       Boolean hasDiningArea, Boolean hasWorkDesk, Boolean hasSeatingArea) {
        this.hasBalcony = hasBalcony != null ? hasBalcony : false;
        this.hasKitchen = hasKitchen != null ? hasKitchen : false;
        this.hasLivingRoom = hasLivingRoom != null ? hasLivingRoom : false;
        this.hasBathtub = hasBathtub != null ? hasBathtub : false;
        this.hasShower = hasShower != null ? hasShower : true;
        this.numberOfBathrooms = numberOfBathrooms != null ? numberOfBathrooms : 1;
        this.hasDiningArea = hasDiningArea != null ? hasDiningArea : false;
        this.hasWorkDesk = hasWorkDesk != null ? hasWorkDesk : true;
        this.hasSeatingArea = hasSeatingArea != null ? hasSeatingArea : false;
    }

    // Getters
    public Boolean getHasBalcony() { return hasBalcony; }
    public Boolean getHasKitchen() { return hasKitchen; }
    public Boolean getHasLivingRoom() { return hasLivingRoom; }
    public Boolean getHasBathtub() { return hasBathtub; }
    public Boolean getHasShower() { return hasShower; }
    public Integer getNumberOfBathrooms() { return numberOfBathrooms; }
    public Boolean getHasDiningArea() { return hasDiningArea; }
    public Boolean getHasWorkDesk() { return hasWorkDesk; }
    public Boolean getHasSeatingArea() { return hasSeatingArea; }

    // Setters
    public void setHasBalcony(Boolean hasBalcony) { this.hasBalcony = hasBalcony; }
    public void setHasKitchen(Boolean hasKitchen) { this.hasKitchen = hasKitchen; }
    public void setHasLivingRoom(Boolean hasLivingRoom) { this.hasLivingRoom = hasLivingRoom; }
    public void setHasBathtub(Boolean hasBathtub) { this.hasBathtub = hasBathtub; }
    public void setHasShower(Boolean hasShower) { this.hasShower = hasShower; }
    public void setNumberOfBathrooms(Integer numberOfBathrooms) { this.numberOfBathrooms = numberOfBathrooms; }
    public void setHasDiningArea(Boolean hasDiningArea) { this.hasDiningArea = hasDiningArea; }
    public void setHasWorkDesk(Boolean hasWorkDesk) { this.hasWorkDesk = hasWorkDesk; }
    public void setHasSeatingArea(Boolean hasSeatingArea) { this.hasSeatingArea = hasSeatingArea; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RoomFeatures that = (RoomFeatures) obj;
        return Objects.equals(hasBalcony, that.hasBalcony) &&
               Objects.equals(hasKitchen, that.hasKitchen) &&
               Objects.equals(hasLivingRoom, that.hasLivingRoom) &&
               Objects.equals(hasBathtub, that.hasBathtub) &&
               Objects.equals(hasShower, that.hasShower) &&
               Objects.equals(numberOfBathrooms, that.numberOfBathrooms) &&
               Objects.equals(hasDiningArea, that.hasDiningArea) &&
               Objects.equals(hasWorkDesk, that.hasWorkDesk) &&
               Objects.equals(hasSeatingArea, that.hasSeatingArea);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasBalcony, hasKitchen, hasLivingRoom, hasBathtub, hasShower,
                          numberOfBathrooms, hasDiningArea, hasWorkDesk, hasSeatingArea);
    }
}