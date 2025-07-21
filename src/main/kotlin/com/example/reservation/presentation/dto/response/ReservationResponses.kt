package com.example.reservation.presentation.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 생성 응답 DTO
 * 실무 릴리즈 급 구현: 민감정보 제외, 클라이언트 친화적 응답
 */
@Schema(description = "예약 생성 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateReservationResponseDto(
    
    @Schema(description = "예약 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val reservationId: UUID,
    
    @Schema(description = "확인번호", example = "RSV202512251234")
    val confirmationNumber: String,
    
    @Schema(description = "예약 상태", example = "CONFIRMED")
    val status: String,
    
    @Schema(description = "총 금액", example = "250000.00")
    val totalAmount: BigDecimal,
    
    @Schema(description = "통화", example = "KRW")
    val currency: String = "KRW",
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크인 날짜", example = "2025-12-25")
    val checkInDate: LocalDate,
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크아웃 날짜", example = "2025-12-27")
    val checkOutDate: LocalDate,
    
    @Schema(description = "성인 수", example = "2")
    val adultCount: Int,
    
    @Schema(description = "아동 수", example = "1")
    val childCount: Int,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예약 생성 시간", example = "2025-07-21 14:30:00")
    val createdAt: LocalDateTime,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예상 체크인 시간", example = "2025-12-25 15:00:00")
    val estimatedCheckInTime: LocalDateTime? = null,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예상 체크아웃 시간", example = "2025-12-27 11:00:00")
    val estimatedCheckOutTime: LocalDateTime? = null,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "취소 마감 시간", example = "2025-12-24 23:59:59")
    val cancellationDeadline: LocalDateTime? = null,
    
    @Schema(description = "환불 가능 금액", example = "200000.00")
    val refundableAmount: BigDecimal? = null,
    
    @Schema(description = "결제 정보")
    val payment: PaymentInfoDto? = null,
    
    @Schema(description = "고객 정보")
    val guest: GuestInfoDto,
    
    @Schema(description = "시설 정보")
    val property: PropertyInfoDto,
    
    @Schema(description = "객실 유형 정보")
    val roomType: RoomTypeInfoDto
)

/**
 * 예약 상세 응답 DTO
 */
@Schema(description = "예약 상세 조회 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReservationDetailResponseDto(
    
    @Schema(description = "기본 예약 정보")
    val reservation: ReservationResponseDto,
    
    @Schema(description = "고객 상세 정보")
    val guest: GuestDetailDto,
    
    @Schema(description = "시설 상세 정보")
    val property: PropertyDetailDto,
    
    @Schema(description = "객실 유형 상세 정보")
    val roomType: RoomTypeDetailDto,
    
    @Schema(description = "결제 상세 정보")
    val payment: PaymentDetailDto? = null,
    
    @Schema(description = "추가 게스트 목록")
    val additionalGuests: List<AdditionalGuestDto> = emptyList(),
    
    @Schema(description = "예약 선호사항")
    val preferences: ReservationPreferencesDto? = null,
    
    @Schema(description = "예약 변경 이력")
    val history: List<ReservationHistoryDto> = emptyList(),
    
    @Schema(description = "예약 타임라인")
    val timeline: List<ReservationTimelineDto> = emptyList()
)

/**
 * 예약 응답 DTO (기본)
 */
