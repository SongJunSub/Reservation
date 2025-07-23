package com.example.reservation.domain.availability_java;

import com.example.reservation.domain.room_java.Room;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
    name = "room_availability_java",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "date"})
    },
    indexes = {
        @Index(name = "idx_room_date_java", columnList = "room_id, date"),
        @Index(name = "idx_date_status_java", columnList = "date, status")
    }
)
@EntityListeners(AuditingEntityListener.class)
public class RoomAvailability {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityStatus status = AvailabilityStatus.AVAILABLE;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumRate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumRate;
    
    @Column(nullable = false)
    private Integer minimumStay = 1;
    
    @Column(nullable = false)
    private Integer maximumStay = 30;
    
    @Column(nullable = false)
    private Integer availableRooms = 1;
    
    @Column(nullable = false)
    private Integer totalRooms = 1;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RatePlan ratePlan = RatePlan.STANDARD;
    
    @Column(length = 500)
    private String restrictions;
    
    @Column(length = 1000)
    private String notes;
    
    @Column(nullable = false)
    private Boolean isClosedToArrival = false;
    
    @Column(nullable = false)
    private Boolean isClosedToDeparture = false;
    
    @Column(nullable = false)
    private Boolean stopSell = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();

    // 기본 생성자
    protected RoomAvailability() {}

    // 전체 생성자
    public RoomAvailability(Room room, LocalDate date, AvailabilityStatus status,
                           BigDecimal rate, BigDecimal minimumRate, BigDecimal maximumRate,
                           Integer minimumStay, Integer maximumStay, Integer availableRooms,
                           Integer totalRooms, RatePlan ratePlan, String restrictions,
                           String notes, Boolean isClosedToArrival, Boolean isClosedToDeparture,
                           Boolean stopSell) {
        this.room = room;
        this.date = date;
        this.status = status != null ? status : AvailabilityStatus.AVAILABLE;
        this.rate = rate;
        this.minimumRate = minimumRate != null ? minimumRate : rate;
        this.maximumRate = maximumRate != null ? maximumRate : rate;
        this.minimumStay = minimumStay != null ? minimumStay : 1;
        this.maximumStay = maximumStay != null ? maximumStay : 30;
        this.availableRooms = availableRooms != null ? availableRooms : 1;
        this.totalRooms = totalRooms != null ? totalRooms : 1;
        this.ratePlan = ratePlan != null ? ratePlan : RatePlan.STANDARD;
        this.restrictions = restrictions;
        this.notes = notes;
        this.isClosedToArrival = isClosedToArrival != null ? isClosedToArrival : false;
        this.isClosedToDeparture = isClosedToDeparture != null ? isClosedToDeparture : false;
        this.stopSell = stopSell != null ? stopSell : false;
    }

    // 비즈니스 메서드들
    public boolean isAvailable() {
        return status == AvailabilityStatus.AVAILABLE && 
               availableRooms > 0 && 
               !stopSell;
    }

    public boolean canCheckIn() {
        return isAvailable() && !isClosedToArrival;
    }

    public boolean canCheckOut() {
        return !isClosedToDeparture;
    }

    public boolean canStay(int nights) {
        return nights >= minimumStay && nights <= maximumStay;
    }

    public BigDecimal getRateForNights(int nights) {
        if (nights <= 0) {
            return BigDecimal.ZERO;
        }
        return rate.multiply(BigDecimal.valueOf(nights));
    }

    public double getOccupancyRate() {
        if (totalRooms == 0) {
            return 0.0;
        }
        return (double) (totalRooms - availableRooms) / totalRooms;
    }

    public RoomAvailability reserveRoom() {
        RoomAvailability updated = this.copy();
        updated.availableRooms = Math.max(0, availableRooms - 1);
        if (updated.availableRooms <= 0) {
            updated.status = AvailabilityStatus.SOLD_OUT;
        }
        return updated;
    }

    public RoomAvailability releaseRoom() {
        RoomAvailability updated = this.copy();
        updated.availableRooms = Math.min(totalRooms, availableRooms + 1);
        if (updated.availableRooms > 0) {
            updated.status = AvailabilityStatus.AVAILABLE;
        }
        return updated;
    }

