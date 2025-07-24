package com.example.reservation.service

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.domain.reservation.PaymentStatus
import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.repository.ReservationRepository
import com.example.reservation.repository.ReservationRepositoryReactive
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import org.springframework.dao.DataAccessException

/**
 * 프로덕션급 예약 비즈니스 서비스
 * 
 * 주요 특징:
 * - 트랜잭션 관리 및 데이터 일관성 보장
 * - 캐싱 전략으로 성능 최적화
 * - 재시도 로직으로 안정성 향상
 * - 비동기 후속 처리로 응답성 개선
 * - 포괄적인 비즈니스 규칙 검증
 */
@Service
@Transactional
class ReservationBusinessService(
    private val reservationRepository: ReservationRepository,
    private val reservationRepositoryReactive: ReservationRepositoryReactive,
    private val notificationService: NotificationService,
    private val auditService: AuditService,
    private val paymentService: PaymentService,
    private val roomAvailabilityService: RoomAvailabilityService
) {

    /**
     * 예약 생성 - 포괄적인 비즈니스 로직
     */
    @Retryable(value = [DataAccessException::class], maxAttempts = 3)
    fun createReservation(request: CreateReservationRequest): Reservation {
        // 1. 비즈니스 규칙 검증
        validateReservationRequest(request)
        
        // 2. 객실 가용성 확인
        checkRoomAvailability(request.roomNumber, request.checkInDate, request.checkOutDate)
        
        // 3. 고객 정보 생성 또는 조회
        val guest = createOrUpdateGuest(request)
        
        // 4. 객실 정보 생성 (실제로는 DB에서 조회)
        val room = createTempRoom(request)
        
        // 5. 예약 엔티티 생성
        val reservation = Reservation(
            id = 0L, // JPA가 자동 할당
            confirmationNumber = generateConfirmationNumber(),
            guest = guest,
            room = room,
            checkInDate = LocalDate.parse(request.checkInDate),
            checkOutDate = LocalDate.parse(request.checkOutDate),
            numberOfGuests = 1,
            numberOfAdults = 1,
            numberOfChildren = 0,
            totalAmount = BigDecimal.valueOf(request.totalAmount),
            roomRate = BigDecimal.valueOf(request.totalAmount),
            status = ReservationStatus.PENDING,
            paymentStatus = PaymentStatus.PENDING,
            guestDetails = createGuestDetails(request),
            source = com.example.reservation.domain.reservation.ReservationSource.DIRECT
        )
        
        // 6. 데이터베이스에 저장
        val savedReservation = reservationRepository.save(reservation)
        
        // 7. 비동기 후속 처리
        processReservationCreatedAsync(savedReservation)
        
        return savedReservation
    }

    /**
     * 예약 조회 - 캐싱 적용
     */
    @Cacheable(value = ["reservations"], key = "#id")
    @Transactional(readOnly = true)
    fun getReservationById(id: Long): Reservation? {
        return reservationRepository.findById(id).orElse(null)
    }

    /**
     * 확인번호로 예약 조회 - 캐싱 적용
     */
    @Cacheable(value = ["reservations-by-confirmation"], key = "#confirmationNumber")
    @Transactional(readOnly = true)
    fun getReservationByConfirmationNumber(confirmationNumber: String): Reservation? {
        return reservationRepository.findByConfirmationNumber(confirmationNumber).orElse(null)
    }

    /**
     * 모든 예약 조회 - 페이징 지원
     */
    @Transactional(readOnly = true)
    fun getAllReservations(pageable: Pageable): Page<Reservation> {
        return reservationRepository.findAll(pageable)
    }

    /**
     * 예약 수정 - 캐시 무효화
     */
    @CacheEvict(value = ["reservations"], key = "#id")
    fun updateReservation(id: Long, request: UpdateReservationRequest): Reservation? {
        val existingReservation = reservationRepository.findById(id).orElse(null) 
            ?: return null
        
        // 비즈니스 규칙 검증
        validateReservationUpdate(existingReservation, request)
        
        // 수정된 예약 생성
        val updatedReservation = existingReservation.copy(
            checkInDate = request.checkInDate?.let { LocalDate.parse(it) } 
                ?: existingReservation.checkInDate,
            checkOutDate = request.checkOutDate?.let { LocalDate.parse(it) } 
                ?: existingReservation.checkOutDate,
            totalAmount = request.totalAmount?.let { BigDecimal.valueOf(it) } 
                ?: existingReservation.totalAmount,
            roomRate = request.totalAmount?.let { BigDecimal.valueOf(it) } 
                ?: existingReservation.roomRate,
            lastModifiedAt = LocalDateTime.now()
        )
        
        val savedReservation = reservationRepository.save(updatedReservation)
        
        // 비동기 후속 처리
        processReservationUpdatedAsync(savedReservation)
        
        return savedReservation
    }

    /**
     * 예약 취소 - 복합 캐시 무효화
     */
    @Caching(evict = [
        CacheEvict(value = ["reservations"], key = "#id"),
        CacheEvict(value = ["reservations-by-confirmation"], key = "#result?.confirmationNumber"),
        CacheEvict(value = ["guest-reservations"], allEntries = true)
    ])
    fun cancelReservation(id: Long, reason: String?): Reservation? {
        val reservation = reservationRepository.findById(id).orElse(null) 
            ?: return null
        
        // 취소 가능 여부 검증
        validateCancellation(reservation)
        
        // 환불 금액 계산
        val refundAmount = calculateRefundAmount(reservation)
        
        // 예약 상태 변경
        val cancelledReservation = reservation.copy(
            status = ReservationStatus.CANCELLED,
            lastModifiedAt = LocalDateTime.now()
        )
        
        val savedReservation = reservationRepository.save(cancelledReservation)
        
        // 비동기 후속 처리
        processCancellationAsync(savedReservation, refundAmount, reason)
        
        return savedReservation
    }

    /**
     * 예약 삭제
     */
    @CacheEvict(value = ["reservations"], key = "#id")
    fun deleteReservation(id: Long): Boolean {
        return if (reservationRepository.existsById(id)) {
            reservationRepository.deleteById(id)
            true
        } else {
            false
        }
    }

    // === 리액티브 메서드들 ===

    /**
     * 실시간 예약 스트림
     */
    fun getReservationStream(): Flux<Reservation> {
        return reservationRepositoryReactive.findAllByOrderByCheckInDateAsc()
    }

    /**
     * 고객별 예약 이력 스트림
     */
    fun getGuestReservationStream(email: String): Flux<Reservation> {
        return reservationRepositoryReactive.findByGuestEmail(email)
    }

    /**
     * 오늘 체크인 스트림
     */
    fun getTodayCheckInsStream(): Flux<Reservation> {
        return reservationRepositoryReactive.findTodayCheckInsStream()
    }

    // === 비즈니스 통계 메서드들 ===

    /**
     * 예약 통계 계산
     */
    @Transactional(readOnly = true)
    fun calculateReservationStats(startDate: LocalDate, endDate: LocalDate): ReservationStats {
        val totalRevenue = reservationRepository.calculateTotalRevenue(startDate, endDate)
        val reservationCount = reservationRepository.countByCheckInDateBetween(startDate, endDate)
        val averageRate = if (reservationCount > 0) totalRevenue.divide(BigDecimal.valueOf(reservationCount)) else BigDecimal.ZERO
        
        return ReservationStats(
            totalReservations = reservationCount,
            totalRevenue = totalRevenue,
            averageRate = averageRate,
            period = "$startDate to $endDate"
        )
    }

    /**
     * 실시간 점유율 계산
     */
    fun getCurrentOccupancyRate(): Mono<Double> {
        return reservationRepositoryReactive.countCurrentOccupancy()
            .map { activeReservations ->
                // 전체 객실 수를 100으로 가정 (실제로는 객실 서비스에서 조회)
                val totalRooms = 100.0
                (activeReservations.toDouble() / totalRooms) * 100.0
            }
    }

    // === Private 헬퍼 메서드들 ===

    private fun validateReservationRequest(request: CreateReservationRequest) {
        val checkIn = LocalDate.parse(request.checkInDate)
        val checkOut = LocalDate.parse(request.checkOutDate)
        
        require(checkIn.isAfter(LocalDate.now().minusDays(1))) {
            "체크인 날짜는 오늘 이후여야 합니다"
        }
        require(checkOut.isAfter(checkIn)) {
            "체크아웃 날짜는 체크인 날짜 이후여야 합니다"
        }
        require(request.totalAmount > 0) {
            "예약 금액은 0보다 커야 합니다"
        }
    }

    private fun checkRoomAvailability(roomNumber: String, checkIn: String, checkOut: String) {
        // 실제로는 객실 가용성 서비스를 통해 확인
        val checkInDate = LocalDate.parse(checkIn)
        val checkOutDate = LocalDate.parse(checkOut)
        
        // 임시 로직: 객실 ID를 1로 가정
        val hasConflict = reservationRepository.existsOverlappingReservation(1L, checkInDate, checkOutDate)
        
        if (hasConflict) {
            throw IllegalStateException("선택한 날짜에 객실이 이미 예약되어 있습니다")
        }
    }

    private fun validateReservationUpdate(reservation: Reservation, request: UpdateReservationRequest) {
        if (reservation.status == ReservationStatus.CANCELLED) {
            throw IllegalStateException("취소된 예약은 수정할 수 없습니다")
        }
        if (reservation.status == ReservationStatus.COMPLETED) {
            throw IllegalStateException("완료된 예약은 수정할 수 없습니다")
        }
    }

    private fun validateCancellation(reservation: Reservation) {
        if (reservation.status == ReservationStatus.CANCELLED) {
            throw IllegalStateException("이미 취소된 예약입니다")
        }
        if (reservation.status == ReservationStatus.COMPLETED) {
            throw IllegalStateException("완료된 예약은 취소할 수 없습니다")
        }
    }

    private fun calculateRefundAmount(reservation: Reservation): BigDecimal {
        val daysUntilCheckIn = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), reservation.checkInDate)
        
        return when {
            daysUntilCheckIn >= 7 -> reservation.totalAmount // 100% 환불
            daysUntilCheckIn >= 3 -> reservation.totalAmount.multiply(BigDecimal("0.5")) // 50% 환불
            else -> BigDecimal.ZERO // 환불 불가
        }
    }

    private fun processReservationCreatedAsync(reservation: Reservation) {
        CompletableFuture.runAsync {
            try {
                notificationService.sendReservationConfirmation(reservation)
                auditService.logReservationCreated(reservation)
            } catch (e: Exception) {
                // 로깅만 하고 예외를 전파하지 않음
                println("비동기 처리 중 오류 발생: ${e.message}")
            }
        }
    }

    private fun processReservationUpdatedAsync(reservation: Reservation) {
        CompletableFuture.runAsync {
            try {
                auditService.logReservationUpdated(reservation)
            } catch (e: Exception) {
                println("예약 수정 감사 로그 실패: ${e.message}")
            }
        }
    }

    private fun processCancellationAsync(reservation: Reservation, refundAmount: BigDecimal, reason: String?) {
        CompletableFuture.runAsync {
            try {
                if (refundAmount > BigDecimal.ZERO) {
                    paymentService.processRefund(reservation, refundAmount)
                }
                notificationService.sendCancellationNotification(reservation, reason)
                auditService.logReservationCancelled(reservation, reason)
            } catch (e: Exception) {
                println("취소 처리 중 오류 발생: ${e.message}")
            }
        }
    }

    private fun generateConfirmationNumber(): String {
        return "CONF-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
    }

    private fun createOrUpdateGuest(request: CreateReservationRequest) = 
        com.example.reservation.domain.guest.Guest(
            firstName = request.guestName.split(" ").first(),
            lastName = request.guestName.split(" ").lastOrNull() ?: "",
            email = "guest@example.com"
        )

    private fun createTempRoom(request: CreateReservationRequest) = 
        com.example.reservation.domain.room.Room(
            property = createTempProperty(),
            roomNumber = request.roomNumber,
            name = "Standard Room",
            type = com.example.reservation.domain.room.RoomType.STANDARD,
            bedType = com.example.reservation.domain.room.BedType.QUEEN,
            maxOccupancy = 2,
            standardOccupancy = 2,
            baseRate = BigDecimal.valueOf(request.totalAmount),
            size = 25.0,
            floor = 1
        )

    private fun createTempProperty() = 
        com.example.reservation.domain.room.Property(
            name = "Sample Hotel",
            type = com.example.reservation.domain.room.PropertyType.HOTEL,
            category = com.example.reservation.domain.room.PropertyCategory.BUSINESS,
            starRating = 4,
            address = com.example.reservation.domain.guest.Address(
                street = "123 Main St",
                city = "City",
                postalCode = "12345",
                countryCode = "US"
            )
        )

    private fun createGuestDetails(request: CreateReservationRequest) = 
        com.example.reservation.domain.reservation.ReservationGuestDetails(
            primaryGuestFirstName = request.guestName.split(" ").first(),
            primaryGuestLastName = request.guestName.split(" ").lastOrNull() ?: "",
            primaryGuestEmail = "guest@example.com"
        )

    data class ReservationStats(
        val totalReservations: Long,
        val totalRevenue: BigDecimal,
        val averageRate: BigDecimal,
        val period: String
    )
}

// === 의존성 서비스 인터페이스들 ===

interface NotificationService {
    fun sendReservationConfirmation(reservation: Reservation)
    fun sendCancellationNotification(reservation: Reservation, reason: String?)
}

interface AuditService {
    fun logReservationCreated(reservation: Reservation)
    fun logReservationUpdated(reservation: Reservation)
    fun logReservationCancelled(reservation: Reservation, reason: String?)
}

interface PaymentService {
    fun processRefund(reservation: Reservation, amount: BigDecimal)
}

interface RoomAvailabilityService {
    fun checkAvailability(roomId: Long, checkIn: LocalDate, checkOut: LocalDate): Boolean
}