@Schema(description = "예약 정보")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReservationResponseDto(
    
    @Schema(description = "예약 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val reservationId: UUID,
    
    @Schema(description = "확인번호", example = "RSV202512251234")
    val confirmationNumber: String,
    
    @Schema(description = "예약 상태", example = "CONFIRMED")
    val status: String,
    
    @Schema(description = "고객 ID", example = "550e8400-e29b-41d4-a716-446655440001")
    val guestId: UUID,
    
    @Schema(description = "시설 ID", example = "550e8400-e29b-41d4-a716-446655440002")
    val propertyId: UUID,
    
    @Schema(description = "객실 유형 ID", example = "550e8400-e29b-41d4-a716-446655440003")
    val roomTypeId: UUID,
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크인 날짜", example = "2025-12-25")
    val checkInDate: LocalDate,
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크아웃 날짜", example = "2025-12-27")
    val checkOutDate: LocalDate,
    
    @Schema(description = "총 금액", example = "250000.00")
    val totalAmount: BigDecimal,
    
    @Schema(description = "통화", example = "KRW")
    val currency: String = "KRW",
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예약 생성 시간", example = "2025-07-21 14:30:00")
    val createdAt: LocalDateTime,
    
    @Schema(description = "숙박 일수", example = "2")
    val nights: Int,
    
    @Schema(description = "총 인원 수", example = "3")
    val totalGuests: Int
)

/**
 * 예약 취소 응답 DTO
 */
@Schema(description = "예약 취소 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CancellationResponseDto(
    
    @Schema(description = "예약 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val reservationId: UUID,
    
    @Schema(description = "취소 ID", example = "550e8400-e29b-41d4-a716-446655440004")
    val cancellationId: UUID,
    
    @Schema(description = "취소 상태", example = "CANCELLED")
    val status: String,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "취소 처리 시간", example = "2025-07-21 16:45:00")
    val cancellationDate: LocalDateTime,
    
    @Schema(description = "환불 금액", example = "200000.00")
    val refundAmount: BigDecimal,
    
    @Schema(description = "취소 수수료", example = "25000.00")
    val cancellationFee: BigDecimal,
    
    @Schema(description = "처리 수수료", example = "5000.00")
    val processingFee: BigDecimal,
    
    @Schema(description = "실제 환불 금액", example = "170000.00")
    val netRefund: BigDecimal,
    
    @Schema(description = "환불 수단", example = "ORIGINAL_PAYMENT_METHOD")
    val refundMethod: String? = null,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예상 환불 완료 시간", example = "2025-07-23 16:45:00")
    val estimatedRefundDate: LocalDateTime? = null,
    
    @Schema(description = "취소 정책", example = "3일 전 취소시 100% 환불")
    val cancellationPolicy: String,
    
    @Schema(description = "환불 확인번호", example = "REF202507211645")
    val refundConfirmationNumber: String? = null
)

/**
 * 예약 검색 결과 응답 DTO
 */
@Schema(description = "예약 검색 결과")
data class ReservationSearchResponseDto(
    
    @Schema(description = "예약 목록")
    val reservations: List<ReservationSummaryDto>,
    
    @Schema(description = "페이징 정보")
    val pagination: PaginationDto,
    
    @Schema(description = "검색 통계")
    val statistics: SearchStatisticsDto? = null
)

/**
 * 예약 요약 DTO
 */
@Schema(description = "예약 요약 정보")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReservationSummaryDto(
    
    @Schema(description = "예약 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val reservationId: UUID,
    
    @Schema(description = "확인번호", example = "RSV202512251234")
    val confirmationNumber: String,
    
    @Schema(description = "예약 상태", example = "CONFIRMED")
    val status: String,
    
    @Schema(description = "고객명", example = "김철수")
    val guestName: String,
    
    @Schema(description = "고객 이메일", example = "kim.chulsoo@example.com")
    val guestEmail: String,
    
    @Schema(description = "시설명", example = "조선호텔 서울")
    val propertyName: String,
    
    @Schema(description = "객실 유형명", example = "디럭스 킹룸")
    val roomTypeName: String,
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크인 날짜", example = "2025-12-25")
    val checkInDate: LocalDate,
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "체크아웃 날짜", example = "2025-12-27")
    val checkOutDate: LocalDate,
    
    @Schema(description = "총 금액", example = "250000.00")
    val totalAmount: BigDecimal,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "예약 생성 시간", example = "2025-07-21 14:30:00")
    val createdAt: LocalDateTime,
    
    @Schema(description = "예약 출처", example = "WEBSITE")
    val source: String,
    
    @Schema(description = "숙박 일수", example = "2")
    val nights: Int,
    
    @Schema(description = "총 인원", example = "3")
    val totalGuests: Int
)

/**
 * 환불 계산 결과 응답 DTO
 */
@Schema(description = "환불 계산 결과")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RefundCalculationResponseDto(
    
    @Schema(description = "원래 예약 금액", example = "250000.00")
    val originalAmount: BigDecimal,
    
    @Schema(description = "환불 가능 금액", example = "200000.00")
    val refundableAmount: BigDecimal,
    
    @Schema(description = "취소 수수료", example = "25000.00")
    val cancellationFee: BigDecimal,
    
    @Schema(description = "처리 수수료", example = "5000.00")
    val processingFee: BigDecimal,
    
    @Schema(description = "세금 환불", example = "20000.00")
    val taxRefund: BigDecimal,
    
    @Schema(description = "실제 환불 금액", example = "170000.00")
    val netRefund: BigDecimal,
    
    @Schema(description = "환불 비율", example = "68.0")
    val refundPercentage: Double,
    
    @Schema(description = "취소 정책 정보")
    val cancellationPolicy: CancellationPolicyDto,
    
    @Schema(description = "환불 내역")
    val breakdown: List<RefundBreakdownItemDto> = emptyList()
)

/**
 * 예약 검증 응답 DTO
 */
