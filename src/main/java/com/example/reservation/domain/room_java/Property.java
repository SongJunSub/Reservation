package com.example.reservation.domain.room_java;

import com.example.reservation.domain.guest_java.Address;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "properties_java")
@EntityListeners(AuditingEntityListener.class)
public class Property {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
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
    @CollectionTable(name = "property_amenities_java", joinColumns = @JoinColumn(name = "property_id"))
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
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();

    // 기본 생성자
    protected Property() {}

    // 전체 생성자
    public Property(String name, String description, PropertyType type, PropertyCategory category,
                   Integer starRating, Address address, String phoneNumber, String email,
                   String website, CheckInOutInfo checkInOutInfo, PropertyPolicies policies,
                   Set<PropertyAmenity> amenities, PropertyStatus status, Boolean isPublished) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.category = category;
        this.starRating = starRating;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.website = website;
        this.checkInOutInfo = checkInOutInfo != null ? checkInOutInfo : new CheckInOutInfo();
        this.policies = policies != null ? policies : new PropertyPolicies();
        this.amenities = amenities != null ? amenities : new HashSet<>();
        this.status = status != null ? status : PropertyStatus.ACTIVE;
        this.isPublished = isPublished != null ? isPublished : false;
    }

    // 비즈니스 메서드들
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

    public BigDecimal getLowestRate() {
        return rooms.stream()
                   .map(Room::getBaseRate)
                   .min(BigDecimal::compareTo)
                   .orElse(null);
    }

    public BigDecimal getHighestRate() {
        return rooms.stream()
                   .map(Room::getBaseRate)
                   .max(BigDecimal::compareTo)
                   .orElse(null);
    }

    public boolean isAcceptingReservations() {
        return status == PropertyStatus.ACTIVE && isPublished;
    }

    // Getters
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PropertyType getType() { return type; }
    public PropertyCategory getCategory() { return category; }
    public Integer getStarRating() { return starRating; }
    public Address getAddress() { return address; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public CheckInOutInfo getCheckInOutInfo() { return checkInOutInfo; }
    public PropertyPolicies getPolicies() { return policies; }
    public Set<PropertyAmenity> getAmenities() { return amenities; }
    public List<Room> getRooms() { return rooms; }
    public PropertyStatus getStatus() { return status; }
    public Boolean getIsPublished() { return isPublished; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setType(PropertyType type) { this.type = type; }
    public void setCategory(PropertyCategory category) { this.category = category; }
    public void setStarRating(Integer starRating) { this.starRating = starRating; }
    public void setAddress(Address address) { this.address = address; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setEmail(String email) { this.email = email; }
    public void setWebsite(String website) { this.website = website; }
    public void setCheckInOutInfo(CheckInOutInfo checkInOutInfo) { this.checkInOutInfo = checkInOutInfo; }
    public void setPolicies(PropertyPolicies policies) { this.policies = policies; }
    public void setAmenities(Set<PropertyAmenity> amenities) { this.amenities = amenities; }
    public void setRooms(List<Room> rooms) { this.rooms = rooms; }
    public void setStatus(PropertyStatus status) { this.status = status; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Property property = (Property) obj;
        return id != null && id != 0L && Objects.equals(id, property.id);
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
                ", category=" + category +
                ", starRating=" + starRating +
                ", status=" + status +
                '}';
    }
}