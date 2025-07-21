package com.example.reservation.presentation.dto.request

import com.example.reservation.application.usecase.reservation.*
import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * 예약 생성 요청 DTO
 * 실무 릴리즈 급 구현: Bean Validation, Swagger 문서화, 보안 고려
 */
@Schema(description = "예약 생성 요청")
data class CreateReservationRequest(
    
    @field:NotNull(message = "고객 ID는 필수입니다")
    @Schema(description = "고객 ID", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    val guestId: UUID,
    
    @field:NotNull(message = "시설 ID는 필수입니다")
    @Schema(description = "시설 ID", example = "550e8400-e29b-41d4-a716-446655440001", required = true)
    val propertyId: UUID,
    
    @field:NotNull(message = "객실 유형 ID는 필수입니다")
    @Schema(description = "객실 유형 ID", example = "550e8400-e29b-41d4-a716-446655440002", required = true)
    val roomTypeId: UUID,
    
    @field:NotNull(message = "체크인 날짜는 필수입니다")
    @field:Future(message = "체크인 날짜는 현재 날짜 이후여야 합니다")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크인 날짜", example = "2025-12-25", required = true)
    val checkInDate: LocalDate,
    
    @field:NotNull(message = "체크아웃 날짜는 필수입니다")
    @field:Future(message = "체크아웃 날짜는 현재 날짜 이후여야 합니다")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크아웃 날짜", example = "2025-12-27", required = true)
    val checkOutDate: LocalDate,
    
    @field:Min(value = 1, message = "성인 수는 최소 1명이어야 합니다")
    @field:Max(value = 10, message = "성인 수는 최대 10명까지 가능합니다")
    @Schema(description = "성인 수", example = "2", required = true, minimum = "1", maximum = "10")
    val adultCount: Int = 1,
    
    @field:Min(value = 0, message = "아동 수는 0 이상이어야 합니다")
    @field:Max(value = 8, message = "아동 수는 최대 8명까지 가능합니다")
    @Schema(description = "아동 수 (2-12세)", example = "1", minimum = "0", maximum = "8")
    val childCount: Int = 0,
    
    @field:Min(value = 0, message = "유아 수는 0 이상이어야 합니다")
    @field:Max(value = 4, message = "유아 수는 최대 4명까지 가능합니다")
    @Schema(description = "유아 수 (0-2세)", example = "0", minimum = "0", maximum = "4")
    val infantCount: Int = 0,
    
    @field:NotNull(message = "총 금액은 필수입니다")
    @field:DecimalMin(value = "0.01", message = "총 금액은 0보다 커야 합니다")
    @field:Digits(integer = 10, fraction = 2, message = "금액 형식이 올바르지 않습니다")
    @Schema(description = "총 예약 금액", example = "250000.00", required = true)
    val totalAmount: BigDecimal,
    
    @field:Size(max = 100, message = "결제 수단 ID는 100자를 초과할 수 없습니다")
    @Schema(description = "결제 수단 ID", example = "CARD_VISA_1234")
    val paymentMethodId: String? = null,
    
    @field:Size(max = 1000, message = "특별 요청사항은 1000자를 초과할 수 없습니다")
    @Schema(description = "특별 요청사항", example = "12층 이상 객실 희망, 금연실 요청")
    val specialRequests: String? = null,
    
    @field:Size(max = 50, message = "요금 플랜 코드는 50자를 초과할 수 없습니다")
    @Schema(description = "요금 플랜 코드", example = "ADVANCE_PURCHASE_30")
    val ratePlanCode: String? = null,
    
    @field:Size(max = 50, message = "프로모션 코드는 50자를 초과할 수 없습니다")
    @Schema(description = "프로모션 코드", example = "SUMMER2025")
    val promotionCode: String? = null,
    
    @Schema(description = "마케팅 수신 동의", example = "true")
    val marketingOptIn: Boolean = false,
    
    @field:Size(max = 10, message = "커뮤니케이션 선호사항은 최대 10개까지 가능합니다")
    @Schema(description = "커뮤니케이션 선호사항", example = "[\"EMAIL\", \"SMS\"]")
    val communicationPreferences: Set<String> = emptySet(),
    
    @field:Valid
    @field:Size(max = 10, message = "추가 게스트는 최대 10명까지 가능합니다")
    @Schema(description = "추가 게스트 정보")
    val additionalGuests: List<AdditionalGuestRequest> = emptyList(),
    
    @field:Valid
    @Schema(description = "예약 선호사항")
    val preferences: ReservationPreferencesRequest? = null
) {
    
    init {
        require(checkOutDate.isAfter(checkInDate)) { 
            "체크아웃 날짜는 체크인 날짜보다 늦어야 합니다" 
        }
        require(adultCount + childCount + infantCount <= 12) {
            "총 인원수는 12명을 초과할 수 없습니다"
        }
        require(java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate) <= 365) {
            "최대 숙박 기간은 365일입니다"
        }
    }
    
    /**
     * UseCase Command로 변환
     */
    fun toCommand(): CreateReservationCommand {
        return CreateReservationCommand(
            guestId = guestId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate,
            adultCount = adultCount,
            childCount = childCount,
            infantCount = infantCount,
            totalAmount = totalAmount,
            paymentMethodId = paymentMethodId,
            specialRequests = specialRequests,
            ratePlanCode = ratePlanCode,
            promotionCode = promotionCode,
            marketingOptIn = marketingOptIn,
            communicationPreferences = communicationPreferences,
            additionalGuests = additionalGuests.map { it.toInfo() },
            preferences = preferences?.toInfo()
        )
    }
}

