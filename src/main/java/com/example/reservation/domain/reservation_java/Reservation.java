package com.example.reservation.domain.reservation_java;

import com.example.reservation.domain.guest_java.Guest;
import com.example.reservation.domain.room_java.Room;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "reservations_java")
@EntityListeners(AuditingEntityListener.class)
public class Reservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id = 0L;
    
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
    private Integer numberOfChildren = 0;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal roomRate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharges = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Embedded
    private ReservationGuestDetails guestDetails;
    
    @Embedded
    private ReservationPreferences preferences = new ReservationPreferences();
    
    @ElementCollection
    @CollectionTable(name = "reservation_special_requests_java", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "request")
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
    private ReservationSource source = ReservationSource.DIRECT;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt = LocalDateTime.now();
    
    @Column
    private LocalDateTime confirmedAt;
    
    @Column
    private LocalDateTime cancelledAt;

    // 기본 생성자
    protected Reservation() {}

    // 전체 생성자
    public Reservation(String confirmationNumber, Guest guest, Room room, 
                      LocalDate checkInDate, LocalDate checkOutDate,
                      Integer numberOfGuests, Integer numberOfAdults, Integer numberOfChildren,
                      BigDecimal totalAmount, BigDecimal roomRate, BigDecimal taxAmount,
                      BigDecimal serviceCharges, BigDecimal discountAmount,
                      ReservationStatus status, PaymentStatus paymentStatus,
                      ReservationGuestDetails guestDetails, ReservationPreferences preferences,
                      List<String> specialRequests, String notes, String cancellationReason,
                      LocalDateTime actualCheckInTime, LocalDateTime actualCheckOutTime,
                      LocalDateTime estimatedArrivalTime, ReservationSource source) {
        this.confirmationNumber = confirmationNumber;
        this.guest = guest;
        this.room = room;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.numberOfGuests = numberOfGuests;
        this.numberOfAdults = numberOfAdults;
        this.numberOfChildren = numberOfChildren != null ? numberOfChildren : 0;
        this.totalAmount = totalAmount;
        this.roomRate = roomRate;
        this.taxAmount = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.serviceCharges = serviceCharges != null ? serviceCharges : BigDecimal.ZERO;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.status = status != null ? status : ReservationStatus.PENDING;
        this.paymentStatus = paymentStatus != null ? paymentStatus : PaymentStatus.PENDING;
        this.guestDetails = guestDetails;
        this.preferences = preferences != null ? preferences : new ReservationPreferences();
        this.specialRequests = specialRequests != null ? specialRequests : new ArrayList<>();
        this.notes = notes;
        this.cancellationReason = cancellationReason;
        this.actualCheckInTime = actualCheckInTime;
        this.actualCheckOutTime = actualCheckOutTime;
        this.estimatedArrivalTime = estimatedArrivalTime;
        this.source = source != null ? source : ReservationSource.DIRECT;
    }

    // 비즈니스 메서드들
    public long getNumberOfNights() {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    public boolean isActive() {
        return Arrays.asList(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN, ReservationStatus.PENDING)
                .contains(status);
    }

    public boolean canBeCancelled() {
        return Arrays.asList(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(status) 
                && checkInDate.isAfter(LocalDate.now());
    }

    public boolean canBeModified() {
        return Arrays.asList(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(status)
                && checkInDate.isAfter(LocalDate.now().plusDays(1));
    }

    public boolean isNoShow() {
        return status == ReservationStatus.NO_SHOW;
    }

    public boolean isOverdue() {
        return status == ReservationStatus.CONFIRMED
                && checkInDate.isBefore(LocalDate.now())
                && actualCheckInTime == null;
    }

    public boolean canCheckIn() {
        return status == ReservationStatus.CONFIRMED
                && !checkInDate.isAfter(LocalDate.now())
                && actualCheckInTime == null;
    }

    public boolean canCheckOut() {
        return status == ReservationStatus.CHECKED_IN
                && actualCheckInTime != null
                && actualCheckOutTime == null;
    }

    public BigDecimal calculateRefundAmount() {
        long daysToCancellation = ChronoUnit.DAYS.between(LocalDate.now(), checkInDate);
        if (daysToCancellation >= 7) {
            return totalAmount;
        } else if (daysToCancellation >= 3) {
            return totalAmount.multiply(new BigDecimal("0.5"));
        } else if (daysToCancellation >= 1) {
            return totalAmount.multiply(new BigDecimal("0.2"));
        } else {
            return BigDecimal.ZERO;
        }
    }

    public Reservation checkIn() {
        Reservation updated = this.copy();
        updated.status = ReservationStatus.CHECKED_IN;
        updated.actualCheckInTime = LocalDateTime.now();
        return updated;
    }

    public Reservation checkOut() {
        Reservation updated = this.copy();
        updated.status = ReservationStatus.CHECKED_OUT;
        updated.actualCheckOutTime = LocalDateTime.now();
        return updated;
    }

    public Reservation confirm() {
        Reservation updated = this.copy();
        updated.status = ReservationStatus.CONFIRMED;
        updated.confirmedAt = LocalDateTime.now();
        return updated;
    }

    public Reservation cancel(String reason) {
        Reservation updated = this.copy();
        updated.status = ReservationStatus.CANCELLED;
        updated.cancellationReason = reason;
        updated.cancelledAt = LocalDateTime.now();
        return updated;
    }

    public Reservation markAsNoShow() {
        Reservation updated = this.copy();
        updated.status = ReservationStatus.NO_SHOW;
        return updated;
    }

    // Java에서는 copy 메서드를 수동으로 구현
    private Reservation copy() {
        Reservation copy = new Reservation();
        copy.id = this.id;
        copy.confirmationNumber = this.confirmationNumber;
        copy.guest = this.guest;
        copy.room = this.room;
        copy.checkInDate = this.checkInDate;
        copy.checkOutDate = this.checkOutDate;
        copy.numberOfGuests = this.numberOfGuests;
        copy.numberOfAdults = this.numberOfAdults;
        copy.numberOfChildren = this.numberOfChildren;
        copy.totalAmount = this.totalAmount;
        copy.roomRate = this.roomRate;
        copy.taxAmount = this.taxAmount;
        copy.serviceCharges = this.serviceCharges;
        copy.discountAmount = this.discountAmount;
        copy.status = this.status;
        copy.paymentStatus = this.paymentStatus;
        copy.guestDetails = this.guestDetails;
        copy.preferences = this.preferences;
        copy.specialRequests = new ArrayList<>(this.specialRequests);
        copy.notes = this.notes;
        copy.cancellationReason = this.cancellationReason;
        copy.actualCheckInTime = this.actualCheckInTime;
        copy.actualCheckOutTime = this.actualCheckOutTime;
        copy.estimatedArrivalTime = this.estimatedArrivalTime;
        copy.source = this.source;
        copy.createdAt = this.createdAt;
        copy.lastModifiedAt = this.lastModifiedAt;
        copy.confirmedAt = this.confirmedAt;
        copy.cancelledAt = this.cancelledAt;
        return copy;
    }

    // Getters
    public Long getId() { return id; }
    public String getConfirmationNumber() { return confirmationNumber; }
    public Guest getGuest() { return guest; }
    public Room getRoom() { return room; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public Integer getNumberOfGuests() { return numberOfGuests; }
    public Integer getNumberOfAdults() { return numberOfAdults; }
    public Integer getNumberOfChildren() { return numberOfChildren; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getRoomRate() { return roomRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getServiceCharges() { return serviceCharges; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public ReservationStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public ReservationGuestDetails getGuestDetails() { return guestDetails; }
    public ReservationPreferences getPreferences() { return preferences; }
    public List<String> getSpecialRequests() { return specialRequests; }
    public String getNotes() { return notes; }
    public String getCancellationReason() { return cancellationReason; }
    public LocalDateTime getActualCheckInTime() { return actualCheckInTime; }
    public LocalDateTime getActualCheckOutTime() { return actualCheckOutTime; }
    public LocalDateTime getEstimatedArrivalTime() { return estimatedArrivalTime; }
    public ReservationSource getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }

    // Setters (필요한 경우만)
    public void setId(Long id) { this.id = id; }
    public void setConfirmationNumber(String confirmationNumber) { this.confirmationNumber = confirmationNumber; }
    public void setGuest(Guest guest) { this.guest = guest; }
    public void setRoom(Room room) { this.room = room; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setNumberOfGuests(Integer numberOfGuests) { this.numberOfGuests = numberOfGuests; }
    public void setNumberOfAdults(Integer numberOfAdults) { this.numberOfAdults = numberOfAdults; }
    public void setNumberOfChildren(Integer numberOfChildren) { this.numberOfChildren = numberOfChildren; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setRoomRate(BigDecimal roomRate) { this.roomRate = roomRate; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public void setServiceCharges(BigDecimal serviceCharges) { this.serviceCharges = serviceCharges; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setGuestDetails(ReservationGuestDetails guestDetails) { this.guestDetails = guestDetails; }
    public void setPreferences(ReservationPreferences preferences) { this.preferences = preferences; }
    public void setSpecialRequests(List<String> specialRequests) { this.specialRequests = specialRequests; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }
    public void setActualCheckInTime(LocalDateTime actualCheckInTime) { this.actualCheckInTime = actualCheckInTime; }
    public void setActualCheckOutTime(LocalDateTime actualCheckOutTime) { this.actualCheckOutTime = actualCheckOutTime; }
    public void setEstimatedArrivalTime(LocalDateTime estimatedArrivalTime) { this.estimatedArrivalTime = estimatedArrivalTime; }
    public void setSource(ReservationSource source) { this.source = source; }
    public void setLastModifiedAt(LocalDateTime lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Reservation that = (Reservation) obj;
        return id != null && id != 0L && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Reservation{" +
                "id=" + id +
                ", confirmationNumber='" + confirmationNumber + '\'' +
                ", checkInDate=" + checkInDate +
                ", checkOutDate=" + checkOutDate +
                ", status=" + status +
                ", totalAmount=" + totalAmount +
                '}';
    }
}