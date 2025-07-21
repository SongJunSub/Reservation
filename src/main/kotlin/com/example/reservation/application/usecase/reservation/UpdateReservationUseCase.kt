package com.example.reservation.application.usecase.reservation

import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 수정 유스케이스
 * 실무 릴리즈 급 구현: 비즈니스 규칙 검증 및 변경 이력 관리
 */
interface UpdateReservationUseCase {
    
    /**
     * 기존 예약을 수정합니다.
     * 
     * @param command 예약 수정 명령
     * @return 수정된 예약 정보
     */
    fun execute(command: UpdateReservationCommand): Mono<ReservationResponse>
    
    /**
     * 예약 수정 가능성을 검증합니다.
     * 
     * @param reservationId 예약 ID
     * @param command 수정 명령
     * @return 수정 가능 여부 및 추가 비용 정보
     */
    fun validateUpdate(reservationId: UUID, command: UpdateReservationCommand): Mono<UpdateValidationResult>
}

/**
 * 예약 수정 명령
 */
data class UpdateReservationCommand(
    val reservationId: UUID,
    val checkInDate: LocalDate? = null,
    val checkOutDate: LocalDate? = null,
    val adultCount: Int? = null,
    val childCount: Int? = null,
    val infantCount: Int? = null,
    val roomTypeId: UUID? = null,
    val specialRequests: String? = null,
    val additionalGuests: List<AdditionalGuestInfo>? = null,
    val preferences: ReservationPreferencesInfo? = null,
    val updateReason: String,
    val updatedBy: UUID? = null // 업데이트 실행자 (관리자 등)
)

/**
 * 수정 검증 결과
 */
data class UpdateValidationResult(
    val canUpdate: Boolean,
    val additionalCharges: BigDecimal = BigDecimal.ZERO,
    val refundAmount: BigDecimal = BigDecimal.ZERO,
    val netChange: BigDecimal = BigDecimal.ZERO,
    val newCancellationDeadline: LocalDateTime? = null,
    val validationErrors: List<ValidationError> = emptyList(),
    val warnings: List<ValidationWarning> = emptyList(),
    val changesSummary: List<String> = emptyList()
)