/**
 * 예약 수정 요청 DTO
 */
@Schema(description = "예약 수정 요청")
data class UpdateReservationRequest(
    
    @field:Future(message = "체크인 날짜는 현재 날짜 이후여야 합니다")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "새로운 체크인 날짜", example = "2025-12-26")
    val checkInDate: LocalDate? = null,
    
    @field:Future(message = "체크아웃 날짜는 현재 날짜 이후여야 합니다")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "새로운 체크아웃 날짜", example = "2025-12-28")
    val checkOutDate: LocalDate? = null,
    
    @field:Min(value = 1, message = "성인 수는 최소 1명이어야 합니다")
    @field:Max(value = 10, message = "성인 수는 최대 10명까지 가능합니다")
    @Schema(description = "성인 수", example = "3")
    val adultCount: Int? = null,
    
    @field:Min(value = 0, message = "아동 수는 0 이상이어야 합니다")
    @field:Max(value = 8, message = "아동 수는 최대 8명까지 가능합니다")
    @Schema(description = "아동 수", example = "2")
    val childCount: Int? = null,
    
    @field:Min(value = 0, message = "유아 수는 0 이상이어야 합니다")
    @field:Max(value = 4, message = "유아 수는 최대 4명까지 가능합니다")
    @Schema(description = "유아 수", example = "1")
    val infantCount: Int? = null,
    
    @Schema(description = "새로운 객실 유형 ID", example = "550e8400-e29b-41d4-a716-446655440003")
    val roomTypeId: UUID? = null,
    
    @field:Size(max = 1000, message = "특별 요청사항은 1000자를 초과할 수 없습니다")
    @Schema(description = "특별 요청사항", example = "발코니가 있는 객실로 변경 희망")
    val specialRequests: String? = null,
    
    @field:Valid
    @field:Size(max = 10, message = "추가 게스트는 최대 10명까지 가능합니다")
    @Schema(description = "추가 게스트 정보")
    val additionalGuests: List<AdditionalGuestRequest>? = null,
    
    @field:Valid
    @Schema(description = "예약 선호사항")
    val preferences: ReservationPreferencesRequest? = null,
    
    @field:NotBlank(message = "수정 사유는 필수입니다")
    @field:Size(max = 500, message = "수정 사유는 500자를 초과할 수 없습니다")
    @Schema(description = "수정 사유", example = "고객 요청에 의한 일정 변경", required = true)
    val updateReason: String,
    
    @Schema(description = "수정자 ID (관리자인 경우)", example = "550e8400-e29b-41d4-a716-446655440004")
    val updatedBy: UUID? = null
) {
    
    init {
        if (checkInDate != null && checkOutDate != null) {
            require(checkOutDate.isAfter(checkInDate)) { 
                "체크아웃 날짜는 체크인 날짜보다 늦어야 합니다" 
            }
        }
    }
    
    /**
     * UseCase Command로 변환
     */
    fun toCommand(reservationId: UUID): UpdateReservationCommand {
        return UpdateReservationCommand(
            reservationId = reservationId,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate,
            adultCount = adultCount,
            childCount = childCount,
            infantCount = infantCount,
            roomTypeId = roomTypeId,
            specialRequests = specialRequests,
            additionalGuests = additionalGuests?.map { it.toInfo() },
            preferences = preferences?.toInfo(),
            updateReason = updateReason,
            updatedBy = updatedBy
        )
    }
}

/**
 * 예약 취소 요청 DTO
 */
