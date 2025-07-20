package com.example.reservation.domain.room

import com.example.reservation.domain.guest.Address
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener::class)
data class Property(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, length = 200)
    val name: String,
    
    @Column(length = 1000)
    val description: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: PropertyType,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val category: PropertyCategory,
    
    @Column(nullable = false)
    val starRating: Int,
    
    @Embedded
    val address: Address,
    
    @Column(length = 20)
    val phoneNumber: String? = null,
    
    @Column(length = 320)
    val email: String? = null,
    
    @Column(length = 500)
    val website: String? = null,
    
    @Embedded
    val checkInOutInfo: CheckInOutInfo = CheckInOutInfo(),
    
    @Embedded
    val policies: PropertyPolicies = PropertyPolicies(),
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "property_amenities", joinColumns = [JoinColumn(name = "property_id")])
    @Column(name = "amenity")
    val amenities: Set<PropertyAmenity> = emptySet(),
    
    @OneToMany(mappedBy = "property", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val rooms: List<Room> = emptyList(),
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PropertyStatus = PropertyStatus.ACTIVE,
    
    @Column(nullable = false)
    val isPublished: Boolean = false,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now()
) {
    fun getTotalRooms(): Int = rooms.size
    
    fun getAvailableRooms(): List<Room> = rooms.filter { it.isAvailable() }
    
    fun getRoomsByType(roomType: RoomType): List<Room> = rooms.filter { it.type == roomType }
    
    fun getLowestRate(): BigDecimal? = rooms.minOfOrNull { it.baseRate }
    
    fun getHighestRate(): BigDecimal? = rooms.maxOfOrNull { it.baseRate }
    
    fun isAcceptingReservations(): Boolean = 
        status == PropertyStatus.ACTIVE && isPublished
        
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Property) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class CheckInOutInfo(
    @Column(nullable = false)
    val checkInTime: LocalTime = LocalTime.of(15, 0),
    
    @Column(nullable = false)  
    val checkOutTime: LocalTime = LocalTime.of(11, 0),
    
    @Column(nullable = false)
    val lateCheckInAllowed: Boolean = true,
    
    @Column(nullable = false)
    val earlyCheckInAllowed: Boolean = true,
    
    @Column(nullable = false)
    val lateCheckOutAllowed: Boolean = true
)

@Embeddable
data class PropertyPolicies(
    @Column(nullable = false)
    val cancellationPolicy: String = "24시간 전 무료 취소",
    
    @Column(nullable = false)
    val petPolicy: String = "반려동물 동반 불가",
    
    @Column(nullable = false)
    val smokingPolicy: String = "전 객실 금연",
    
    @Column(nullable = false)
    val childPolicy: String = "만 18세 미만 투숙 불가",
    
    @Column(nullable = false)
    val extraBedPolicy: String = "추가 침대 요청 가능",
    
    @Column(nullable = false)
    val ageRestriction: Int = 18
)

enum class PropertyType {
    HOTEL, RESORT, MOTEL, PENSION, GUESTHOUSE, HOSTEL, APARTMENT, VILLA, CAMPING
}

enum class PropertyCategory {
    LUXURY, PREMIUM, STANDARD, BUDGET, BUSINESS, BOUTIQUE, FAMILY, ROMANTIC
}

enum class PropertyAmenity {
    WIFI, PARKING, POOL, GYM, SPA, RESTAURANT, BAR, ROOM_SERVICE, 
    CONCIERGE, BUSINESS_CENTER, MEETING_ROOM, AIRPORT_SHUTTLE,
    LAUNDRY, DRY_CLEANING, SAFE_DEPOSIT_BOX, CURRENCY_EXCHANGE,
    ELEVATOR, WHEELCHAIR_ACCESS, PET_FRIENDLY, NON_SMOKING,
    AIR_CONDITIONING, HEATING, BALCONY, GARDEN, BEACH_ACCESS
}

enum class PropertyStatus {
    ACTIVE, INACTIVE, MAINTENANCE, CLOSED_TEMPORARILY, CLOSED_PERMANENTLY
}