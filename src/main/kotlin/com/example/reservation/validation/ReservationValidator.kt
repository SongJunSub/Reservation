package com.example.reservation.validation

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.exception.ValidationException
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * 예약 요청 검증기
 * 비즈니스 규칙에 따른 포괄적인 입력 검증
 */
@Component
class ReservationValidator {
    
    /**
     * 예약 생성 요청 검증
     */
    fun validateCreateRequest(request: CreateReservationRequest) {
        val errors = mutableMapOf<String, String>()
        
        // 고객명 검증
        validateGuestName(request.guestName, errors)
        
        // 객실번호 검증
        validateRoomNumber(request.roomNumber, errors)
        
        // 날짜 검증
        val checkInDate = validateCheckInDate(request.checkInDate, errors)
        val checkOutDate = validateCheckOutDate(request.checkOutDate, errors)
        
        // 날짜 범위 검증 (개별 날짜가 유효한 경우에만)
        if (checkInDate != null && checkOutDate != null) {
            validateDateRange(checkInDate, checkOutDate, errors)
        }
        
        // 금액 검증
        validateTotalAmount(request.totalAmount, errors)
        
        // 오류가 있으면 예외 발생
        if (errors.isNotEmpty()) {
            throw ValidationException.multipleFieldErrors(errors)
        }
    }
    
    /**
     * 예약 수정 요청 검증
     */
    fun validateUpdateRequest(request: UpdateReservationRequest) {
        val errors = mutableMapOf<String, String>()
        
        // 고객명 검증 (null이 아닌 경우에만)
        request.guestName?.let { 
            validateGuestName(it, errors)
        }
        
        // 객실번호 검증 (null이 아닌 경우에만)
        request.roomNumber?.let {
            validateRoomNumber(it, errors)
        }
        
        // 날짜 검증
        val checkInDate = request.checkInDate?.let { 
            validateCheckInDate(it, errors) 
        }
        val checkOutDate = request.checkOutDate?.let { 
            validateCheckOutDate(it, errors) 
        }
        
        // 날짜 범위 검증 (둘 다 제공된 경우에만)
        if (checkInDate != null && checkOutDate != null) {
            validateDateRange(checkInDate, checkOutDate, errors)
        }
        
        // 금액 검증 (null이 아닌 경우에만)
        request.totalAmount?.let {
            validateTotalAmount(it, errors)
        }
        
        if (errors.isNotEmpty()) {
            throw ValidationException.multipleFieldErrors(errors)
        }
    }
    
    private fun validateGuestName(guestName: String, errors: MutableMap<String, String>) {
        when {
            guestName.isBlank() -> {
                errors["guestName"] = "고객명은 필수입니다"
            }
            guestName.length < 2 -> {
                errors["guestName"] = "고객명은 최소 2자 이상이어야 합니다"
            }
            guestName.length > 100 -> {
                errors["guestName"] = "고객명은 100자를 초과할 수 없습니다"
            }
            !guestName.matches(Regex("^[a-zA-Z가-힣\\s]+$")) -> {
                errors["guestName"] = "고객명은 한글, 영문, 공백만 허용됩니다"
            }
        }
    }
    
    private fun validateRoomNumber(roomNumber: String, errors: MutableMap<String, String>) {
        when {
            roomNumber.isBlank() -> {
                errors["roomNumber"] = "객실번호는 필수입니다"
            }
            roomNumber.length > 10 -> {
                errors["roomNumber"] = "객실번호는 10자를 초과할 수 없습니다"
            }
            !roomNumber.matches(Regex("^[A-Z0-9]+$")) -> {
                errors["roomNumber"] = "객실번호는 대문자와 숫자만 허용됩니다"
            }
        }
    }
    
    private fun validateCheckInDate(checkInDate: String, errors: MutableMap<String, String>): LocalDate? {
        if (checkInDate.isBlank()) {
            errors["checkInDate"] = "체크인 날짜는 필수입니다"
            return null
        }
        
        val parsedDate = try {
            LocalDate.parse(checkInDate)
        } catch (e: DateTimeParseException) {
            errors["checkInDate"] = "체크인 날짜 형식이 올바르지 않습니다 (YYYY-MM-DD)"
            return null
        }
        
        val today = LocalDate.now()
        val maxAdvanceBooking = today.plusYears(1)
        
        when {
            parsedDate.isBefore(today) -> {
                errors["checkInDate"] = "체크인 날짜는 오늘 이후여야 합니다"
            }
            parsedDate.isAfter(maxAdvanceBooking) -> {
                errors["checkInDate"] = "체크인 날짜는 1년 이내여야 합니다"
            }
        }
        
        return parsedDate
    }
    
    private fun validateCheckOutDate(checkOutDate: String, errors: MutableMap<String, String>): LocalDate? {
        if (checkOutDate.isBlank()) {
            errors["checkOutDate"] = "체크아웃 날짜는 필수입니다"
            return null
        }
        
        val parsedDate = try {
            LocalDate.parse(checkOutDate)
        } catch (e: DateTimeParseException) {
            errors["checkOutDate"] = "체크아웃 날짜 형식이 올바르지 않습니다 (YYYY-MM-DD)"
            return null
        }
        
        val today = LocalDate.now()
        val maxAdvanceBooking = today.plusYears(1).plusDays(1)
        
        when {
            parsedDate.isBefore(today.plusDays(1)) -> {
                errors["checkOutDate"] = "체크아웃 날짜는 내일 이후여야 합니다"
            }
            parsedDate.isAfter(maxAdvanceBooking) -> {
                errors["checkOutDate"] = "체크아웃 날짜는 1년 이내여야 합니다"
            }
        }
        
        return parsedDate
    }
    
