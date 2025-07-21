package com.example.reservation.application.usecase.room

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 객실 가용성 관리 유스케이스
 * 실무 릴리즈 급 구현: 동적 요금, 재고 관리, 실시간 업데이트
 */
interface RoomAvailabilityUseCase {
    
    /**
     * 객실 가용성을 조회합니다.
     * 
     * @param query 가용성 조회 쿼리
     * @return 가용성 정보
     */
    fun checkAvailability(query: AvailabilityQuery): Flux<RoomAvailabilityResponse>
    
    /**
     * 특정 객실 유형의 가용성을 조회합니다.
     * 
     * @param propertyId 시설 ID
     * @param roomTypeId 객실 유형 ID
     * @param dateRange 날짜 범위
     * @return 상세 가용성 정보
     */
    fun getRoomTypeAvailability(
        propertyId: UUID,
        roomTypeId: UUID,
        dateRange: DateRange
    ): Mono<RoomTypeAvailabilityDetail>
    
    /**
     * 객실 재고를 업데이트합니다.
     * 
     * @param command 재고 업데이트 명령
     * @return 업데이트 결과
     */
    fun updateInventory(command: UpdateInventoryCommand): Mono<InventoryUpdateResult>
    
    /**
     * 요금을 업데이트합니다.
     * 
     * @param command 요금 업데이트 명령
     * @return 업데이트 결과
     */
    fun updateRates(command: UpdateRatesCommand): Mono<RateUpdateResult>
    
    /**
     * 가용성 캘린더를 조회합니다.
     * 
     * @param propertyId 시설 ID
     * @param roomTypeId 객실 유형 ID (선택사항)
     * @param dateRange 날짜 범위
     * @return 캘린더 정보
     */
    fun getAvailabilityCalendar(
        propertyId: UUID,
        roomTypeId: UUID? = null,
        dateRange: DateRange
    ): Mono<AvailabilityCalendar>
}

/**
 * 가용성 조회 쿼리
 */
data class AvailabilityQuery(
    val propertyId: UUID? = null,
    val roomTypeIds: Set<UUID> = emptySet(),
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val infantCount: Int = 0,
    val ratePlanCode: String? = null,
    val promotionCode: String? = null,
    val includeRestrictions: Boolean = true,
    val includePolicies: Boolean = true,
    val currency: String = "KRW",
    val guestId: UUID? = null, // 고객별 맞춤 요금을 위해
    val channel: String? = null // 채널별 요금을 위해
)

/**
 * 날짜 범위
 */
data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate
) {
    init {
        require(!startDate.isAfter(endDate)) { "시작일은 종료일보다 이후일 수 없습니다." }
    }
}

/**
 * 재고 업데이트 명령
 */
data class UpdateInventoryCommand(
    val propertyId: UUID,
    val roomTypeId: UUID,
    val dateRange: DateRange,
    val availableRooms: Int,
    val updateType: InventoryUpdateType,
    val reason: String? = null,
    val updatedBy: UUID? = null
)

/**
 * 재고 업데이트 유형
 */
enum class InventoryUpdateType {
    SET,        // 절대값 설정
    INCREMENT,  // 증가
    DECREMENT,  // 감소
    BLOCK,      // 차단
    RELEASE     // 해제
}

/**
 * 요금 업데이트 명령
 */
data class UpdateRatesCommand(
    val propertyId: UUID,
    val roomTypeId: UUID,
    val ratePlanId: UUID,
    val dateRange: DateRange,
    val rates: List<DailyRate>,
    val updateType: RateUpdateType,
    val reason: String? = null,
    val updatedBy: UUID? = null
)

/**
 * 요금 업데이트 유형
 */
enum class RateUpdateType {
    SET,           // 절대값 설정
    PERCENTAGE,    // 퍼센트 조정
    AMOUNT_ADJUST, // 금액 조정
    BULK_UPDATE    // 일괄 업데이트
}

/**
 * 일별 요금
 */
data class DailyRate(
    val date: LocalDate,
    val baseRate: BigDecimal,
    val taxes: BigDecimal = BigDecimal.ZERO,
    val serviceCharges: BigDecimal = BigDecimal.ZERO,
    val currency: String = "KRW",
    val minimumStay: Int = 1,
    val maximumStay: Int = 365,
    val closedToArrival: Boolean = false,
    val closedToDeparture: Boolean = false,
    val stopSell: Boolean = false
)

/**
 * 객실 가용성 응답
 */
data class RoomAvailabilityResponse(
    val propertyId: UUID,
    val propertyName: String,
    val roomTypeId: UUID,
    val roomTypeName: String,
    val availableRooms: Int,
    val totalRooms: Int,
    val occupancyRate: Double,
    val rates: List<RateInfo>,
    val restrictions: RoomRestrictions,
    val policies: RoomPolicies,
    val amenities: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val lastUpdated: LocalDateTime
)

/**
 * 요금 정보
 */