@Schema(description = "예약 검증 결과")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidationResponseDto(
    
    @Schema(description = "검증 통과 여부", example = "true")
    val isValid: Boolean,
    
    @Schema(description = "사용 가능한 객실 수", example = "5")
    val availableRooms: Int,
    
    @Schema(description = "총 금액", example = "250000.00")
    val totalAmount: BigDecimal,
    
    @Schema(description = "세금", example = "25000.00")
    val taxAmount: BigDecimal,
    
    @Schema(description = "서비스 요금", example = "5000.00")
    val serviceCharges: BigDecimal,
    
    @Schema(description = "취소 정책", example = "3일 전 취소시 100% 환불")
    val cancellationPolicy: String,
    
    @Schema(description = "검증 오류 목록")
    val validationErrors: List<FieldErrorDto> = emptyList(),
    
    @Schema(description = "경고 목록")
    val warnings: List<ValidationWarningDto> = emptyList(),
    
    @Schema(description = "추천 사항")
    val recommendations: List<String> = emptyList()
)

// === 공통 DTO 클래스들 ===

/**
 * 페이징 정보 DTO
 */
@Schema(description = "페이징 정보")
data class PaginationDto(
    @Schema(description = "현재 페이지", example = "0")
    val currentPage: Int,
    
    @Schema(description = "페이지 크기", example = "20")
    val pageSize: Int,
    
    @Schema(description = "총 요소 수", example = "157")
    val totalElements: Long,
    
    @Schema(description = "총 페이지 수", example = "8")
    val totalPages: Int,
    
    @Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
    
    @Schema(description = "이전 페이지 존재 여부", example = "false")
    val hasPrevious: Boolean
)

/**
 * 검색 통계 DTO
 */
@Schema(description = "검색 통계")
data class SearchStatisticsDto(
    @Schema(description = "총 예약 수", example = "157")
    val totalReservations: Long,
    
    @Schema(description = "총 매출", example = "39250000.00")
    val totalRevenue: BigDecimal,
    
    @Schema(description = "평균 예약 금액", example = "250000.00")
    val averageReservationValue: BigDecimal,
    
    @Schema(description = "취소율", example = "5.7")
    val cancellationRate: Double
)

/**
 * 에러 응답 DTO
 */
@Schema(description = "에러 응답")
data class ErrorResponse(
    @Schema(description = "에러 코드", example = "RESERVATION_NOT_FOUND")
    val code: String,
    
    @Schema(description = "에러 메시지", example = "예약을 찾을 수 없습니다")
    val message: String,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "에러 발생 시간", example = "2025-07-21 14:30:00")
    val timestamp: LocalDateTime
)

/**
 * 검증 에러 응답 DTO
 */
@Schema(description = "검증 에러 응답")
data class ValidationErrorResponse(
    @Schema(description = "에러 코드", example = "VALIDATION_ERROR")
    val code: String,
    
    @Schema(description = "에러 메시지", example = "입력값 검증 실패")
    val message: String,
    
    @Schema(description = "필드별 에러 정보")
    val errors: List<FieldErrorDto>,
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "에러 발생 시간", example = "2025-07-21 14:30:00")
    val timestamp: LocalDateTime
)

/**
 * 필드 에러 DTO
 */
@Schema(description = "필드 에러 정보")
data class FieldErrorDto(
    @Schema(description = "필드명", example = "checkInDate")
    val field: String,
    
    @Schema(description = "에러 코드", example = "FUTURE_DATE_REQUIRED")
    val code: String,
    
    @Schema(description = "에러 메시지", example = "체크인 날짜는 현재 날짜 이후여야 합니다")
    val message: String
)

// 나머지 보조 DTO 클래스들은 간단히 정의
data class PaymentInfoDto(val paymentId: UUID, val status: String, val method: String)
data class GuestInfoDto(val guestId: UUID, val name: String, val email: String)
data class PropertyInfoDto(val propertyId: UUID, val name: String, val address: String)
data class RoomTypeInfoDto(val roomTypeId: UUID, val name: String, val description: String)
data class GuestDetailDto(val guestId: UUID, val name: String, val email: String, val loyaltyTier: String)
data class PropertyDetailDto(val propertyId: UUID, val name: String, val address: String, val amenities: List<String>)
data class RoomTypeDetailDto(val roomTypeId: UUID, val name: String, val description: String, val amenities: List<String>)
data class PaymentDetailDto(val paymentId: UUID, val status: String, val method: String, val amount: BigDecimal)
data class AdditionalGuestDto(val name: String, val email: String?, val relationship: String?)
data class ReservationPreferencesDto(val roomLocation: String?, val bedType: String?, val smokingPreference: String)
data class ReservationHistoryDto(val changeId: UUID, val changeType: String, val changedAt: LocalDateTime)
data class ReservationTimelineDto(val eventId: UUID, val eventType: String, val title: String, val eventTime: LocalDateTime)
data class CancellationPolicyDto(val policyName: String, val description: String)
data class RefundBreakdownItemDto(val category: String, val description: String, val amount: BigDecimal, val isRefundable: Boolean)
data class ValidationWarningDto(val code: String, val message: String, val suggestion: String?)