    public RoomAvailability updateRate(BigDecimal newRate) {
        RoomAvailability updated = this.copy();
        updated.rate = newRate;
        updated.minimumRate = minimumRate.min(newRate);
        updated.maximumRate = maximumRate.max(newRate);
        return updated;
    }

    public RoomAvailability block(String reason) {
        RoomAvailability updated = this.copy();
        updated.status = AvailabilityStatus.BLOCKED;
        updated.notes = reason;
        return updated;
    }

    public RoomAvailability unblock() {
        RoomAvailability updated = this.copy();
        updated.status = availableRooms > 0 ? AvailabilityStatus.AVAILABLE : AvailabilityStatus.SOLD_OUT;
        return updated;
    }

    // Copy method for immutability pattern
    public RoomAvailability copy() {
        RoomAvailability copy = new RoomAvailability();
        copy.id = this.id;
        copy.room = this.room;
        copy.date = this.date;
        copy.status = this.status;
        copy.rate = this.rate;
        copy.minimumRate = this.minimumRate;
        copy.maximumRate = this.maximumRate;
        copy.minimumStay = this.minimumStay;
        copy.maximumStay = this.maximumStay;
        copy.availableRooms = this.availableRooms;
        copy.totalRooms = this.totalRooms;
        copy.ratePlan = this.ratePlan;
        copy.restrictions = this.restrictions;
        copy.notes = this.notes;
        copy.isClosedToArrival = this.isClosedToArrival;
        copy.isClosedToDeparture = this.isClosedToDeparture;
        copy.stopSell = this.stopSell;
        copy.createdAt = this.createdAt;
        copy.lastModifiedAt = this.lastModifiedAt;
        return copy;
    }

    // Getters
    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public LocalDate getDate() { return date; }
    public AvailabilityStatus getStatus() { return status; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getMinimumRate() { return minimumRate; }
    public BigDecimal getMaximumRate() { return maximumRate; }
    public Integer getMinimumStay() { return minimumStay; }
    public Integer getMaximumStay() { return maximumStay; }
    public Integer getAvailableRooms() { return availableRooms; }
    public Integer getTotalRooms() { return totalRooms; }
    public RatePlan getRatePlan() { return ratePlan; }
    public String getRestrictions() { return restrictions; }
    public String getNotes() { return notes; }
    public Boolean getIsClosedToArrival() { return isClosedToArrival; }
    public Boolean getIsClosedToDeparture() { return isClosedToDeparture; }
    public Boolean getStopSell() { return stopSell; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setRoom(Room room) { this.room = room; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setStatus(AvailabilityStatus status) { this.status = status; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public void setMinimumRate(BigDecimal minimumRate) { this.minimumRate = minimumRate; }
    public void setMaximumRate(BigDecimal maximumRate) { this.maximumRate = maximumRate; }
    public void setMinimumStay(Integer minimumStay) { this.minimumStay = minimumStay; }
    public void setMaximumStay(Integer maximumStay) { this.maximumStay = maximumStay; }
    public void setAvailableRooms(Integer availableRooms) { this.availableRooms = availableRooms; }
    public void setTotalRooms(Integer totalRooms) { this.totalRooms = totalRooms; }
    public void setRatePlan(RatePlan ratePlan) { this.ratePlan = ratePlan; }
    public void setRestrictions(String restrictions) { this.restrictions = restrictions; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setIsClosedToArrival(Boolean isClosedToArrival) { this.isClosedToArrival = isClosedToArrival; }
    public void setIsClosedToDeparture(Boolean isClosedToDeparture) { this.isClosedToDeparture = isClosedToDeparture; }
    public void setStopSell(Boolean stopSell) { this.stopSell = stopSell; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RoomAvailability that = (RoomAvailability) obj;
        return id != null && id != 0L && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoomAvailability{" +
                "id=" + id +
                ", date=" + date +
                ", status=" + status +
                ", rate=" + rate +
                ", availableRooms=" + availableRooms +
                ", totalRooms=" + totalRooms +
                ", ratePlan=" + ratePlan +
                '}';
    }
}