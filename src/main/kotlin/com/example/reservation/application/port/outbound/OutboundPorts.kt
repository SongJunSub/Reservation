package com.example.reservation.application.port.outbound

import com.example.reservation.application.usecase.guest.GuestResponse
import com.example.reservation.application.usecase.payment.PaymentResponse
import com.example.reservation.application.usecase.reservation.*
import com.example.reservation.application.service.Reservation
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.*

/**
 * 고객 포트 (아웃바운드)
 */
interface GuestPort {
    fun findById(id: UUID): Mono<Guest>
    fun existsById(id: UUID): Mono<Boolean>
}

/**
 * 객실/시설 포트 (아웃바운드)
 */
interface RoomPort {
    fun findPropertyById(id: UUID): Mono<Property>
    fun findRoomTypeById(id: UUID): Mono<RoomType>
    fun checkAvailability(
        propertyId: UUID,
        roomTypeId: UUID,
        checkInDate: LocalDate,
        checkOutDate: LocalDate
    ): Mono<RoomAvailability>
}

/**
 * 결제 포트 (아웃바운드)
 */
interface PaymentPort {
    fun findById(id: UUID): Mono<PaymentSummary>
    fun processPayment(request: PaymentRequest): Mono<PaymentResponse>
    fun processRefund(request: RefundRequest): Mono<RefundResponse>
}

/**
 * 알림 포트 (아웃바운드)
 */
interface NotificationPort {
    fun sendReservationConfirmation(reservation: Reservation): Mono<Void>
    fun sendReservationUpdateNotification(reservation: Reservation): Mono<Void>
    fun sendCancellationNotification(reservation: Reservation, response: CancellationResponse): Mono<Void>
    fun sendPaymentNotification(reservation: Reservation, payment: PaymentResponse): Mono<Void>
}

/**
 * 감사/로깅 포트 (아웃바운드)
 */
interface AuditPort {
    fun recordReservationCreation(reservation: Reservation, command: CreateReservationCommand): Mono<Void>
    fun recordReservationUpdate(original: Reservation, updated: Reservation, command: UpdateReservationCommand): Mono<Void>
    fun recordReservationCancellation(reservation: Reservation, command: CancelReservationCommand, response: CancellationResponse): Mono<Void>
    fun getReservationHistory(reservationId: UUID): Mono<List<ReservationHistoryItem>>
    fun getReservationTimeline(reservationId: UUID): Mono<List<ReservationTimelineEvent>>
}

/**
 * 외부 서비스 포트 (아웃바운드)
 */
interface ExternalServicePort {
    fun validateCreditCard(cardInfo: CardInfo): Mono<CardValidationResult>
    fun sendSMS(phoneNumber: String, message: String): Mono<SMSResult>
    fun sendEmail(emailAddress: String, subject: String, content: String): Mono<EmailResult>
    fun checkFraud(transactionInfo: TransactionInfo): Mono<FraudCheckResult>
}

/**
 * 캐시 포트 (아웃바운드)
 */
interface CachePort {
    fun get(key: String): Mono<String>
    fun set(key: String, value: String, ttlSeconds: Long): Mono<Void>
    fun delete(key: String): Mono<Void>
    fun exists(key: String): Mono<Boolean>
}

/**
 * 이벤트 발행 포트 (아웃바운드)
 */
interface EventPublisherPort {
    fun publishReservationCreated(event: ReservationCreatedEvent): Mono<Void>
    fun publishReservationUpdated(event: ReservationUpdatedEvent): Mono<Void>
    fun publishReservationCancelled(event: ReservationCancelledEvent): Mono<Void>
    fun publishPaymentProcessed(event: PaymentProcessedEvent): Mono<Void>
}

// === 데이터 클래스들 ===

/**
 * 임시 도메인 객체들 (실제로는 domain 패키지에 정의)
 */
data class Guest(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val status: String
) {
    fun toSummary(): GuestSummary = GuestSummary(
        guestId = id,
        firstName = firstName,
        lastName = lastName,
        email = email,
        loyaltyTier = "STANDARD",
        loyaltyPoints = 0,
        status = status,
        registrationDate = java.time.LocalDateTime.now(),
        totalStays = 0,
        totalSpent = 0.0,
        vipStatus = false
    )
}

data class Property(
    val id: UUID,
    val name: String,
    val address: String,
    val city: String,
    val country: String
) {
    fun toSummary(): PropertySummary = PropertySummary(
        propertyId = id,
        name = name,
        address = address,
        city = city,
        country = country,
        category = "HOTEL",
        rating = 4.5
    )
}

data class RoomType(
    val id: UUID,
    val name: String,
    val description: String,
    val maxOccupancy: Int
) {
    fun toSummary(): RoomTypeSummary = RoomTypeSummary(
        roomTypeId = id,
        name = name,
        description = description,
        maxOccupancy = maxOccupancy,
        bedType = "QUEEN",
        amenities = listOf("WiFi", "TV", "Air Conditioning")
    )
}

data class RoomAvailability(
    val availableRooms: Int,
    val totalRooms: Int,
    val occupancyRate: Double
)

data class PaymentRequest(
    val reservationId: UUID,
    val amount: java.math.BigDecimal,
    val currency: String,
    val paymentMethod: String
)

data class RefundRequest(
    val paymentId: UUID,
    val refundAmount: java.math.BigDecimal,
    val reason: String
)

data class CardInfo(
    val cardNumber: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val cvv: String,
    val holderName: String
)

data class CardValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

data class SMSResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

data class EmailResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

data class TransactionInfo(
    val amount: java.math.BigDecimal,
    val currency: String,
    val guestId: UUID,
    val ipAddress: String? = null,
    val deviceInfo: String? = null
)

data class FraudCheckResult(
    val riskScore: Double,
    val isHighRisk: Boolean,
    val reasons: List<String> = emptyList()
)

// === 이벤트 클래스들 ===

data class ReservationCreatedEvent(
    val reservationId: UUID,
    val guestId: UUID,
    val propertyId: UUID,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val totalAmount: java.math.BigDecimal,
    val createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)

data class ReservationUpdatedEvent(
    val reservationId: UUID,
    val guestId: UUID,
    val changes: Map<String, Any>,
    val updatedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)

data class ReservationCancelledEvent(
    val reservationId: UUID,
    val guestId: UUID,
    val cancellationReason: String,
    val refundAmount: java.math.BigDecimal,
    val cancelledAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)

data class PaymentProcessedEvent(
    val paymentId: UUID,
    val reservationId: UUID,
    val amount: java.math.BigDecimal,
    val status: String,
    val processedAt: java.time.LocalDateTime = java.time.LocalDateTime.now()
)