data class RateInfo(
    val ratePlanId: UUID,
    val ratePlanName: String,
    val ratePlanType: String, // STANDARD, ADVANCE_PURCHASE, LAST_MINUTE, PACKAGE
    val totalRate: BigDecimal,
    val averageNightlyRate: BigDecimal,
    val breakdown: RateBreakdown,
    val currency: String = "KRW",
    val isRefundable: Boolean = true,
    val cancellationDeadline: LocalDateTime? = null,
    val promotions: List<PromotionInfo> = emptyList()
)

/**
 * 요금 상세 내역
 */
data class RateBreakdown(
    val baseRate: BigDecimal,
    val taxes: BigDecimal,
    val serviceCharges: BigDecimal,
    val discounts: BigDecimal = BigDecimal.ZERO,
    val promotionDiscount: BigDecimal = BigDecimal.ZERO,
    val memberDiscount: BigDecimal = BigDecimal.ZERO,
    val totalBeforeTax: BigDecimal,
    val totalAfterTax: BigDecimal,
    val dailyRates: List<DailyRateDetail> = emptyList()
)

/**
 * 일별 요금 상세
 */
data class DailyRateDetail(
    val date: LocalDate,
    val baseRate: BigDecimal,
    val adjustments: BigDecimal = BigDecimal.ZERO,
    val finalRate: BigDecimal,
    val dayOfWeek: String
)

/**
 * 프로모션 정보
 */
data class PromotionInfo(
    val promotionId: UUID,
    val name: String,
    val description: String,
    val discountType: String, // PERCENTAGE, FIXED_AMOUNT, FREE_NIGHTS
    val discountValue: BigDecimal,
    val applicableNights: Set<LocalDate> = emptySet()
)

/**
 * 객실 제한사항
 */
data class RoomRestrictions(
    val minimumStay: Int = 1,
    val maximumStay: Int = 365,
    val minimumAdvanceBooking: Int = 0, // 최소 사전 예약 일수
    val maximumAdvanceBooking: Int = 365, // 최대 사전 예약 일수
    val closedToArrival: Set<LocalDate> = emptySet(),
    val closedToDeparture: Set<LocalDate> = emptySet(),
    val stopSell: Set<LocalDate> = emptySet(),
    val restrictedDays: Set<String> = emptySet() // MONDAY, TUESDAY, etc.
)

/**
 * 객실 정책
 */
data class RoomPolicies(
    val checkInTime: String = "15:00",
    val checkOutTime: String = "11:00",
    val cancellationPolicy: String,
    val childPolicy: String,
    val petPolicy: String? = null,
    val smokingPolicy: String,
    val extraBedPolicy: String? = null,
    val incidentalPolicy: String? = null
)

/**
 * 객실 유형 가용성 상세
 */
data class RoomTypeAvailabilityDetail(
    val roomTypeId: UUID,
    val roomTypeName: String,
    val description: String,
    val maxOccupancy: Int,
    val totalInventory: Int,
    val dailyAvailability: List<DailyAvailability>,
    val averageOccupancy: Double,
    val peakDemandDates: List<LocalDate>,
    val lowDemandDates: List<LocalDate>
)

/**
 * 일별 가용성
 */
data class DailyAvailability(
    val date: LocalDate,
    val totalRooms: Int,
    val availableRooms: Int,
    val bookedRooms: Int,
    val blockedRooms: Int,
    val maintenanceRooms: Int,
    val outOfOrderRooms: Int,
    val occupancyRate: Double,
    val rates: List<RateInfo> = emptyList()
)

/**
 * 재고 업데이트 결과
 */
data class InventoryUpdateResult(
    val success: Boolean,
    val updatedDates: List<LocalDate>,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val affectedReservations: Int = 0
)

/**
 * 요금 업데이트 결과
 */
data class RateUpdateResult(
    val success: Boolean,
    val updatedDates: List<LocalDate>,
    val previousRates: Map<LocalDate, BigDecimal> = emptyMap(),
    val newRates: Map<LocalDate, BigDecimal> = emptyMap(),
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * 가용성 캘린더
 */
data class AvailabilityCalendar(
    val propertyId: UUID,
    val roomTypeId: UUID? = null,
    val dateRange: DateRange,
    val calendar: List<CalendarDay>,
    val summary: CalendarSummary
)

/**
 * 캘린더 일
 */
data class CalendarDay(
    val date: LocalDate,
    val dayOfWeek: String,
    val isWeekend: Boolean,
    val availability: DailyAvailability,
    val specialEvents: List<String> = emptyList(),
    val notes: String? = null
)

/**
 * 캘린더 요약
 */
data class CalendarSummary(
    val totalDays: Int,
    val availableDays: Int,
    val soldOutDays: Int,
    val restrictedDays: Int,
    val averageOccupancy: Double,
    val averageRate: BigDecimal,
    val revenueOpportunity: BigDecimal
)