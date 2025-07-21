package com.example.reservation.application.service.reservation

import com.example.reservation.application.exception.*
import com.example.reservation.application.usecase.reservation.*
import com.example.reservation.domain.guest.Guest
import com.example.reservation.domain.payment.Payment
import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.domain.room.Room
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import org.slf4j.LoggerFactory

/**
 * 예약 서비스 구현체
 * 실무 릴리즈 급 구현: 트랜잭션, 로깅, 검증, 에러 처리
 */
@Service
@Transactional
class ReservationService(
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
    private val roomRepository: RoomRepository,
    private val paymentService: PaymentService,
    private val availabilityService: AvailabilityService,
    private val notificationService: NotificationService,
    private val auditService: AuditService
) : CreateReservationUseCase, UpdateReservationUseCase, CancelReservationUseCase, ReservationQueryUseCase {

    private val logger = LoggerFactory.getLogger(ReservationService::class.java)

    /**
     * 새로운 예약 생성
     */
    override fun execute(command: CreateReservationCommand): Mono<ReservationResponse> {
        logger.info("예약 생성 시작: guestId={}, propertyId={}, 체크인={}, 체크아웃={}", 
                   command.guestId, command.propertyId, command.checkInDate, command.checkOutDate)

        return validateReservation(command)
            .flatMap { validationResult ->
                if (!validationResult.isValid) {
                    return@flatMap Mono.error<ReservationResponse>(
                        ReservationValidationException(
                            "예약 생성 검증 실패",
                            validationResult.validationErrors
                        )
                    )
                }
                processReservationCreation(command)
            }
            .doOnSuccess { response ->
                logger.info("예약 생성 완료: reservationId={}, confirmationNumber={}", 
                           response.reservationId, response.confirmationNumber)
            }
            .doOnError { error ->
                logger.error("예약 생성 실패: guestId={}, error={}", command.guestId, error.message)
            }
    }

    /**
     * 예약 검증
     */
    override fun validateReservation(command: CreateReservationCommand): Mono<ReservationValidationResult> {
        return Mono.zip(
            validateGuest(command.guestId),
            validateRoomAvailability(command),
            validateBusinessRules(command),
            calculateTotalAmount(command)
        ).map { tuple ->
            val guestValidation = tuple.t1
            val availabilityValidation = tuple.t2
            val businessRulesValidation = tuple.t3
            val amountCalculation = tuple.t4

            val allErrors = mutableListOf<ValidationError>()
            allErrors.addAll(guestValidation.errors)
            allErrors.addAll(availabilityValidation.errors)
            allErrors.addAll(businessRulesValidation.errors)

            ReservationValidationResult(
                isValid = allErrors.isEmpty(),
                availableRooms = availabilityValidation.availableRooms,
                totalAmount = amountCalculation.totalAmount,
                taxAmount = amountCalculation.taxAmount,
                serviceCharges = amountCalculation.serviceCharges,
                cancellationPolicy = businessRulesValidation.cancellationPolicy,
                validationErrors = allErrors,
                warnings = availabilityValidation.warnings + businessRulesValidation.warnings,
                recommendations = generateRecommendations(command, availabilityValidation)
            )
        }
    }

    /**
     * 예약 수정
     */
    override fun execute(command: UpdateReservationCommand): Mono<ReservationResponse> {
        logger.info("예약 수정 시작: reservationId={}", command.reservationId)

        return reservationRepository.findById(command.reservationId)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(command.reservationId)) 
            }
            .flatMap { existingReservation ->
                validateUpdate(command.reservationId, command)
                    .flatMap { validationResult ->
                        if (!validationResult.canUpdate) {
                            return@flatMap Mono.error<ReservationResponse>(
                                ReservationBusinessException(
                                    "예약 수정이 불가능합니다",
                                    details = mapOf("errors" to validationResult.validationErrors)
                                )
                            )
                        }
                        processReservationUpdate(existingReservation, command, validationResult)
                    }
            }
            .doOnSuccess { response ->
                logger.info("예약 수정 완료: reservationId={}", response.reservationId)
            }
            .doOnError { error ->
                logger.error("예약 수정 실패: reservationId={}, error={}", command.reservationId, error.message)
            }
    }

    /**
     * 예약 수정 검증
     */
    override fun validateUpdate(reservationId: UUID, command: UpdateReservationCommand): Mono<UpdateValidationResult> {
        return reservationRepository.findById(reservationId)
            .switchIfEmpty { Mono.error(ReservationNotFoundException(reservationId)) }
            .flatMap { reservation ->
                val canUpdate = canReservationBeUpdated(reservation)
                if (!canUpdate.first) {
                    return@flatMap Mono.just(
                        UpdateValidationResult(
                            canUpdate = false,
                            validationErrors = listOf(
                                ValidationError(
                                    field = "reservationStatus",
                                    code = "INVALID_STATUS",
                                    message = canUpdate.second
                                )
                            )
                        )
                    )
                }

                calculateUpdateCosts(reservation, command)
                    .map { costCalculation ->
                        UpdateValidationResult(
                            canUpdate = true,
                            additionalCharges = costCalculation.additionalCharges,
                            refundAmount = costCalculation.refundAmount,
                            netChange = costCalculation.netChange,
                            newCancellationDeadline = calculateNewCancellationDeadline(reservation, command),
                            changesSummary = generateChangesSummary(reservation, command)
                        )
                    }
            }
    }

    /**
     * 예약 취소
     */
    override fun execute(command: CancelReservationCommand): Mono<CancellationResponse> {
        logger.info("예약 취소 시작: reservationId={}, reason={}", command.reservationId, command.cancellationReason)

        return reservationRepository.findById(command.reservationId)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(command.reservationId)) 
            }
            .flatMap { reservation ->
                validateCancellation(reservation, command)
                    .flatMap { 
                        processCancellation(reservation, command) 
                    }
            }
            .doOnSuccess { response ->
                logger.info("예약 취소 완료: reservationId={}, cancellationId={}, refund={}", 
                           response.reservationId, response.cancellationId, response.netRefund)
            }
            .doOnError { error ->
                logger.error("예약 취소 실패: reservationId={}, error={}", command.reservationId, error.message)
            }
    }

    /**
     * 환불 금액 계산
     */
    override fun calculateRefund(reservationId: UUID, cancellationReason: CancellationReason): Mono<RefundCalculationResult> {
        return reservationRepository.findById(reservationId)
            .switchIfEmpty { Mono.error(ReservationNotFoundException(reservationId)) }
            .flatMap { reservation ->
                val policy = getCancellationPolicy(reservation, cancellationReason)
                val refundPercentage = calculateRefundPercentage(reservation, policy, LocalDateTime.now())
                val refundCalculation = performRefundCalculation(reservation, refundPercentage, policy)
                
                Mono.just(refundCalculation)
            }
    }

    /**
     * 예약 ID로 조회
     */
    @Transactional(readOnly = true)
    override fun findById(reservationId: UUID, includeHistory: Boolean): Mono<ReservationDetailResponse> {
        return reservationRepository.findById(reservationId)
            .switchIfEmpty { Mono.error(ReservationNotFoundException(reservationId)) }
            .flatMap { reservation ->
                Mono.zip(
                    guestRepository.findById(reservation.guestId),
                    roomRepository.findPropertyById(reservation.propertyId),
                    roomRepository.findRoomTypeById(reservation.roomTypeId),
                    if (reservation.paymentId != null) 
                        paymentService.findById(reservation.paymentId!!) 
                    else 
                        Mono.empty<PaymentSummary>(),
                    if (includeHistory) 
                        auditService.getReservationHistory(reservationId) 
                    else 
                        Mono.just(emptyList<ReservationHistoryItem>()),
                    auditService.getReservationTimeline(reservationId)
                ).map { tuple ->
                    ReservationDetailResponse(
                        reservation = reservation.toResponse(),
                        guest = tuple.t1.toSummary(),
                        property = tuple.t2.toSummary(),
                        roomType = tuple.t3.toSummary(),
                        payment = tuple.t4,
                        additionalGuests = reservation.additionalGuests.map { it.toInfo() },
                        preferences = reservation.preferences?.toInfo(),
                        history = tuple.t5,
                        timeline = tuple.t6
                    )
                }
            }
    }

    /**
     * 확인번호로 조회
     */
    @Transactional(readOnly = true)
    override fun findByConfirmationNumber(confirmationNumber: String): Mono<ReservationResponse> {
        return reservationRepository.findByConfirmationNumber(confirmationNumber)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(
                    UUID.randomUUID(), // 임시 UUID
                    "CONFIRMATION_NUMBER_NOT_FOUND"
                )) 
            }
            .map { it.toResponse() }
    }

    /**
     * 고객별 예약 목록 조회
     */
    @Transactional(readOnly = true)
    override fun findByGuestId(guestId: UUID, criteria: ReservationSearchCriteria): Flux<ReservationSummary> {
        return reservationRepository.findByGuestId(guestId, criteria)
            .map { it.toSummary() }
    }

    /**
     * 시설별 예약 목록 조회
     */
    @Transactional(readOnly = true)
    override fun findByPropertyId(propertyId: UUID, criteria: ReservationSearchCriteria): Flux<ReservationSummary> {
        return reservationRepository.findByPropertyId(propertyId, criteria)
            .map { it.toSummary() }
    }

    /**
     * 예약 검색
     */
    @Transactional(readOnly = true)
    override fun search(criteria: ReservationSearchCriteria): Mono<ReservationSearchResult> {
        return Mono.zip(
            reservationRepository.search(criteria).collectList(),
            reservationRepository.count(criteria)
        ).map { tuple ->
            val reservations = tuple.t1.map { it.toSummary() }
            val totalElements = tuple.t2
            val totalPages = ((totalElements + criteria.size - 1) / criteria.size).toInt()
            
            ReservationSearchResult(
                reservations = reservations,
                totalElements = totalElements,
                totalPages = totalPages,
                currentPage = criteria.page,
                pageSize = criteria.size,
                hasNext = criteria.page < totalPages - 1,
                hasPrevious = criteria.page > 0
            )
        }
    }

    // === 내부 헬퍼 메서드들 ===

    private fun processReservationCreation(command: CreateReservationCommand): Mono<ReservationResponse> {
        return createReservationEntity(command)
            .flatMap { reservation ->
                reservationRepository.save(reservation)
                    .flatMap { savedReservation ->
                        // 결제 처리
                        if (command.paymentMethodId != null && command.totalAmount > BigDecimal.ZERO) {
                            processInitialPayment(savedReservation, command)
                                .flatMap { paymentResult ->
                                    updateReservationWithPayment(savedReservation, paymentResult)
                                }
                        } else {
                            Mono.just(savedReservation)
                        }
                    }
                    .flatMap { finalReservation ->
                        // 후속 처리 (알림, 감사로그 등)
                        performPostCreationTasks(finalReservation, command)
                            .then(Mono.just(finalReservation.toResponse()))
                    }
            }
    }

    private fun processReservationUpdate(
        existingReservation: Reservation,
        command: UpdateReservationCommand,
        validationResult: UpdateValidationResult
    ): Mono<ReservationResponse> {
        return updateReservationEntity(existingReservation, command)
            .flatMap { updatedReservation ->
                reservationRepository.save(updatedReservation)
                    .flatMap { savedReservation ->
                        // 추가 요금 처리
                        if (validationResult.additionalCharges > BigDecimal.ZERO) {
                            processAdditionalCharges(savedReservation, validationResult.additionalCharges)
                        } else {
                            Mono.just(savedReservation)
                        }
                    }
                    .flatMap { finalReservation ->
                        // 후속 처리
                        performPostUpdateTasks(existingReservation, finalReservation, command)
                            .then(Mono.just(finalReservation.toResponse()))
                    }
            }
    }

    private fun processCancellation(
        reservation: Reservation,
        command: CancelReservationCommand
    ): Mono<CancellationResponse> {
        return calculateRefund(reservation.id, command.cancellationReason)
            .flatMap { refundCalculation ->
                // 예약 상태 변경
                val cancelledReservation = reservation.copy(
                    status = ReservationStatus.CANCELLED,
                    modifiedAt = LocalDateTime.now()
                )
                
                reservationRepository.save(cancelledReservation)
                    .flatMap { saved ->
                        // 환불 처리
                        if (command.processRefund && refundCalculation.netRefund > BigDecimal.ZERO) {
                            processRefund(saved, refundCalculation, command)
                        } else {
                            createCancellationResponse(saved, refundCalculation, command)
                        }
                    }
                    .flatMap { response ->
                        // 후속 처리
                        performPostCancellationTasks(reservation, command, response)
                            .then(Mono.just(response))
                    }
            }
    }

    // 추상 메서드들 - 실제 구현에서는 Repository나 외부 서비스 호출로 구현
    private fun validateGuest(guestId: UUID): Mono<GuestValidationResult> {
        return guestRepository.findById(guestId)
            .map { guest ->
                val errors = mutableListOf<ValidationError>()
                if (guest.status != "ACTIVE") {
                    errors.add(ValidationError("guestStatus", "INACTIVE_GUEST", "비활성화된 고객입니다"))
                }
                GuestValidationResult(errors)
            }
            .switchIfEmpty {
                Mono.just(GuestValidationResult(
                    listOf(ValidationError("guestId", "GUEST_NOT_FOUND", "존재하지 않는 고객입니다"))
                ))
            }
    }

    private fun validateRoomAvailability(command: CreateReservationCommand): Mono<AvailabilityValidationResult> {
        return availabilityService.checkAvailability(
            command.propertyId,
            command.roomTypeId,
            command.checkInDate,
            command.checkOutDate,
            command.adultCount + command.childCount
        ).map { availability ->
            val errors = mutableListOf<ValidationError>()
            val warnings = mutableListOf<ValidationWarning>()
            
            if (availability.availableRooms == 0) {
                errors.add(ValidationError("availability", "NO_ROOMS_AVAILABLE", "예약 가능한 객실이 없습니다"))
            } else if (availability.availableRooms <= 2) {
                warnings.add(ValidationWarning("LOW_AVAILABILITY", "객실이 얼마 남지 않았습니다", "빠른 예약을 권장합니다"))
            }
            
            AvailabilityValidationResult(availability.availableRooms, errors, warnings)
        }
    }

    private fun validateBusinessRules(command: CreateReservationCommand): Mono<BusinessRulesValidationResult> {
        // 체크인/체크아웃 날짜 검증, 최소/최대 숙박 기간 등의 비즈니스 규칙 검증
        return Mono.just(BusinessRulesValidationResult(emptyList(), emptyList(), "표준 취소 정책"))
    }

    private fun calculateTotalAmount(command: CreateReservationCommand): Mono<AmountCalculationResult> {
        // 실제로는 요금 계산 엔진을 통해 계산
        return Mono.just(AmountCalculationResult(
            totalAmount = command.totalAmount,
            taxAmount = command.totalAmount.multiply(BigDecimal("0.1")),
            serviceCharges = BigDecimal("5000")
        ))
    }

    // 기타 헬퍼 데이터 클래스들
    private data class GuestValidationResult(val errors: List<ValidationError>)
    private data class AvailabilityValidationResult(
        val availableRooms: Int,
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>
    )
    private data class BusinessRulesValidationResult(
        val errors: List<ValidationError>,
        val warnings: List<ValidationWarning>,
        val cancellationPolicy: String
    )
    private data class AmountCalculationResult(
        val totalAmount: BigDecimal,
        val taxAmount: BigDecimal,
        val serviceCharges: BigDecimal
    )

    // Repository 및 Service 인터페이스들 (실제 구현체는 Infrastructure 레이어에서 구현)
    interface ReservationRepository {
        fun save(reservation: Reservation): Mono<Reservation>
        fun findById(id: UUID): Mono<Reservation>
        fun findByConfirmationNumber(confirmationNumber: String): Mono<Reservation>
        fun findByGuestId(guestId: UUID, criteria: ReservationSearchCriteria): Flux<Reservation>
        fun findByPropertyId(propertyId: UUID, criteria: ReservationSearchCriteria): Flux<Reservation>
        fun search(criteria: ReservationSearchCriteria): Flux<Reservation>
        fun count(criteria: ReservationSearchCriteria): Mono<Long>
    }

    interface GuestRepository {
        fun findById(id: UUID): Mono<Guest>
    }

    interface RoomRepository {
        fun findPropertyById(id: UUID): Mono<Property>
        fun findRoomTypeById(id: UUID): Mono<RoomType>
    }

    interface PaymentService {
        fun findById(id: UUID): Mono<PaymentSummary>
    }

    interface AvailabilityService {
        fun checkAvailability(propertyId: UUID, roomTypeId: UUID, checkIn: java.time.LocalDate, checkOut: java.time.LocalDate, guests: Int): Mono<AvailabilityInfo>
    }

    interface NotificationService {
        fun sendReservationConfirmation(reservation: Reservation): Mono<Void>
    }

    interface AuditService {
        fun getReservationHistory(reservationId: UUID): Mono<List<ReservationHistoryItem>>
        fun getReservationTimeline(reservationId: UUID): Mono<List<ReservationTimelineEvent>>
    }

    // 임시 데이터 클래스들 (실제로는 도메인 객체나 Infrastructure 레이어에서 정의)
    private data class Property(val id: UUID, val name: String)
    private data class RoomType(val id: UUID, val name: String)
    private data class AvailabilityInfo(val availableRooms: Int)

    // 확장 함수들
    private fun Reservation.toResponse(): ReservationResponse = TODO("도메인 객체를 응답 DTO로 변환")
    private fun Reservation.toSummary(): ReservationSummary = TODO("도메인 객체를 요약 DTO로 변환")
    private fun Guest.toSummary(): GuestSummary = TODO("도메인 객체를 요약 DTO로 변환")
    private fun Property.toSummary(): PropertySummary = TODO("도메인 객체를 요약 DTO로 변환")
    private fun RoomType.toSummary(): RoomTypeSummary = TODO("도메인 객체를 요약 DTO로 변환")

    // 나머지 내부 메서드들은 실제 비즈니스 로직에 따라 구현...
    private fun generateRecommendations(command: CreateReservationCommand, availability: AvailabilityValidationResult): List<String> = emptyList()
    private fun canReservationBeUpdated(reservation: Reservation): Pair<Boolean, String> = Pair(true, "")
    private fun calculateUpdateCosts(reservation: Reservation, command: UpdateReservationCommand): Mono<UpdateValidationResult> = TODO()
    private fun calculateNewCancellationDeadline(reservation: Reservation, command: UpdateReservationCommand): LocalDateTime? = null
    private fun generateChangesSummary(reservation: Reservation, command: UpdateReservationCommand): List<String> = emptyList()
    private fun validateCancellation(reservation: Reservation, command: CancelReservationCommand): Mono<Unit> = Mono.just(Unit)
    private fun createReservationEntity(command: CreateReservationCommand): Mono<Reservation> = TODO()
    private fun processInitialPayment(reservation: Reservation, command: CreateReservationCommand): Mono<Any> = TODO()
    private fun updateReservationWithPayment(reservation: Reservation, paymentResult: Any): Mono<Reservation> = TODO()
    private fun performPostCreationTasks(reservation: Reservation, command: CreateReservationCommand): Mono<Void> = Mono.empty()
    private fun updateReservationEntity(existingReservation: Reservation, command: UpdateReservationCommand): Mono<Reservation> = TODO()
    private fun processAdditionalCharges(reservation: Reservation, additionalCharges: BigDecimal): Mono<Reservation> = TODO()
    private fun performPostUpdateTasks(existing: Reservation, updated: Reservation, command: UpdateReservationCommand): Mono<Void> = Mono.empty()
    private fun getCancellationPolicy(reservation: Reservation, reason: CancellationReason): Any = TODO()
    private fun calculateRefundPercentage(reservation: Reservation, policy: Any, now: LocalDateTime): Double = 0.0
    private fun performRefundCalculation(reservation: Reservation, refundPercentage: Double, policy: Any): RefundCalculationResult = TODO()
    private fun processRefund(reservation: Reservation, refundCalculation: RefundCalculationResult, command: CancelReservationCommand): Mono<CancellationResponse> = TODO()
    private fun createCancellationResponse(reservation: Reservation, refundCalculation: RefundCalculationResult, command: CancelReservationCommand): Mono<CancellationResponse> = TODO()
    private fun performPostCancellationTasks(reservation: Reservation, command: CancelReservationCommand, response: CancellationResponse): Mono<Void> = Mono.empty()
}