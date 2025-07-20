package com.example.reservation.domain.room;

import com.example.reservation.domain.guest.Address;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"rooms", "amenities"})
public class Property {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
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
    @Builder.Default
    private CheckInOutInfo checkInOutInfo = new CheckInOutInfo();
    
    @Embedded
    @Builder.Default
    private PropertyPolicies policies = new PropertyPolicies();
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "property_amenities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity")
    @Builder.Default
    private Set<PropertyAmenity> amenities = new HashSet<>();
    
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Room> rooms = new ArrayList<>();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.ACTIVE;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublished = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
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
                   .filter(Objects::nonNull)
                   .min(BigDecimal::compareTo);
    }
    
    public Optional<BigDecimal> getHighestRate() {
        return rooms.stream()
                   .map(Room::getBaseRate)
                   .filter(Objects::nonNull)
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
    
    public void setForMaintenance() {
        this.status = PropertyStatus.MAINTENANCE;
    }
    
    public boolean isPetFriendly() {
        return amenities.contains(PropertyAmenity.PET_FRIENDLY);
    }
    
    public boolean hasWifi() {
        return amenities.contains(PropertyAmenity.WIFI);
    }
    
    public boolean hasParking() {
        return amenities.contains(PropertyAmenity.PARKING);
    }
}