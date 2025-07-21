package com.example.reservation.application.usecase.guest

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 고객 관리 유스케이스
 * 실무 릴리즈 급 구현: GDPR 준수, 개인정보보호, 로열티 관리
 */
interface GuestManagementUseCase {
    
    /**
     * 새로운 고객을 등록합니다.
     * 
     * @param command 고객 등록 명령
     * @return 등록된 고객 정보
     */
    fun createGuest(command: CreateGuestCommand): Mono<GuestResponse>
    
    /**
     * 고객 정보를 수정합니다.
     * 
     * @param command 고객 수정 명령
     * @return 수정된 고객 정보
     */
    fun updateGuest(command: UpdateGuestCommand): Mono<GuestResponse>
    
    /**
     * 고객 ID로 고객 정보를 조회합니다.
     * 
     * @param guestId 고객 ID
     * @param includeSensitive 민감정보 포함 여부
     * @return 고객 상세 정보
     */
    fun findById(guestId: UUID, includeSensitive: Boolean = false): Mono<GuestDetailResponse>
    
    /**
     * 이메일로 고객을 조회합니다.
     * 
     * @param email 이메일
     * @return 고객 정보
     */
    fun findByEmail(email: String): Mono<GuestResponse>
    
    /**
     * 고객 정보를 검색합니다.
     * 
     * @param criteria 검색 조건
     * @return 고객 목록
     */
    fun searchGuests(criteria: GuestSearchCriteria): Mono<GuestSearchResult>
    
    /**
     * 고객 계정을 비활성화합니다.
     * 
     * @param guestId 고객 ID
     * @param reason 비활성화 사유
     * @return 처리 결과
     */
    fun deactivateGuest(guestId: UUID, reason: String): Mono<Boolean>
    
    /**
     * GDPR 준수를 위한 고객 데이터 삭제
     * 
     * @param guestId 고객 ID
     * @return 삭제 처리 결과
     */
    fun deleteGuestData(guestId: UUID): Mono<DataDeletionResult>
}

/**
 * 고객 등록 명령
 */
data class CreateGuestCommand(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String? = null,
    val dateOfBirth: LocalDate? = null,
    val nationality: String? = null,
    val gender: String? = null,
    val preferredLanguage: String = "EN",
    val address: AddressInfo? = null,
    val preferences: GuestPreferencesInfo? = null,
    val marketingOptIn: Boolean = false,
    val termsAccepted: Boolean = true,
    val privacyPolicyAccepted: Boolean = true,
    val dataProcessingConsent: Boolean = true,
    val communicationPreferences: Set<String> = setOf("EMAIL")
)

/**
 * 고객 수정 명령
 */
data class UpdateGuestCommand(
    val guestId: UUID,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val dateOfBirth: LocalDate? = null,
    val nationality: String? = null,
    val gender: String? = null,
    val preferredLanguage: String? = null,
    val address: AddressInfo? = null,
    val preferences: GuestPreferencesInfo? = null,
    val marketingOptIn: Boolean? = null,
    val communicationPreferences: Set<String>? = null,
    val updateReason: String? = null
)

/**
 * 주소 정보
 */
data class AddressInfo(
    val street: String,
    val city: String,
    val state: String? = null,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean = true
)

/**
 * 고객 선호사항 정보
 */
data class GuestPreferencesInfo(
    val roomType: String? = null,
    val bedType: String? = null,
    val floorPreference: String? = null,
    val smokingPreference: String = "NON_SMOKING",
    val dietaryRestrictions: Set<String> = emptySet(),
    val accessibilityNeeds: Set<String> = emptySet(),
    val servicePreferences: Set<String> = emptySet(),
    val amenityPreferences: Set<String> = emptySet()
)

/**
 * 고객 검색 조건
 */
data class GuestSearchCriteria(
    val name: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val loyaltyTier: String? = null,
    val status: String? = null,
    val registrationDateFrom: LocalDateTime? = null,
    val registrationDateTo: LocalDateTime? = null,
    val lastActivityFrom: LocalDateTime? = null,
    val lastActivityTo: LocalDateTime? = null,
    val totalStaysFrom: Int? = null,
    val totalStaysTo: Int? = null,
    val totalSpentFrom: Double? = null,
    val totalSpentTo: Double? = null,
    val nationality: String? = null,
    val city: String? = null,
    val country: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "lastName",
    val sortDirection: SortDirection = SortDirection.ASC
)

/**
 * 정렬 방향
 */
enum class SortDirection {
    ASC, DESC
}

/**
 * 고객 응답 정보
 */
data class GuestResponse(
    val guestId: UUID,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phoneNumber: String? = null,
    val preferredLanguage: String,
    val loyaltyTier: String,
    val loyaltyPoints: Int,
    val status: String,
    val registrationDate: LocalDateTime,
    val lastActivityDate: LocalDateTime? = null,
    val totalStays: Int = 0,
    val totalSpent: Double = 0.0,
    val vipStatus: Boolean = false
)

/**
 * 고객 상세 응답 정보
 */
data class GuestDetailResponse(
    val guest: GuestResponse,
    val address: AddressInfo? = null,
    val preferences: GuestPreferencesInfo? = null,
    val loyaltyDetails: LoyaltyDetails,
    val recentReservations: List<ReservationSummaryInfo> = emptyList(),
    val communicationHistory: List<CommunicationRecord> = emptyList(),
    val securityInfo: SecurityInfo? = null
)

/**
 * 로열티 상세 정보
 */
data class LoyaltyDetails(
    val currentTier: String,
    val currentPoints: Int,
    val pointsToNextTier: Int,
    val lifetimePoints: Int,
    val tierBenefits: List<String>,
    val expiringPoints: Int = 0,
    val expirationDate: LocalDate? = null
)

/**
 * 예약 요약 정보
 */
data class ReservationSummaryInfo(
    val reservationId: UUID,
    val confirmationNumber: String,
    val propertyName: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val status: String,
    val totalAmount: Double
)

/**
 * 커뮤니케이션 기록
 */
data class CommunicationRecord(
    val recordId: UUID,
    val type: String, // EMAIL, SMS, PHONE, CHAT
    val subject: String,
    val channel: String,
    val sentAt: LocalDateTime,
    val status: String, // SENT, DELIVERED, READ, FAILED
    val category: String // BOOKING, MARKETING, SUPPORT, etc.
)

/**
 * 보안 정보
 */
data class SecurityInfo(
    val lastLoginDate: LocalDateTime? = null,
    val failedLoginAttempts: Int = 0,
    val accountLocked: Boolean = false,
    val passwordLastChanged: LocalDateTime? = null,
    val twoFactorEnabled: Boolean = false,
    val trustedDevices: Int = 0
)

/**
 * 고객 검색 결과
 */
data class GuestSearchResult(
    val guests: List<GuestResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

/**
 * 데이터 삭제 결과
 */
data class DataDeletionResult(
    val guestId: UUID,
    val deletionId: UUID,
    val status: String, // REQUESTED, IN_PROGRESS, COMPLETED, FAILED
    val requestedAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val deletedEntities: List<String> = emptyList(),
    val retainedEntities: List<String> = emptyList(), // 법적 요구사항으로 보존
    val errors: List<String> = emptyList()
)