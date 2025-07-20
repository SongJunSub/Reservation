package com.example.reservation.domain.room;

import com.example.reservation.domain.guest.Address;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener.class)
public class Property {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyCategory category;
    
    @Column(nullable = false)
    private Integer starRating;
    
    @Embedded
    private Address address;
    
    @Column(length = 20)
    private String phoneNumber;
    
    @Column(length = 320)
    private String email;
    
    @Column(length = 500)
    private String website;
    
    @Embedded
    private CheckInOutInfo checkInOutInfo = new CheckInOutInfo();
    
    @Embedded
    private PropertyPolicies policies = new PropertyPolicies();
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "property_amenities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity")
    private Set<PropertyAmenity> amenities = new HashSet<>();
    
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Room> rooms = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.ACTIVE;
    
    @Column(nullable = false)
    private Boolean isPublished = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    protected Property() {}
    
    public Property(String name, PropertyType type, PropertyCategory category, 
                   Integer starRating, Address address) {
        this.name = name;
        this.type = type;
        this.category = category;
        this.starRating = starRating;
        this.address = address;
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }
    
    // 비즈니스 메서드
    public int getTotalRooms() {
        return rooms.size();
    }
    
    public List<Room> getAvailableRooms() {
        return rooms.stream()
                   .filter(Room::isAvailable)
                   .toList();
    }
    
    public List<Room> getRoomsByType(RoomType roomType) {
        return rooms.stream()
                   .filter(room -> room.getType() == roomType)
                   .toList();
    }
    
    public Optional<BigDecimal> getLowestRate() {
        return rooms.stream()
                   .map(Room::getBaseRate)
                   .min(BigDecimal::compareTo);
    }
    
    public Optional<BigDecimal> getHighestRate() {
        return rooms.stream()
                   .map(Room::getBaseRate)
                   .max(BigDecimal::compareTo);
    }
    
    public boolean isAcceptingReservations() {
        return status == PropertyStatus.ACTIVE && isPublished;
    }
    
    public void addRoom(Room room) {
        rooms.add(room);
    }
    
    public void removeRoom(Room room) {
        rooms.remove(room);
    }
    
    public void addAmenity(PropertyAmenity amenity) {
        amenities.add(amenity);
    }
    
    public void removeAmenity(PropertyAmenity amenity) {
        amenities.remove(amenity);
    }
    
    public boolean hasAmenity(PropertyAmenity amenity) {
        return amenities.contains(amenity);
    }
    
    public void publish() {
        this.isPublished = true;
    }
    
    public void unpublish() {
        this.isPublished = false;
    }
    
    public void activate() {
        this.status = PropertyStatus.ACTIVE;
    }
    
    public void deactivate() {
        this.status = PropertyStatus.INACTIVE;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public PropertyType getType() { return type; }
    public void setType(PropertyType type) { this.type = type; }
    
    public PropertyCategory getCategory() { return category; }
    public void setCategory(PropertyCategory category) { this.category = category; }
    
    public Integer getStarRating() { return starRating; }
    public void setStarRating(Integer starRating) { this.starRating = starRating; }
    
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    
    public CheckInOutInfo getCheckInOutInfo() { return checkInOutInfo; }
    public void setCheckInOutInfo(CheckInOutInfo checkInOutInfo) { this.checkInOutInfo = checkInOutInfo; }
    
    public PropertyPolicies getPolicies() { return policies; }
    public void setPolicies(PropertyPolicies policies) { this.policies = policies; }
    
    public Set<PropertyAmenity> getAmenities() { return new HashSet<>(amenities); }
    public void setAmenities(Set<PropertyAmenity> amenities) { this.amenities = new HashSet<>(amenities); }
    
    public List<Room> getRooms() { return new ArrayList<>(rooms); }
    public void setRooms(List<Room> rooms) { this.rooms = new ArrayList<>(rooms); }
    
    public PropertyStatus getStatus() { return status; }
    public void setStatus(PropertyStatus status) { this.status = status; }
    
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Property property = (Property) obj;
        return Objects.equals(id, property.id) && id != null;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Property{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", starRating=" + starRating +
                ", status=" + status +
                '}';
    }
}