package com.example.reservation.domain.reservation;

import com.example.reservation.domain.guest.Guest;
import com.example.reservation.domain.room.Room;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"guest", "room", "specialRequests"})
public class Reservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;
    
    @Column(unique = true, nullable = false, length = 20)
    private String confirmationNumber;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @Column(nullable = false)
    private LocalDate checkInDate;
    
    @Column(nullable = false)
    private LocalDate checkOutDate;
    
    @Column(nullable = false)
    private Integer numberOfGuests;
    
    @Column(nullable = false)
    private Integer numberOfAdults;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer numberOfChildren = 0;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal roomRate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceCharges = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Embedded
    private ReservationGuestDetails guestDetails;
    
    @Embedded
    @Builder.Default
    private ReservationPreferences preferences = new ReservationPreferences();
    
    @ElementCollection
    @CollectionTable(name = "reservation_special_requests", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "request")
    @Builder.Default
    private List<String> specialRequests = new ArrayList<>();
    
    @Column(length = 1000)
    private String notes;
    
    @Column(length = 1000)
    private String cancellationReason;
    
    @Column
    private LocalDateTime actualCheckInTime;
    
    @Column
    private LocalDateTime actualCheckOutTime;
    
    @Column
    private LocalDateTime estimatedArrivalTime;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationSource source = ReservationSource.DIRECT;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
    
    @Column
    private LocalDateTime confirmedAt;
    
    @Column
    private LocalDateTime cancelledAt;
    
    // 비즈니스 메서드
    public long getNumberOfNights() {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }
    
    public boolean isActive() {
        return status == ReservationStatus.CONFIRMED || 
               status == ReservationStatus.CHECKED_IN ||
               status == ReservationStatus.PENDING;
    }
    
    public boolean canBeCancelled() {
        return (status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED) &&
               checkInDate.isAfter(LocalDate.now());
    }
    
    public boolean canBeModified() {
        return (status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED) &&
               checkInDate.isAfter(LocalDate.now().plusDays(1));
    }
    
    public boolean isNoShow() {
        return status == ReservationStatus.NO_SHOW;
    }
    
    public boolean isOverdue() {
        return status == ReservationStatus.CONFIRMED &&
               checkInDate.isBefore(LocalDate.now()) &&
               actualCheckInTime == null;
    }
    
    public boolean canCheckIn() {
        return status == ReservationStatus.CONFIRMED &&
               !checkInDate.isAfter(LocalDate.now()) &&
               actualCheckInTime == null;
    }
    
    public boolean canCheckOut() {
        return status == ReservationStatus.CHECKED_IN &&
               actualCheckInTime != null &&
               actualCheckOutTime == null;
    }
    
    public BigDecimal calculateRefundAmount() {
        long daysToCancellation = ChronoUnit.DAYS.between(LocalDate.now(), checkInDate);
        
        if (daysToCancellation >= 7) {
            return totalAmount;
        } else if (daysToCancellation >= 3) {
            return totalAmount.multiply(BigDecimal.valueOf(0.5));
        } else if (daysToCancellation >= 1) {
            return totalAmount.multiply(BigDecimal.valueOf(0.2));
        } else {
            return BigDecimal.ZERO;
        }
    }
    
    public void checkIn() {
        this.status = ReservationStatus.CHECKED_IN;
        this.actualCheckInTime = LocalDateTime.now();
    }
    
    public void checkOut() {
        this.status = ReservationStatus.CHECKED_OUT;
        this.actualCheckOutTime = LocalDateTime.now();
    }
    
    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }
    
    public void cancel(String reason) {
        this.status = ReservationStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }
    
    public void markAsNoShow() {
        this.status = ReservationStatus.NO_SHOW;
    }
    
    public void complete() {
        this.status = ReservationStatus.COMPLETED;
    }
    
    public void addSpecialRequest(String request) {
        if (specialRequests == null) {
            specialRequests = new ArrayList<>();
        }
        specialRequests.add(request);
    }
    
    public void removeSpecialRequest(String request) {
        if (specialRequests != null) {
            specialRequests.remove(request);
        }
    }
    
    public boolean hasSpecialRequests() {
        return specialRequests != null && !specialRequests.isEmpty();
    }
    
    public BigDecimal getSubtotal() {
        return roomRate.multiply(BigDecimal.valueOf(getNumberOfNights()));
    }
    
    public BigDecimal getTotalBeforeDiscounts() {
        return getSubtotal().add(taxAmount).add(serviceCharges);
    }
    
    public BigDecimal getFinalAmount() {
        return getTotalBeforeDiscounts().subtract(discountAmount);
    }
    
    public boolean isEarlyCheckIn() {
        return actualCheckInTime != null && 
               actualCheckInTime.toLocalDate().isBefore(checkInDate);
    }
    
    public boolean isLateCheckOut() {
        return actualCheckOutTime != null && 
               actualCheckOutTime.toLocalDate().isAfter(checkOutDate);
    }
    
    public boolean isPaid() {
        return paymentStatus == PaymentStatus.PAID;
    }
    
    public boolean isPartiallyPaid() {
        return paymentStatus == PaymentStatus.PARTIALLY_PAID;
    }
    
    public String getGuestFullName() {
        return guestDetails != null ? guestDetails.getPrimaryGuestFullName() : "";
    }
}