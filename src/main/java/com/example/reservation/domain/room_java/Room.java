package com.example.reservation.domain.room_java;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "rooms_java")
@EntityListeners(AuditingEntityListener.class)
public class Room {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
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
    @CollectionTable(name = "room_amenities_java", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity")
    private Set<RoomAmenity> amenities = new HashSet<>();
    
    @ElementCollection
    @CollectionTable(name = "room_views_java", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "view_type")
    private Set<String> views = new HashSet<>();
    
    @Embedded
    private RoomFeatures roomFeatures = new RoomFeatures();
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.AVAILABLE;
    
    @Column(length = 500)
    private String description;
    
    @Column(length = 1000)
    private String specialInstructions;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();

    // 기본 생성자
    protected Room() {}

    // 전체 생성자
    public Room(Property property, String roomNumber, String name, RoomType type,
               BedType bedType, Integer maxOccupancy, Integer standardOccupancy,
               BigDecimal baseRate, Double size, Integer floor, Set<RoomAmenity> amenities,
               Set<String> views, RoomFeatures roomFeatures, RoomStatus status,
               String description, String specialInstructions) {
        this.property = property;
        this.roomNumber = roomNumber;
        this.name = name;
        this.type = type;
        this.bedType = bedType;
        this.maxOccupancy = maxOccupancy;
        this.standardOccupancy = standardOccupancy;
        this.baseRate = baseRate;
        this.size = size;
        this.floor = floor;
        this.amenities = amenities != null ? amenities : new HashSet<>();
        this.views = views != null ? views : new HashSet<>();
        this.roomFeatures = roomFeatures != null ? roomFeatures : new RoomFeatures();
        this.status = status != null ? status : RoomStatus.AVAILABLE;
        this.description = description;
        this.specialInstructions = specialInstructions;
    }

    // 비즈니스 메서드들
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

    // Getters
    public Long getId() { return id; }
    public Property getProperty() { return property; }
    public String getRoomNumber() { return roomNumber; }
    public String getName() { return name; }
    public RoomType getType() { return type; }
    public BedType getBedType() { return bedType; }
    public Integer getMaxOccupancy() { return maxOccupancy; }
    public Integer getStandardOccupancy() { return standardOccupancy; }
    public BigDecimal getBaseRate() { return baseRate; }
    public Double getSize() { return size; }
    public Integer getFloor() { return floor; }
    public Set<RoomAmenity> getAmenities() { return amenities; }
    public Set<String> getViews() { return views; }
    public RoomFeatures getRoomFeatures() { return roomFeatures; }
    public RoomStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public String getSpecialInstructions() { return specialInstructions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setProperty(Property property) { this.property = property; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setName(String name) { this.name = name; }
    public void setType(RoomType type) { this.type = type; }
    public void setBedType(BedType bedType) { this.bedType = bedType; }
    public void setMaxOccupancy(Integer maxOccupancy) { this.maxOccupancy = maxOccupancy; }
    public void setStandardOccupancy(Integer standardOccupancy) { this.standardOccupancy = standardOccupancy; }
    public void setBaseRate(BigDecimal baseRate) { this.baseRate = baseRate; }
    public void setSize(Double size) { this.size = size; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public void setAmenities(Set<RoomAmenity> amenities) { this.amenities = amenities; }
    public void setViews(Set<String> views) { this.views = views; }
    public void setRoomFeatures(RoomFeatures roomFeatures) { this.roomFeatures = roomFeatures; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public void setDescription(String description) { this.description = description; }
    public void setSpecialInstructions(String specialInstructions) { this.specialInstructions = specialInstructions; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Room room = (Room) obj;
        return id != null && id != 0L && Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Room{" +
                "id=" + id +
                ", roomNumber='" + roomNumber + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", status=" + status +
                '}';
    }
}