    private fun validateDateRange(checkIn: LocalDate, checkOut: LocalDate, errors: MutableMap<String, String>) {
        when {
            !checkOut.isAfter(checkIn) -> {
                errors["dateRange"] = "체크아웃 날짜는 체크인 날짜보다 늦어야 합니다"
            }
            else -> {
                val stayNights = ChronoUnit.DAYS.between(checkIn, checkOut)
                when {
                    stayNights > 30 -> {
                        errors["dateRange"] = "숙박 기간은 최대 30박까지 가능합니다"
                    }
                    isBlackoutPeriod(checkIn, checkOut) -> {
                        errors["dateRange"] = "선택한 기간은 예약할 수 없는 블랙아웃 기간입니다"
                    }
                    isHighDemandPeriod(checkIn, checkOut) -> {
                        // 경고만 하고 에러는 아님
                        // errors["dateRange"] = "성수기 기간으로 추가 요금이 적용될 수 있습니다"
                    }
                }
            }
        }
    }
    
    private fun validateTotalAmount(totalAmount: Double, errors: MutableMap<String, String>) {
        when {
            totalAmount <= 0 -> {
                errors["totalAmount"] = "예약 금액은 0보다 커야 합니다"
            }
            totalAmount > 10000000 -> {
                errors["totalAmount"] = "예약 금액은 1,000만원을 초과할 수 없습니다"
            }
            totalAmount != Math.round(totalAmount * 100) / 100.0 -> {
                errors["totalAmount"] = "예약 금액은 소수점 2자리까지만 허용됩니다"
            }
        }
    }
    
    /**
     * 블랙아웃 기간 확인
     */
    private fun isBlackoutPeriod(checkIn: LocalDate, checkOut: LocalDate): Boolean {
        val blackoutRanges = listOf(
            // 크리스마스/신정 시즌
            LocalDate.of(checkIn.year, 12, 24)..LocalDate.of(checkIn.year, 12, 26),
            LocalDate.of(checkOut.year, 12, 31)..LocalDate.of(checkOut.year + 1, 1, 2),
            
            // 추석 연휴 (고정값 - 실제로는 동적 계산 필요)
            LocalDate.of(checkIn.year, 9, 28)..LocalDate.of(checkIn.year, 9, 30),
            
            // 설날 연휴 (고정값 - 실제로는 동적 계산 필요)
            LocalDate.of(checkIn.year + 1, 1, 21)..LocalDate.of(checkIn.year + 1, 1, 23)
        )
        
        return blackoutRanges.any { blackoutRange ->
            checkIn <= blackoutRange.endInclusive && checkOut > blackoutRange.start
        }
    }
    
    /**
     * 성수기 기간 확인
     */
    private fun isHighDemandPeriod(checkIn: LocalDate, checkOut: LocalDate): Boolean {
        val highDemandRanges = listOf(
            // 여름휴가철
            LocalDate.of(checkIn.year, 7, 1)..LocalDate.of(checkIn.year, 8, 31),
            
            // 봄 벚꽃 시즌
            LocalDate.of(checkIn.year, 4, 1)..LocalDate.of(checkIn.year, 4, 30),
            
            // 가을 단풍 시즌
            LocalDate.of(checkIn.year, 10, 1)..LocalDate.of(checkIn.year, 11, 15)
        )
        
        return highDemandRanges.any { range ->
            checkIn <= range.endInclusive && checkOut > range.start
        }
    }
    
    /**
     * 비즈니스 규칙 검증
     */
    fun validateBusinessRules(request: CreateReservationRequest) {
        val errors = mutableListOf<String>()
        
        // 최소 예약 리드타임 (당일 예약 불가)
        val checkInDate = LocalDate.parse(request.checkInDate)
        if (checkInDate.isEqual(LocalDate.now())) {
            errors.add("당일 예약은 불가능합니다. 최소 1일 전에 예약해주세요.")
        }
        
        // 주말 프리미엄 정책
        if (isWeekend(checkInDate) && request.totalAmount < 150000) {
            errors.add("주말 예약은 최소 15만원 이상이어야 합니다.")
        }
        
        // VIP 고객 우대 정책 (실제로는 고객 정보를 확인해야 함)
        if (request.guestName.contains("VIP") && request.totalAmount < 100000) {
            errors.add("VIP 고객은 최소 10만원 이상 객실만 예약 가능합니다.")
        }
        
        if (errors.isNotEmpty()) {
            throw ValidationException(
                "비즈니스 규칙 위반",
                globalErrors = errors
            )
        }
    }
    
    private fun isWeekend(date: LocalDate): Boolean {
        val dayOfWeek = date.dayOfWeek
        return dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY
    }
}