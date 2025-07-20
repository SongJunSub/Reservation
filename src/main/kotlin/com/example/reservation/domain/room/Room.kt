package com.example.reservation.domain.room

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "rooms")
@EntityListeners(AuditingEntityListener::class)
data class Room(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    val property: Property,
    
    @Column(nullable = false, length = 50)
    val roomNumber: String,
    
    @Column(nullable = false, length = 100)
    val name: String,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: RoomType,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val bedType: BedType,
    
    @Column(nullable = false)
    val maxOccupancy: Int,
    
    @Column(nullable = false)
    val standardOccupancy: Int,
    
    @Column(nullable = false, precision = 10, scale = 2)
    val baseRate: BigDecimal,
    
    @Column(nullable = false)
    val size: Double, // 평방미터
    
    @Column(nullable = false)
    val floor: Int,
    
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "room_amenities", joinColumns = [JoinColumn(name = "room_id")])
    @Column(name = "amenity")
    val amenities: Set<RoomAmenity> = emptySet(),
    
    @ElementCollection
    @CollectionTable(name = "room_views", joinColumns = [JoinColumn(name = "room_id")])
    @Column(name = "view_type")
    val views: Set<String> = emptySet(),
    
    @Embedded
    val roomFeatures: RoomFeatures = RoomFeatures(),
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RoomStatus = RoomStatus.AVAILABLE,
    
    @Column(length = 500)
    val description: String? = null,
    
    @Column(length = 1000)
    val specialInstructions: String? = null,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isAvailable(): Boolean = status == RoomStatus.AVAILABLE
    
    fun canAccommodate(guestCount: Int): Boolean = guestCount <= maxOccupancy
    
    fun getRecommendedRate(occupancy: Int): BigDecimal {
        return if (occupancy > standardOccupancy) {
            baseRate.multiply(BigDecimal.valueOf(1.2)) // 20% 추가 요금
        } else {
            baseRate
        }
    }
    
    fun hasAmenity(amenity: RoomAmenity): Boolean = amenities.contains(amenity)
    
    fun isAccessible(): Boolean = amenities.contains(RoomAmenity.WHEELCHAIR_ACCESS)
    
    fun isSmokingAllowed(): Boolean = !amenities.contains(RoomAmenity.NON_SMOKING)
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Room) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class RoomFeatures(
    @Column(nullable = false)
    val hasBalcony: Boolean = false,
    
    @Column(nullable = false)
    val hasKitchen: Boolean = false,
    
    @Column(nullable = false)
    val hasLivingRoom: Boolean = false,
    
    @Column(nullable = false)
    val hasBathtub: Boolean = false,
    
    @Column(nullable = false)
    val hasShower: Boolean = true,
    
    @Column(nullable = false)
    val numberOfBathrooms: Int = 1,
    
    @Column(nullable = false)
    val hasDiningArea: Boolean = false,
    
    @Column(nullable = false)
    val hasWorkDesk: Boolean = true,
    
    @Column(nullable = false)
    val hasSeatingArea: Boolean = false
)

enum class RoomType {
    STANDARD, DELUXE, SUITE, JUNIOR_SUITE, PRESIDENTIAL_SUITE,
    FAMILY, CONNECTING, STUDIO, PENTHOUSE, VILLA
}

enum class BedType {
    SINGLE, TWIN, DOUBLE, QUEEN, KING, SOFA_BED, BUNK_BED
}

enum class RoomAmenity {
    AIR_CONDITIONING, HEATING, WIFI, TV, CABLE_TV, SATELLITE_TV,
    MINIBAR, COFFEE_MAKER, TEA_MAKER, REFRIGERATOR, MICROWAVE,
    SAFE, TELEPHONE, HAIR_DRYER, BATHROBE, SLIPPERS, IRON,
    IRONING_BOARD, WAKE_UP_SERVICE, ROOM_SERVICE, HOUSEKEEPING,
    BALCONY, TERRACE, GARDEN_VIEW, SEA_VIEW, MOUNTAIN_VIEW,
    CITY_VIEW, POOL_VIEW, WHEELCHAIR_ACCESS, NON_SMOKING,
    SOUNDPROOF, BLACKOUT_CURTAINS, EXTRA_BED_AVAILABLE
}

enum class RoomStatus {
    AVAILABLE, OCCUPIED, OUT_OF_ORDER, MAINTENANCE, CLEANING, RESERVED
}