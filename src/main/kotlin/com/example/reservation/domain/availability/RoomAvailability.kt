package com.example.reservation.domain.availability

import com.example.reservation.domain.room.Room
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "room_availability",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["room_id", "date"])
    ],
    indexes = [
        Index(name = "idx_room_date", columnList = "room_id, date"),
        Index(name = "idx_date_status", columnList = "date, status")
    ]
)
@EntityListeners(AuditingEntityListener::class)
data class RoomAvailability(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,
    
    @Column(nullable = false)
    val date: LocalDate,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AvailabilityStatus = AvailabilityStatus.AVAILABLE,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val rate: BigDecimal,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val minimumRate: BigDecimal = rate,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val maximumRate: BigDecimal = rate,
    
    @Column(nullable = false)
    val minimumStay: Int = 1,
    
    @Column(nullable = false)
    val maximumStay: Int = 30,
    
    @Column(nullable = false)
    val availableRooms: Int = 1,
    
    @Column(nullable = false)
    val totalRooms: Int = 1,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val ratePlan: RatePlan = RatePlan.STANDARD,
    
    @Column(length = 500)
    val restrictions: String? = null,
    
    @Column(length = 1000)
    val notes: String? = null,
    
    @Column(nullable = false)
    val isClosedToArrival: Boolean = false,
    
    @Column(nullable = false)
    val isClosedToDeparture: Boolean = false,
    
    @Column(nullable = false)
    val stopSell: Boolean = false,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isAvailable(): Boolean = 
        status == AvailabilityStatus.AVAILABLE && 
        availableRooms > 0 && 
        !stopSell
    
    fun canCheckIn(): Boolean = 
        isAvailable() && !isClosedToArrival
    
    fun canCheckOut(): Boolean = 
        !isClosedToDeparture
    
    fun canStay(nights: Int): Boolean = 
        nights >= minimumStay && nights <= maximumStay
    
    fun getRateForNights(nights: Int): BigDecimal = 
        if (nights <= 0) BigDecimal.ZERO else rate.multiply(BigDecimal.valueOf(nights.toLong()))
    
    fun getOccupancyRate(): Double = 
        if (totalRooms == 0) 0.0 
        else (totalRooms - availableRooms).toDouble() / totalRooms.toDouble()
    
    fun reserveRoom(): RoomAvailability = copy(
        availableRooms = maxOf(0, availableRooms - 1),
        status = if (availableRooms <= 1) AvailabilityStatus.SOLD_OUT else status
    )
    
    fun releaseRoom(): RoomAvailability = copy(
        availableRooms = minOf(totalRooms, availableRooms + 1),
        status = if (availableRooms >= 0) AvailabilityStatus.AVAILABLE else status
    )
    
    fun updateRate(newRate: BigDecimal): RoomAvailability = copy(
        rate = newRate,
        minimumRate = minOf(minimumRate, newRate),
        maximumRate = maxOf(maximumRate, newRate)
    )
    
    fun block(reason: String? = null): RoomAvailability = copy(
        status = AvailabilityStatus.BLOCKED,
        notes = reason
    )
    
    fun unblock(): RoomAvailability = copy(
        status = if (availableRooms > 0) AvailabilityStatus.AVAILABLE else AvailabilityStatus.SOLD_OUT
    )
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoomAvailability) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

enum class AvailabilityStatus {
    AVAILABLE,      // 예약 가능
    SOLD_OUT,       // 매진
    BLOCKED,        // 차단됨 (관리자에 의해)
    MAINTENANCE,    // 정비중
    OUT_OF_ORDER,   // 고장
    RESERVED        // 예약됨
}

enum class RatePlan {
    STANDARD,           // 기본 요금
    EARLY_BIRD,         // 조기 예약 할인
    LAST_MINUTE,        // 막판 특가
    WEEKEND,            // 주말 요금
    HOLIDAY,            // 휴일 요금
    PEAK_SEASON,        // 성수기 요금
    LOW_SEASON,         // 비수기 요금
    CORPORATE,          // 기업 요금
    GROUP_DISCOUNT,     // 단체 할인
    LOYALTY_MEMBER,     // 멤버십 할인
    PROMOTIONAL        // 프로모션 요금
}