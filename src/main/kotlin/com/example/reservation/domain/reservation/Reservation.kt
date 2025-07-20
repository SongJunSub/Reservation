package com.example.reservation.domain.reservation

import com.example.reservation.domain.guest.Guest
import com.example.reservation.domain.room.Room
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener::class)
data class Reservation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(unique = true, nullable = false, length = 20)
    val confirmationNumber: String,
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_id", nullable = false)
    val guest: Guest,
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,
    
    @Column(nullable = false)
    val checkInDate: LocalDate,
    
    @Column(nullable = false)
    val checkOutDate: LocalDate,
    
    @Column(nullable = false)
    val numberOfGuests: Int,
    
    @Column(nullable = false)
    val numberOfAdults: Int,
    
    @Column(nullable = false)
    val numberOfChildren: Int = 0,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val totalAmount: BigDecimal,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val roomRate: BigDecimal,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val serviceCharges: BigDecimal = BigDecimal.ZERO,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ReservationStatus = ReservationStatus.PENDING,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    
    @Embedded
    val guestDetails: ReservationGuestDetails,
    
    @Embedded
    val preferences: ReservationPreferences = ReservationPreferences(),
    
    @ElementCollection
    @CollectionTable(name = "reservation_special_requests", joinColumns = [JoinColumn(name = "reservation_id")])
    @Column(name = "request")
    val specialRequests: List<String> = emptyList(),
    
    @Column(length = 1000)
    val notes: String? = null,
    
    @Column(length = 1000)
    val cancellationReason: String? = null,
    
    @Column
    val actualCheckInTime: LocalDateTime? = null,
    
    @Column
    val actualCheckOutTime: LocalDateTime? = null,
    
    @Column
    val estimatedArrivalTime: LocalDateTime? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: ReservationSource = ReservationSource.DIRECT,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column
    val confirmedAt: LocalDateTime? = null,
    
    @Column
    val cancelledAt: LocalDateTime? = null
) {
    fun getNumberOfNights(): Long = ChronoUnit.DAYS.between(checkInDate, checkOutDate)
    
    fun isActive(): Boolean = status in listOf(
        ReservationStatus.CONFIRMED, 
        ReservationStatus.CHECKED_IN,
        ReservationStatus.PENDING
    )
    
    fun canBeCancelled(): Boolean = status in listOf(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED
    ) && checkInDate.isAfter(LocalDate.now())
    
    fun canBeModified(): Boolean = status in listOf(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED
    ) && checkInDate.isAfter(LocalDate.now().plusDays(1))
    
    fun isNoShow(): Boolean = status == ReservationStatus.NO_SHOW
    
    fun isOverdue(): Boolean = 
        status == ReservationStatus.CONFIRMED && 
        checkInDate.isBefore(LocalDate.now()) &&
        actualCheckInTime == null
    
    fun canCheckIn(): Boolean = 
        status == ReservationStatus.CONFIRMED &&
        !checkInDate.isAfter(LocalDate.now()) &&
        actualCheckInTime == null
    
    fun canCheckOut(): Boolean = 
        status == ReservationStatus.CHECKED_IN &&
        actualCheckInTime != null &&
        actualCheckOutTime == null
    
    fun calculateRefundAmount(): BigDecimal {
        val daysToCancellation = ChronoUnit.DAYS.between(LocalDate.now(), checkInDate)
        return when {
            daysToCancellation >= 7 -> totalAmount
            daysToCancellation >= 3 -> totalAmount.multiply(BigDecimal("0.5"))
            daysToCancellation >= 1 -> totalAmount.multiply(BigDecimal("0.2"))
            else -> BigDecimal.ZERO
        }
    }
    
    fun checkIn(): Reservation = copy(
        status = ReservationStatus.CHECKED_IN,
        actualCheckInTime = LocalDateTime.now()
    )
    
    fun checkOut(): Reservation = copy(
        status = ReservationStatus.CHECKED_OUT,
        actualCheckOutTime = LocalDateTime.now()
    )
    
    fun confirm(): Reservation = copy(
        status = ReservationStatus.CONFIRMED,
        confirmedAt = LocalDateTime.now()
    )
    
    fun cancel(reason: String? = null): Reservation = copy(
        status = ReservationStatus.CANCELLED,
        cancellationReason = reason,
        cancelledAt = LocalDateTime.now()
    )
    
    fun markAsNoShow(): Reservation = copy(
        status = ReservationStatus.NO_SHOW
    )
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Reservation) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class ReservationGuestDetails(
    @Column(nullable = false, length = 100)
    val primaryGuestFirstName: String,
    
    @Column(nullable = false, length = 100)
    val primaryGuestLastName: String,
    
    @Column(nullable = false, length = 320)
    val primaryGuestEmail: String,
    
    @Column(length = 20)
    val primaryGuestPhone: String? = null,
    
    @Column(length = 10)
    val primaryGuestNationality: String? = null,
    
    @ElementCollection
    @CollectionTable(name = "reservation_additional_guests", joinColumns = [JoinColumn(name = "reservation_id")])
    val additionalGuests: List<AdditionalGuest> = emptyList()
) {
    fun getPrimaryGuestFullName(): String = "$primaryGuestFirstName $primaryGuestLastName"
}

@Embeddable
data class AdditionalGuest(
    @Column(nullable = false, length = 100)
    val firstName: String,
    
    @Column(nullable = false, length = 100)
    val lastName: String,
    
    @Column(nullable = false)
    val isAdult: Boolean = true
) {
    fun getFullName(): String = "$firstName $lastName"
}

@Embeddable
data class ReservationPreferences(
    @Column(nullable = false)
    val bedTypePreference: String = "",
    
    @Column(nullable = false)
    val floorPreference: String = "",
    
    @Column(nullable = false)
    val smokingPreference: Boolean = false,
    
    @Column(nullable = false)
    val quietRoomPreference: Boolean = false,
    
    @Column(nullable = false)
    val accessibilityNeeds: Boolean = false,
    
    @Column(nullable = false)
    val earlyCheckInRequested: Boolean = false,
    
    @Column(nullable = false)
    val lateCheckOutRequested: Boolean = false
)

enum class ReservationStatus {
    PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW, COMPLETED
}

enum class PaymentStatus {
    PENDING, PARTIALLY_PAID, PAID, REFUNDED, FAILED
}

enum class ReservationSource {
    DIRECT, BOOKING_COM, EXPEDIA, AGODA, AIRBNB, PHONE, WALK_IN, CORPORATE, TRAVEL_AGENT
}