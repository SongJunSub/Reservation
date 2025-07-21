package com.example.reservation.application.usecase.reservation

import com.example.reservation.domain.reservation.Reservation
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 생성 유스케이스
 * 실무 릴리즈 급 구현: 완전한 비즈니스 규칙 검증 및 에러 처리
 */
interface CreateReservationUseCase {
    
    /**
     * 새로운 예약을 생성합니다.
     * 
     * @param command 예약 생성 명령
     * @return 생성된 예약 정보
     * @throws ReservationBusinessException 비즈니스 규칙 위반시
     * @throws ReservationValidationException 입력값 검증 실패시
     */
    fun execute(command: CreateReservationCommand): Mono<ReservationResponse>
    
    /**
     * 예약 가능성을 미리 검증합니다.
     * 
     * @param command 예약 생성 명령
     * @return 검증 결과
     */
    fun validateReservation(command: CreateReservationCommand): Mono<ReservationValidationResult>
}

/**
 * 예약 생성 명령
 */
data class CreateReservationCommand(
    val guestId: UUID,
    val propertyId: UUID,
    val roomTypeId: UUID,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val infantCount: Int = 0,
    val totalAmount: BigDecimal,
    val paymentMethodId: String? = null,
    val specialRequests: String? = null,
    val ratePlanCode: String? = null,
    val promotionCode: String? = null,
    val marketingOptIn: Boolean = false,
    val communicationPreferences: Set<String> = emptySet(),
    val additionalGuests: List<AdditionalGuestInfo> = emptyList(),
    val preferences: ReservationPreferencesInfo? = null
)

/**
 * 추가 게스트 정보
 */
data class AdditionalGuestInfo(
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val relationship: String? = null
)

/**
 * 예약 선호사항 정보
 */
data class ReservationPreferencesInfo(
    val roomLocation: String? = null,
    val floorPreference: String? = null,
    val bedType: String? = null,
    val smokingPreference: String? = null,
    val accessibilityNeeds: Set<String> = emptySet(),
    val dietaryRestrictions: Set<String> = emptySet(),
    val specialOccasion: String? = null
)

/**
 * 예약 응답 정보
 */
data class ReservationResponse(
    val reservationId: UUID,
    val confirmationNumber: String,
    val status: String,
    val guestId: UUID,
    val propertyId: UUID,
    val roomTypeId: UUID,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val totalAmount: BigDecimal,
    val createdAt: LocalDateTime,
    val estimatedCheckInTime: LocalDateTime? = null,
    val estimatedCheckOutTime: LocalDateTime? = null,
    val cancellationDeadline: LocalDateTime? = null,
    val refundableAmount: BigDecimal? = null
)

/**
 * 예약 검증 결과
 */
data class ReservationValidationResult(
    val isValid: Boolean,
    val availableRooms: Int,
    val totalAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceCharges: BigDecimal,
    val cancellationPolicy: String,
    val validationErrors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val recommendations: List<String> = emptyList()
)

/**
 * 검증 오류
 */
data class ValidationError(
    val field: String,
    val code: String,
    val message: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
)

/**
 * 검증 경고
 */
data class ValidationWarning(
    val code: String,
    val message: String,
    val suggestion: String? = null
)

/**
 * 검증 심각도
 */
enum class ValidationSeverity {
    WARNING, ERROR, CRITICAL
}