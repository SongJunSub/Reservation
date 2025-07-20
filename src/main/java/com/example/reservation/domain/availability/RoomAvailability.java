package com.example.reservation.domain.availability;

import com.example.reservation.domain.room.Room;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "room_availability",
    uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "date"}),
    indexes = {
        @Index(name = "idx_room_date", columnList = "room_id, date"),
        @Index(name = "idx_date_status", columnList = "date, status")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"room"})
public class RoomAvailability {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @Column(nullable = false)
    private LocalDate date;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AvailabilityStatus status = AvailabilityStatus.AVAILABLE;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumRate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maximumRate;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer minimumStay = 1;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer maximumStay = 30;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer availableRooms = 1;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer totalRooms = 1;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RatePlan ratePlan = RatePlan.STANDARD;
    
    @Column(length = 500)
    private String restrictions;
    
    @Column(length = 1000)
    private String notes;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isClosedToArrival = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean isClosedToDeparture = false;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean stopSell = false;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    // 비즈니스 메서드
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
        if (nights <= 0) return BigDecimal.ZERO;
        return rate.multiply(BigDecimal.valueOf(nights));
    }
    
    public double getOccupancyRate() {
        if (totalRooms == 0) return 0.0;
        return (double) (totalRooms - availableRooms) / totalRooms;
    }
    
    public void reserveRoom() {
        this.availableRooms = Math.max(0, availableRooms - 1);
        if (availableRooms <= 0) {
            this.status = AvailabilityStatus.SOLD_OUT;
        }
    }
    
    public void releaseRoom() {
        this.availableRooms = Math.min(totalRooms, availableRooms + 1);
        if (availableRooms > 0 && status == AvailabilityStatus.SOLD_OUT) {
            this.status = AvailabilityStatus.AVAILABLE;
        }
    }
    
    public void updateRate(BigDecimal newRate) {
        this.rate = newRate;
        this.minimumRate = minimumRate.min(newRate);
        this.maximumRate = maximumRate.max(newRate);
    }
    
    public void block(String reason) {
        this.status = AvailabilityStatus.BLOCKED;
        this.notes = reason;
    }
    
    public void unblock() {
        this.status = availableRooms > 0 ? 
            AvailabilityStatus.AVAILABLE : AvailabilityStatus.SOLD_OUT;
    }
    
    public void setForMaintenance() {
        this.status = AvailabilityStatus.MAINTENANCE;
    }
    
    public void setOutOfOrder() {
        this.status = AvailabilityStatus.OUT_OF_ORDER;
    }
    
    public void enableStopSell() {
        this.stopSell = true;
    }
    
    public void disableStopSell() {
        this.stopSell = false;
    }
    
    public void closeToArrival() {
        this.isClosedToArrival = true;
    }
    
    public void openToArrival() {
        this.isClosedToArrival = false;
    }
    
    public void closeToDeparture() {
        this.isClosedToDeparture = true;
    }
    
    public void openToDeparture() {
        this.isClosedToDeparture = false;
    }
    
    public boolean isHighDemand() {
        return getOccupancyRate() >= 0.8;
    }
    
    public boolean isLowDemand() {
        return getOccupancyRate() <= 0.3;
    }
    
    // Builder의 기본값 설정을 위한 메서드
    @Builder
    public RoomAvailability(Long id, Room room, LocalDate date, AvailabilityStatus status,
                          BigDecimal rate, BigDecimal minimumRate, BigDecimal maximumRate,
                          Integer minimumStay, Integer maximumStay, Integer availableRooms,
                          Integer totalRooms, RatePlan ratePlan, String restrictions,
                          String notes, Boolean isClosedToArrival, Boolean isClosedToDeparture,
                          Boolean stopSell, LocalDateTime createdAt, LocalDateTime lastModifiedAt) {
        this.id = id;
        this.room = room;
        this.date = date;
        this.status = status;
        this.rate = rate;
        this.minimumRate = minimumRate != null ? minimumRate : rate;
        this.maximumRate = maximumRate != null ? maximumRate : rate;
        this.minimumStay = minimumStay;
        this.maximumStay = maximumStay;
        this.availableRooms = availableRooms;
        this.totalRooms = totalRooms;
        this.ratePlan = ratePlan;
        this.restrictions = restrictions;
        this.notes = notes;
        this.isClosedToArrival = isClosedToArrival;
        this.isClosedToDeparture = isClosedToDeparture;
        this.stopSell = stopSell;
        this.createdAt = createdAt;
        this.lastModifiedAt = lastModifiedAt;
    }
}