@Schema(description = "예약 취소 요청")
data class CancelReservationRequest(
    
    @field:NotNull(message = "취소 사유는 필수입니다")
    @Schema(description = "취소 사유", example = "GUEST_REQUEST", required = true, allowableValues = ["GUEST_REQUEST", "PROPERTY_MAINTENANCE", "OVERBOOKING", "WEATHER", "EMERGENCY", "FORCE_MAJEURE", "PAYMENT_FAILURE", "POLICY_VIOLATION", "ADMIN_DECISION", "SYSTEM_ERROR"])
    val reason: String,
    
    @field:Size(max = 1000, message = "취소 상세 사유는 1000자를 초과할 수 없습니다")
    @Schema(description = "취소 상세 사유", example = "가족 사정으로 인한 여행 취소")
    val reasonDetails: String? = null,
    
    @Schema(description = "취소 요청자 ID", example = "550e8400-e29b-41d4-a716-446655440005")
    val requestedBy: UUID? = null,
    
    @Schema(description = "관리자 권한으로 정책 무시 여부", example = "false")
    val adminOverride: Boolean = false,
    
    @Schema(description = "고객에게 알림 발송 여부", example = "true")
    val notifyGuest: Boolean = true,
    
    @Schema(description = "환불 처리 여부", example = "true")
    val processRefund: Boolean = true
) {
    
    /**
     * UseCase Command로 변환
     */
    fun toCommand(reservationId: UUID): CancelReservationCommand {
        return CancelReservationCommand(
            reservationId = reservationId,
            cancellationReason = CancellationReason.valueOf(reason),
            reasonDetails = reasonDetails,
            requestedBy = requestedBy,
            adminOverride = adminOverride,
            notifyGuest = notifyGuest,
            processRefund = processRefund
        )
    }
}

/**
 * 환불 계산 요청 DTO
 */
@Schema(description = "환불 계산 요청")
data class CalculateRefundRequest(
    
    @field:NotBlank(message = "취소 사유는 필수입니다")
    @Schema(description = "취소 사유", example = "GUEST_REQUEST", required = true)
    val reason: String
)

/**
 * 추가 게스트 요청 DTO
 */
@Schema(description = "추가 게스트 정보")
data class AdditionalGuestRequest(
    
    @field:NotBlank(message = "이름은 필수입니다")
    @field:Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    @Schema(description = "이름", example = "김철수", required = true)
    val firstName: String,
    
    @field:NotBlank(message = "성은 필수입니다")
    @field:Size(max = 50, message = "성은 50자를 초과할 수 없습니다")
    @Schema(description = "성", example = "김", required = true)
    val lastName: String,
    
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    @field:Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다")
    @Schema(description = "이메일", example = "kim.chulsoo@example.com")
    val email: String? = null,
    
    @field:Pattern(regexp = "^[0-9+-]{10,20}$", message = "올바른 전화번호 형식이 아닙니다")
    @Schema(description = "전화번호", example = "+82-10-1234-5678")
    val phoneNumber: String? = null,
    
    @field:Size(max = 50, message = "관계는 50자를 초과할 수 없습니다")
    @Schema(description = "주 고객과의 관계", example = "배우자")
    val relationship: String? = null
) {
    
    /**
     * UseCase DTO로 변환
     */
    fun toInfo(): AdditionalGuestInfo {
        return AdditionalGuestInfo(
            firstName = firstName,
            lastName = lastName,
            email = email,
            phoneNumber = phoneNumber,
            relationship = relationship
        )
    }
}

/**
 * 예약 선호사항 요청 DTO
 */
@Schema(description = "예약 선호사항")
data class ReservationPreferencesRequest(
    
    @field:Size(max = 50, message = "객실 위치는 50자를 초과할 수 없습니다")
    @Schema(description = "선호하는 객실 위치", example = "OCEAN_VIEW")
    val roomLocation: String? = null,
    
    @field:Size(max = 20, message = "층수 선호사항은 20자를 초과할 수 없습니다")
    @Schema(description = "선호하는 층수", example = "HIGH_FLOOR")
    val floorPreference: String? = null,
    
    @field:Size(max = 20, message = "침대 유형은 20자를 초과할 수 없습니다")
    @Schema(description = "선호하는 침대 유형", example = "KING")
    val bedType: String? = null,
    
    @Schema(description = "흡연 선호사항", example = "NON_SMOKING", allowableValues = ["SMOKING", "NON_SMOKING"])
    val smokingPreference: String = "NON_SMOKING",
    
    @field:Size(max = 10, message = "접근성 요구사항은 최대 10개까지 가능합니다")
    @Schema(description = "접근성 요구사항", example = "[\"WHEELCHAIR_ACCESSIBLE\", \"HEARING_ACCESSIBLE\"]")
    val accessibilityNeeds: Set<String> = emptySet(),
    
    @field:Size(max = 10, message = "식이 제한사항은 최대 10개까지 가능합니다")
    @Schema(description = "식이 제한사항", example = "[\"VEGETARIAN\", \"NUT_FREE\"]")
    val dietaryRestrictions: Set<String> = emptySet(),
    
    @field:Size(max = 100, message = "특별한 날은 100자를 초과할 수 없습니다")
    @Schema(description = "특별한 날 (기념일 등)", example = "결혼기념일")
    val specialOccasion: String? = null
) {
    
    /**
     * UseCase DTO로 변환
     */
    fun toInfo(): ReservationPreferencesInfo {
        return ReservationPreferencesInfo(
            roomLocation = roomLocation,
            floorPreference = floorPreference,
            bedType = bedType,
            smokingPreference = smokingPreference,
            accessibilityNeeds = accessibilityNeeds,
            dietaryRestrictions = dietaryRestrictions,
            specialOccasion = specialOccasion
        )
    }
}