package com.example.reservation.domain.guest

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "guests")
@EntityListeners(AuditingEntityListener::class)
data class Guest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false, length = 100)
    val firstName: String,
    
    @Column(nullable = false, length = 100)  
    val lastName: String,
    
    @Column(unique = true, nullable = false, length = 320)
    val email: String,
    
    @Column(length = 20)
    val phoneNumber: String? = null,
    
    @Column(length = 10)
    val nationality: String? = null,
    
    @Column
    val dateOfBirth: LocalDate? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val gender: Gender = Gender.NOT_SPECIFIED,
    
    @Enumerated(EnumType.STRING) 
    @Column(nullable = false)
    val preferredLanguage: Language = Language.ENGLISH,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val loyaltyTier: LoyaltyTier = LoyaltyTier.STANDARD,
    
    @Column(nullable = false)
    val loyaltyPoints: Int = 0,
    
    @Embedded
    val address: Address? = null,
    
    @Embedded
    val preferences: GuestPreferences = GuestPreferences(),
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: GuestStatus = GuestStatus.ACTIVE,
    
    @Column(nullable = false)
    val isEmailVerified: Boolean = false,
    
    @Column(nullable = false)
    val isPhoneVerified: Boolean = false,
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @LastModifiedDate
    @Column(nullable = false)
    val lastModifiedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column
    val lastLoginAt: LocalDateTime? = null
) {
    fun getFullName(): String = "$firstName $lastName"
    
    fun canMakeReservation(): Boolean = status == GuestStatus.ACTIVE
    
    fun addLoyaltyPoints(points: Int): Guest {
        val newPoints = loyaltyPoints + points
        val newTier = LoyaltyTier.calculateTier(newPoints)
        return copy(loyaltyPoints = newPoints, loyaltyTier = newTier)
    }
    
    fun updateLastLogin(): Guest = copy(lastLoginAt = LocalDateTime.now())
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Guest) return false
        return id != 0L && id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class Address(
    @Column(length = 200)
    val street: String,
    
    @Column(length = 100)
    val city: String,
    
    @Column(length = 100)
    val state: String? = null,
    
    @Column(length = 20)
    val postalCode: String,
    
    @Column(length = 3)
    val countryCode: String
)

@Embeddable
data class GuestPreferences(
    @Column(nullable = false)
    val roomTypePreference: String = "",
    
    @Column(nullable = false)
    val floorPreference: String = "",
    
    @Column(nullable = false)
    val bedTypePreference: String = "",
    
    @Column(nullable = false)
    val smokingPreference: Boolean = false,
    
    @Column(nullable = false)
    val accessibilityNeeds: Boolean = false,
    
    @Column(nullable = false)
    val dietaryRestrictions: String = "",
    
    @Column(nullable = false)
    val specialRequests: String = "",
    
    @Column(nullable = false)
    val marketingOptIn: Boolean = false
)

enum class Gender {
    MALE, FEMALE, OTHER, NOT_SPECIFIED
}

enum class Language {
    ENGLISH, KOREAN, JAPANESE, CHINESE_SIMPLIFIED, CHINESE_TRADITIONAL, 
    SPANISH, FRENCH, GERMAN, ITALIAN, PORTUGUESE, RUSSIAN, ARABIC
}

enum class LoyaltyTier {
    STANDARD, SILVER, GOLD, PLATINUM, DIAMOND;
    
    companion object {
        fun calculateTier(points: Int): LoyaltyTier = when {
            points >= 50000 -> DIAMOND
            points >= 25000 -> PLATINUM
            points >= 10000 -> GOLD
            points >= 5000 -> SILVER
            else -> STANDARD
        }
    }
    
    fun getDiscountPercentage(): Double = when (this) {
        STANDARD -> 0.0
        SILVER -> 5.0
        GOLD -> 10.0
        PLATINUM -> 15.0
        DIAMOND -> 20.0
    }
}

enum class GuestStatus {
    ACTIVE, INACTIVE, SUSPENDED, BLACKLISTED
}