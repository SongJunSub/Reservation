package com.example.reservation.domain.room;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "rooms")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"property", "amenities", "views"})
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @Column(nullable = false, length = 50)
    private String roomNumber;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BedType bedType;
    
    @Column(nullable = false)
    private Integer maxOccupancy;
    
    @Column(nullable = false)
    private Integer standardOccupancy;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseRate;
    
    @Column(nullable = false)
    private Double size; // 평방미터
    
    @Column(nullable = false)
    private Integer floor;
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity")
    @Builder.Default
    private Set<RoomAmenity> amenities = new HashSet<>();
    
    @ElementCollection
    @CollectionTable(name = "room_views", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "view_type")
    @Builder.Default
    private Set<String> views = new HashSet<>();
    
    @Embedded
    @Builder.Default
    private RoomFeatures roomFeatures = new RoomFeatures();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomStatus status = RoomStatus.AVAILABLE;
    
    @Column(length = 500)
    private String description;
    
    @Column(length = 1000)
    private String specialInstructions;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    // 비즈니스 메서드
    public boolean isAvailable() {
        return status == RoomStatus.AVAILABLE;
    }
    
    public boolean canAccommodate(int guestCount) {
        return guestCount <= maxOccupancy;
    }
    
    public BigDecimal getRecommendedRate(int occupancy) {
        if (occupancy > standardOccupancy) {
            return baseRate.multiply(BigDecimal.valueOf(1.2)); // 20% 추가 요금
        } else {
            return baseRate;
        }
    }
    
    public boolean hasAmenity(RoomAmenity amenity) {
        return amenities.contains(amenity);
    }
    
    public boolean isAccessible() {
        return amenities.contains(RoomAmenity.WHEELCHAIR_ACCESS);
    }
    
    public boolean isSmokingAllowed() {
        return !amenities.contains(RoomAmenity.NON_SMOKING);
    }
    
    public void addAmenity(RoomAmenity amenity) {
        amenities.add(amenity);
    }
    
    public void removeAmenity(RoomAmenity amenity) {
        amenities.remove(amenity);
    }
    
    public void addView(String view) {
        views.add(view);
    }
    
    public void removeView(String view) {
        views.remove(view);
    }
    
    public void reserve() {
        this.status = RoomStatus.RESERVED;
    }
    
    public void occupy() {
        this.status = RoomStatus.OCCUPIED;
    }
    
    public void makeAvailable() {
        this.status = RoomStatus.AVAILABLE;
    }
    
    public void setOutOfOrder() {
        this.status = RoomStatus.OUT_OF_ORDER;
    }
    
    public void setForMaintenance() {
        this.status = RoomStatus.MAINTENANCE;
    }
    
    public void setForCleaning() {
        this.status = RoomStatus.CLEANING;
    }
}