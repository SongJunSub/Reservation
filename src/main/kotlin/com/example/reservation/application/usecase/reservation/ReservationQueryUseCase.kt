package com.example.reservation.application.usecase.reservation

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 조회 유스케이스
 * 실무 릴리즈 급 구현: 페이징, 필터링, 정렬 지원
 */
interface ReservationQueryUseCase {
    
    /**
     * 예약 ID로 예약 정보를 조회합니다.
     * 
     * @param reservationId 예약 ID
     * @param includeHistory 변경 이력 포함 여부
     * @return 예약 상세 정보
     */
    fun findById(reservationId: UUID, includeHistory: Boolean = false): Mono<ReservationDetailResponse>
    
    /**
     * 확인번호로 예약 정보를 조회합니다.
     * 
     * @param confirmationNumber 확인번호
     * @return 예약 정보
     */
    fun findByConfirmationNumber(confirmationNumber: String): Mono<ReservationResponse>
    
    /**
     * 고객 ID로 예약 목록을 조회합니다.
     * 
     * @param guestId 고객 ID
     * @param criteria 조회 조건
     * @return 예약 목록
     */
    fun findByGuestId(guestId: UUID, criteria: ReservationSearchCriteria): Flux<ReservationSummary>
    
    /**
     * 시설 ID로 예약 목록을 조회합니다.
     * 
     * @param propertyId 시설 ID
     * @param criteria 조회 조건
     * @return 예약 목록
     */
    fun findByPropertyId(propertyId: UUID, criteria: ReservationSearchCriteria): Flux<ReservationSummary>
    
    /**
     * 다중 조건으로 예약을 검색합니다.
     * 
     * @param criteria 검색 조건
     * @return 예약 목록
     */
    fun search(criteria: ReservationSearchCriteria): Mono<ReservationSearchResult>
}

/**
 * 예약 검색 조건
 */
data class ReservationSearchCriteria(
    val guestId: UUID? = null,
    val propertyId: UUID? = null,
    val status: Set<String> = emptySet(),
    val checkInDateFrom: LocalDate? = null,
    val checkInDateTo: LocalDate? = null,
    val checkOutDateFrom: LocalDate? = null,
    val checkOutDateTo: LocalDate? = null,
    val createdDateFrom: LocalDateTime? = null,
    val createdDateTo: LocalDateTime? = null,
    val confirmationNumber: String? = null,
    val guestEmail: String? = null,
    val guestPhone: String? = null,
    val guestName: String? = null,
    val roomTypeId: UUID? = null,
    val totalAmountFrom: Double? = null,
    val totalAmountTo: Double? = null,
    val source: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "createdAt",
    val sortDirection: SortDirection = SortDirection.DESC,
    val includeHistory: Boolean = false,
    val includeCancelled: Boolean = false
)

/**
 * 정렬 방향
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * 예약 상세 응답
 */
data class ReservationDetailResponse(
    val reservation: ReservationResponse,
    val guest: GuestSummary,
    val property: PropertySummary,
    val roomType: RoomTypeSummary,
    val payment: PaymentSummary? = null,
    val additionalGuests: List<AdditionalGuestInfo> = emptyList(),
    val preferences: ReservationPreferencesInfo? = null,
    val history: List<ReservationHistoryItem> = emptyList(),
    val timeline: List<ReservationTimelineEvent> = emptyList()
)

/**
 * 예약 요약
 */
data class ReservationSummary(
    val reservationId: UUID,
    val confirmationNumber: String,
    val status: String,
    val guestName: String,
    val guestEmail: String,
    val propertyName: String,
    val roomTypeName: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val totalAmount: Double,
    val createdAt: LocalDateTime,
    val source: String
)

/**
 * 고객 요약 정보
 */
data class GuestSummary(
    val guestId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String? = null,
    val loyaltyTier: String? = null,
    val totalStays: Int = 0
)

/**
 * 시설 요약 정보
 */
data class PropertySummary(
    val propertyId: UUID,
    val name: String,
    val address: String,
    val city: String,
    val country: String,
    val category: String,
    val rating: Double? = null
)

/**
 * 객실 유형 요약 정보
 */
data class RoomTypeSummary(
    val roomTypeId: UUID,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val bedType: String,
    val amenities: List<String> = emptyList()
)

/**
 * 결제 요약 정보
 */
data class PaymentSummary(
    val paymentId: UUID,
    val status: String,
    val method: String,
    val amount: Double,
    val processedAt: LocalDateTime? = null
)

/**
 * 예약 이력 항목
 */
data class ReservationHistoryItem(
    val changeId: UUID,
    val changeType: String, // CREATED, UPDATED, CANCELLED, etc.
    val changedFields: List<String>,
    val previousValues: Map<String, Any?>,
    val newValues: Map<String, Any?>,
    val changedBy: UUID? = null,
    val changeReason: String? = null,
    val changedAt: LocalDateTime
)

/**
 * 예약 타임라인 이벤트
 */
data class ReservationTimelineEvent(
    val eventId: UUID,
    val eventType: String,
    val title: String,
    val description: String,
    val eventTime: LocalDateTime,
    val actor: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 예약 검색 결과
 */
data class ReservationSearchResult(
    val reservations: List<ReservationSummary>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)