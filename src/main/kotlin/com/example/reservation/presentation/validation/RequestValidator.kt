package com.example.reservation.presentation.validation

import com.example.reservation.application.usecase.reservation.ReservationSearchCriteria
import com.example.reservation.presentation.dto.request.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.regex.Pattern

/**
 * 요청 검증기
 * 실무 릴리즈 급 구현: 보안 검증, 비즈니스 규칙 검증, 성능 최적화
 */
@Component
class RequestValidator {

    companion object {
        // 보안 패턴들
        private val SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        private val SQL_INJECTION_PATTERN = Pattern.compile("(union|select|insert|update|delete|drop|create|alter|exec|execute)", Pattern.CASE_INSENSITIVE)
        private val XSS_PATTERN = Pattern.compile("[<>\"'&]")
        
        // 비즈니스 규칙 상수들
        private const val MAX_ADVANCE_BOOKING_DAYS = 365L
        private const val MIN_ADVANCE_BOOKING_HOURS = 4L
        private const val MAX_STAY_NIGHTS = 30
        private const val MAX_TOTAL_GUESTS = 12
        private const val MAX_ADDITIONAL_GUESTS = 10
        
        // 확인번호 패턴
        private val CONFIRMATION_NUMBER_PATTERN = Pattern.compile("^[A-Z0-9]{6,20}$")
    }

    /**
     * 예약 생성 요청 검증
     */
    fun validateCreateRequest(request: CreateReservationRequest): Mono<Void> {
        return Mono.fromRunnable {
            val errors = mutableListOf<String>()
            
            // 기본 필드 검증
            validateBasicFields(request, errors)
            
            // 날짜 검증
            validateDates(request.checkInDate, request.checkOutDate, errors)
            
            // 인원 검증
            validateGuests(request.adultCount, request.childCount, request.infantCount, errors)
            
            // 금액 검증
            validateAmount(request.totalAmount, errors)
            
            // 보안 검증
            validateSecurityThreats(request, errors)
            
            // 비즈니스 규칙 검증
            validateBusinessRules(request, errors)
            
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("검증 실패: ${errors.joinToString(", ")}")
            }
        }
    }

    /**
     * 예약 수정 요청 검증
     */
    fun validateUpdateRequest(request: UpdateReservationRequest): Mono<Void> {
        return Mono.fromRunnable {
            val errors = mutableListOf<String>()
            
            // 날짜 검증 (값이 있는 경우만)
            if (request.checkInDate != null && request.checkOutDate != null) {
                validateDates(request.checkInDate, request.checkOutDate, errors)
            }
            
            // 인원 검증 (값이 있는 경우만)
            val adultCount = request.adultCount ?: 1
            val childCount = request.childCount ?: 0
            val infantCount = request.infantCount ?: 0
            validateGuests(adultCount, childCount, infantCount, errors)
            
            // 보안 검증
            validateSecurityThreats(request, errors)
            
            // 수정 사유 검증
            if (request.updateReason.isBlank()) {
                errors.add("수정 사유는 필수입니다")
            }
            
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("검증 실패: ${errors.joinToString(", ")}")
            }
        }
    }

    /**
     * 예약 취소 요청 검증
     */
    fun validateCancelRequest(request: CancelReservationRequest): Mono<Void> {
        return Mono.fromRunnable {
            val errors = mutableListOf<String>()
            
            // 취소 사유 검증
            if (!isValidCancellationReason(request.reason)) {
                errors.add("유효하지 않은 취소 사유입니다: ${request.reason}")
            }
            
            // 보안 검증
            request.reasonDetails?.let { details ->
                if (containsSecurityThreats(details)) {
                    errors.add("취소 상세 사유에 허용되지 않는 내용이 포함되어 있습니다")
                }
            }
            
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("검증 실패: ${errors.joinToString(", ")}")
            }
        }
    }

    /**
     * 검색 조건 검증
     */
    fun validateSearchCriteria(criteria: ReservationSearchCriteria): Mono<Void> {
        return Mono.fromRunnable {
            val errors = mutableListOf<String>()
            
            // 페이징 검증
            if (criteria.page < 0) {
                errors.add("페이지 번호는 0 이상이어야 합니다")
            }
            
            if (criteria.size < 1 || criteria.size > 100) {
                errors.add("페이지 크기는 1-100 사이여야 합니다")
            }
            
            // 날짜 범위 검증
            if (criteria.checkInDateFrom != null && criteria.checkInDateTo != null) {
                if (criteria.checkInDateFrom.isAfter(criteria.checkInDateTo)) {
                    errors.add("체크인 시작 날짜는 종료 날짜보다 늦을 수 없습니다")
                }
                
                val daysBetween = ChronoUnit.DAYS.between(criteria.checkInDateFrom, criteria.checkInDateTo)
                if (daysBetween > 365) {
                    errors.add("검색 기간은 최대 365일까지 가능합니다")
                }
            }
            
            // 보안 검증 (문자열 필드들)
            criteria.confirmationNumber?.let { 
                if (containsSecurityThreats(it)) {
                    errors.add("확인번호에 허용되지 않는 문자가 포함되어 있습니다")
                }
            }
            
            criteria.guestEmail?.let {
                if (containsSecurityThreats(it)) {
                    errors.add("이메일에 허용되지 않는 문자가 포함되어 있습니다")
                }
            }
            
            criteria.guestName?.let {
                if (containsSecurityThreats(it)) {
                    errors.add("고객명에 허용되지 않는 문자가 포함되어 있습니다")
                }
            }
            
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("검색 조건 검증 실패: ${errors.joinToString(", ")}")
            }
        }
    }

    /**
     * 확인번호 검증
     */
    fun validateConfirmationNumber(confirmationNumber: String): Mono<Void> {
        return Mono.fromRunnable {
            val errors = mutableListOf<String>()
            
            if (confirmationNumber.isBlank()) {
                errors.add("확인번호는 필수입니다")
            }
            
            if (!CONFIRMATION_NUMBER_PATTERN.matcher(confirmationNumber).matches()) {
                errors.add("확인번호 형식이 올바르지 않습니다")
            }
            
            if (containsSecurityThreats(confirmationNumber)) {
                errors.add("확인번호에 허용되지 않는 문자가 포함되어 있습니다")
            }
            
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("확인번호 검증 실패: ${errors.joinToString(", ")}")
            }
        }
    }

    // === 내부 검증 메서드들 ===

    /**
     * 기본 필드 검증
     */
    private fun validateBasicFields(request: CreateReservationRequest, errors: MutableList<String>) {
        // UUID 검증
        listOf(
            request.guestId to "고객 ID",
            request.propertyId to "시설 ID", 
            request.roomTypeId to "객실 유형 ID"
        ).forEach { (uuid, fieldName) ->
            if (uuid.toString().isBlank()) {
                errors.add("${fieldName}는 필수입니다")
            }
        }
        
        // 선택적 문자열 필드 길이 검증
        request.specialRequests?.let {
            if (it.length > 1000) {
                errors.add("특별 요청사항은 1000자를 초과할 수 없습니다")
            }
        }
        
        request.ratePlanCode?.let {
            if (it.length > 50) {
                errors.add("요금 플랜 코드는 50자를 초과할 수 없습니다")
            }
        }
        
        request.promotionCode?.let {
            if (it.length > 50) {
                errors.add("프로모션 코드는 50자를 초과할 수 없습니다")
            }
        }
    }

    /**
     * 날짜 검증
     */
    private fun validateDates(checkInDate: LocalDate, checkOutDate: LocalDate, errors: MutableList<String>) {
        val today = LocalDate.now()
        
        // 과거 날짜 검증
        if (checkInDate.isBefore(today)) {
            errors.add("체크인 날짜는 현재 날짜 이후여야 합니다")
        }
        
        if (checkOutDate.isBefore(today)) {
            errors.add("체크아웃 날짜는 현재 날짜 이후여야 합니다")
        }
        
        // 체크아웃이 체크인보다 늦은지 검증
        if (!checkOutDate.isAfter(checkInDate)) {
            errors.add("체크아웃 날짜는 체크인 날짜보다 늦어야 합니다")
        }
        
        // 최소 체크인 시간 검증 (4시간 전까지만 예약 가능)
        val minCheckInDateTime = today.atTime(java.time.LocalTime.now().plusHours(MIN_ADVANCE_BOOKING_HOURS))
        if (checkInDate.atStartOfDay().isBefore(minCheckInDateTime)) {
            errors.add("체크인 ${MIN_ADVANCE_BOOKING_HOURS}시간 전까지만 예약 가능합니다")
        }
        
        // 최대 사전 예약 기간 검증
        val maxAdvanceDate = today.plusDays(MAX_ADVANCE_BOOKING_DAYS)
        if (checkInDate.isAfter(maxAdvanceDate)) {
            errors.add("최대 ${MAX_ADVANCE_BOOKING_DAYS}일 후까지만 예약 가능합니다")
        }
        
        // 최대 숙박 기간 검증
        val nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate)
        if (nights > MAX_STAY_NIGHTS) {
            errors.add("최대 숙박 기간은 ${MAX_STAY_NIGHTS}박입니다")
        }
        
        if (nights < 1) {
            errors.add("최소 1박 이상 숙박해야 합니다")
        }
    }

    /**
     * 인원 검증
     */
    private fun validateGuests(adultCount: Int, childCount: Int, infantCount: Int, errors: MutableList<String>) {
        val totalGuests = adultCount + childCount + infantCount
        
        if (totalGuests > MAX_TOTAL_GUESTS) {
            errors.add("총 인원수는 ${MAX_TOTAL_GUESTS}명을 초과할 수 없습니다")
        }
        
        if (adultCount < 1) {
            errors.add("성인은 최소 1명 이상이어야 합니다")
        }
        
        if (adultCount > 10) {
            errors.add("성인은 최대 10명까지 가능합니다")
        }
        
        if (childCount < 0 || childCount > 8) {
            errors.add("아동은 0-8명까지 가능합니다")
        }
        
        if (infantCount < 0 || infantCount > 4) {
            errors.add("유아는 0-4명까지 가능합니다")
        }
        
        // 비즈니스 규칙: 유아 수는 성인 수를 초과할 수 없음
        if (infantCount > adultCount) {
            errors.add("유아 수는 성인 수를 초과할 수 없습니다")
        }
    }

    /**
     * 금액 검증
     */
    private fun validateAmount(amount: java.math.BigDecimal, errors: MutableList<String>) {
        if (amount <= java.math.BigDecimal.ZERO) {
            errors.add("예약 금액은 0보다 커야 합니다")
        }
        
        if (amount > java.math.BigDecimal("10000000")) {
            errors.add("예약 금액은 천만원을 초과할 수 없습니다")
        }
        
        // 소수점 검증 (2자리까지만 허용)
        if (amount.scale() > 2) {
            errors.add("금액은 소수점 2자리까지만 허용됩니다")
        }
    }

    /**
     * 보안 위협 검증
     */
    private fun validateSecurityThreats(request: Any, errors: MutableList<String>) {
        when (request) {
            is CreateReservationRequest -> {
                listOfNotNull(
                    request.specialRequests,
                    request.ratePlanCode,
                    request.promotionCode
                ).forEach { field ->
                    if (containsSecurityThreats(field)) {
                        errors.add("입력값에 허용되지 않는 내용이 포함되어 있습니다")
                    }
                }
            }
            is UpdateReservationRequest -> {
                listOfNotNull(
                    request.specialRequests,
                    request.updateReason
                ).forEach { field ->
                    if (containsSecurityThreats(field)) {
                        errors.add("입력값에 허용되지 않는 내용이 포함되어 있습니다")
                    }
                }
            }
        }
    }

    /**
     * 비즈니스 규칙 검증
     */
    private fun validateBusinessRules(request: CreateReservationRequest, errors: MutableList<String>) {
        // 추가 게스트 수 검증
        if (request.additionalGuests.size > MAX_ADDITIONAL_GUESTS) {
            errors.add("추가 게스트는 최대 ${MAX_ADDITIONAL_GUESTS}명까지 가능합니다")
        }
        
        // 커뮤니케이션 선호사항 검증
        if (request.communicationPreferences.size > 10) {
            errors.add("커뮤니케이션 선호사항은 최대 10개까지 선택 가능합니다")
        }
        
        // 유효한 커뮤니케이션 채널 검증
        val validChannels = setOf("EMAIL", "SMS", "PHONE", "PUSH", "MAIL")
        val invalidChannels = request.communicationPreferences - validChannels
        if (invalidChannels.isNotEmpty()) {
            errors.add("유효하지 않은 커뮤니케이션 채널: ${invalidChannels.joinToString()}")
        }
        
        // 주말/성수기 예약 제한 (예시)
        val isWeekend = request.checkInDate.dayOfWeek in setOf(
            java.time.DayOfWeek.FRIDAY, 
            java.time.DayOfWeek.SATURDAY
        )
        val isHighSeason = request.checkInDate.month in setOf(
            java.time.Month.JULY, 
            java.time.Month.AUGUST, 
            java.time.Month.DECEMBER
        )
        
        if (isWeekend && isHighSeason && request.adultCount + request.childCount < 2) {
            errors.add("성수기 주말에는 최소 2명 이상 예약해야 합니다")
        }
    }

    /**
     * 보안 위협 검사
     */
    private fun containsSecurityThreats(input: String): Boolean {
        return SCRIPT_PATTERN.matcher(input).find() ||
                SQL_INJECTION_PATTERN.matcher(input).find() ||
                XSS_PATTERN.matcher(input).find() ||
                input.contains("javascript:", ignoreCase = true) ||
                input.contains("data:", ignoreCase = true) ||
                input.contains("vbscript:", ignoreCase = true)
    }

    /**
     * 유효한 취소 사유인지 확인
     */
    private fun isValidCancellationReason(reason: String): Boolean {
        val validReasons = setOf(
            "GUEST_REQUEST", "PROPERTY_MAINTENANCE", "OVERBOOKING", 
            "WEATHER", "EMERGENCY", "FORCE_MAJEURE", "PAYMENT_FAILURE",
            "POLICY_VIOLATION", "ADMIN_DECISION", "SYSTEM_ERROR"
        )
        return reason in validReasons
    }
}