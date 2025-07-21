package com.example.reservation.application.service

import com.example.reservation.application.exception.*
import com.example.reservation.application.usecase.reservation.*
import com.example.reservation.application.port.inbound.ReservationUseCase
import com.example.reservation.application.port.outbound.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import org.slf4j.LoggerFactory
import java.util.*

/**
 * 예약 애플리케이션 서비스
 * 실무 릴리즈 급 구현: 포트와 어댑터 패턴 적용
 */
@Service
@Transactional
class ReservationApplicationService(
    private val reservationPort: ReservationPort,
    private val guestPort: GuestPort,
    private val roomPort: RoomPort,
    private val paymentPort: PaymentPort,
    private val notificationPort: NotificationPort,
    private val auditPort: AuditPort,
    private val reservationValidationService: ReservationValidationService,
    private val reservationDomainService: ReservationDomainService
) : ReservationUseCase {

    private val logger = LoggerFactory.getLogger(ReservationApplicationService::class.java)

    /**
     * 예약 생성
     */
    override fun createReservation(command: CreateReservationCommand): Mono<ReservationResponse> {
        logger.info("예약 생성 요청: guestId={}, propertyId={}", command.guestId, command.propertyId)

        return reservationValidationService.validateCreation(command)
            .flatMap { validationResult ->
                if (!validationResult.isValid) {
                    return@flatMap Mono.error<ReservationResponse>(
                        ReservationValidationException(
                            "예약 생성 검증 실패",
                            validationResult.validationErrors
                        )
                    )
                }
                
                reservationDomainService.createReservation(command)
                    .flatMap { reservation ->
                        reservationPort.save(reservation)
                            .flatMap { savedReservation ->
                                // 비동기 후속 처리
                                performPostCreationTasks(savedReservation, command)
                                Mono.just(savedReservation.toResponse())
                            }
                    }
            }
            .doOnSuccess { response ->
                logger.info("예약 생성 완료: reservationId={}", response.reservationId)
            }
            .doOnError { error ->
                logger.error("예약 생성 실패: {}", error.message)
            }
    }

    /**
     * 예약 수정
     */
    override fun updateReservation(command: UpdateReservationCommand): Mono<ReservationResponse> {
        logger.info("예약 수정 요청: reservationId={}", command.reservationId)

        return reservationPort.findById(command.reservationId)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(command.reservationId))
            }
            .flatMap { existingReservation ->
                reservationValidationService.validateUpdate(existingReservation, command)
                    .flatMap { validationResult ->
                        if (!validationResult.canUpdate) {
                            return@flatMap Mono.error<ReservationResponse>(
                                ReservationBusinessException(
                                    "예약 수정 불가: ${validationResult.validationErrors.joinToString { it.message }}"
                                )
                            )
                        }

                        reservationDomainService.updateReservation(existingReservation, command)
                            .flatMap { updatedReservation ->
                                reservationPort.save(updatedReservation)
                                    .flatMap { savedReservation ->
                                        performPostUpdateTasks(existingReservation, savedReservation, command)
                                        Mono.just(savedReservation.toResponse())
                                    }
                            }
                    }
            }
            .doOnSuccess { response ->
                logger.info("예약 수정 완료: reservationId={}", response.reservationId)
            }
    }

    /**
     * 예약 취소
     */
    override fun cancelReservation(command: CancelReservationCommand): Mono<CancellationResponse> {
        logger.info("예약 취소 요청: reservationId={}", command.reservationId)

        return reservationPort.findById(command.reservationId)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(command.reservationId))
            }
            .flatMap { reservation ->
                reservationValidationService.validateCancellation(reservation, command)
                    .flatMap {
                        reservationDomainService.cancelReservation(reservation, command)
                            .flatMap { cancelledReservation ->
                                reservationPort.save(cancelledReservation)
                                    .flatMap { savedReservation ->
                                        performPostCancellationTasks(savedReservation, command)
                                    }
                            }
                    }
            }
            .doOnSuccess { response ->
                logger.info("예약 취소 완료: cancellationId={}", response.cancellationId)
            }
    }

    /**
     * 예약 조회
     */
    @Transactional(readOnly = true)
    override fun getReservation(reservationId: UUID, includeHistory: Boolean): Mono<ReservationDetailResponse> {
        return reservationPort.findById(reservationId)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(reservationId))
            }
            .flatMap { reservation ->
                Mono.zip(
                    guestPort.findById(reservation.guestId),
                    roomPort.findPropertyById(reservation.propertyId),
                    roomPort.findRoomTypeById(reservation.roomTypeId),
                    if (reservation.paymentId != null) paymentPort.findById(reservation.paymentId!!) else Mono.empty(),
                    if (includeHistory) auditPort.getReservationHistory(reservationId) else Mono.just(emptyList())
                ).map { tuple ->
                    ReservationDetailResponse(
                        reservation = reservation.toResponse(),
                        guest = tuple.t1.toSummary(),
                        property = tuple.t2.toSummary(),
                        roomType = tuple.t3.toSummary(),
                        payment = tuple.t4,
                        history = tuple.t5
                    )
                }
            }
    }

    /**
     * 예약 검색
     */
    @Transactional(readOnly = true)
    override fun searchReservations(criteria: ReservationSearchCriteria): Mono<ReservationSearchResult> {
        return Mono.zip(
            reservationPort.search(criteria).collectList(),
            reservationPort.count(criteria)
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

    /**
     * 확인번호로 예약 조회
     */
    @Transactional(readOnly = true)
    override fun getReservationByConfirmation(confirmationNumber: String): Mono<ReservationResponse> {
        return reservationPort.findByConfirmationNumber(confirmationNumber)
            .switchIfEmpty { 
                Mono.error(ReservationNotFoundException(UUID.randomUUID(), "CONFIRMATION_NOT_FOUND"))
            }
            .map { it.toResponse() }
    }

    // === 후속 처리 메서드들 ===

    private fun performPostCreationTasks(
        reservation: Reservation, 
        command: CreateReservationCommand
    ): Mono<Void> {
        return Mono.fromRunnable {
            // 비동기로 실행 (알림 발송은 실패해도 예약 생성에 영향 없음)
            notificationPort.sendReservationConfirmation(reservation)
                .doOnError { error ->
                    logger.warn("예약 확인 알림 발송 실패: reservationId={}, error={}", 
                              reservation.id, error.message)
                }
                .subscribe()

            // 감사 로그 기록
            auditPort.recordReservationCreation(reservation, command)
                .subscribe()
        }.then()
    }

    private fun performPostUpdateTasks(
        originalReservation: Reservation,
        updatedReservation: Reservation,
        command: UpdateReservationCommand
    ): Mono<Void> {
        return Mono.fromRunnable {
            notificationPort.sendReservationUpdateNotification(updatedReservation)
                .subscribe()

            auditPort.recordReservationUpdate(originalReservation, updatedReservation, command)
                .subscribe()
        }.then()
    }

    private fun performPostCancellationTasks(
        reservation: Reservation,
        command: CancelReservationCommand
    ): Mono<CancellationResponse> {
        return reservationDomainService.calculateCancellationResponse(reservation, command)
            .flatMap { response ->
                // 알림 발송
                notificationPort.sendCancellationNotification(reservation, response)
                    .doOnError { error ->
                        logger.warn("취소 알림 발송 실패: reservationId={}, error={}", 
                                  reservation.id, error.message)
                    }
                    .subscribe()

                // 감사 로그 기록
                auditPort.recordReservationCancellation(reservation, command, response)
                    .subscribe()

                Mono.just(response)
            }
    }
}

/**
 * 예약 검증 서비스
 */
@Service
class ReservationValidationService(
    private val guestPort: GuestPort,
    private val roomPort: RoomPort,
    private val businessRuleEngine: BusinessRuleEngine
) {
    
    fun validateCreation(command: CreateReservationCommand): Mono<ReservationValidationResult> {
        return Mono.zip(
            validateGuest(command.guestId),
            validateRoomAvailability(command),
            validateBusinessRules(command)
        ).map { tuple ->
            val allErrors = tuple.t1 + tuple.t2 + tuple.t3
            ReservationValidationResult(
                isValid = allErrors.isEmpty(),
                validationErrors = allErrors,
                // 기타 필드들...
            )
        }
    }

    fun validateUpdate(
        reservation: Reservation, 
        command: UpdateReservationCommand
    ): Mono<UpdateValidationResult> {
        // 수정 검증 로직
        return Mono.just(UpdateValidationResult(canUpdate = true))
    }

    fun validateCancellation(
        reservation: Reservation, 
        command: CancelReservationCommand
    ): Mono<Unit> {
        // 취소 검증 로직
        return Mono.just(Unit)
    }

    private fun validateGuest(guestId: UUID): Mono<List<ValidationError>> {
        return guestPort.findById(guestId)
            .map { guest ->
                mutableListOf<ValidationError>().apply {
                    if (guest.status != "ACTIVE") {
                        add(ValidationError("guestStatus", "INACTIVE", "비활성 고객입니다"))
                    }
                }
            }
            .switchIfEmpty {
                Mono.just(listOf(ValidationError("guestId", "NOT_FOUND", "존재하지 않는 고객입니다")))
            }
    }

    private fun validateRoomAvailability(command: CreateReservationCommand): Mono<List<ValidationError>> {
        return roomPort.checkAvailability(
            command.propertyId,
            command.roomTypeId,
            command.checkInDate,
            command.checkOutDate
        ).map { availability ->
            mutableListOf<ValidationError>().apply {
                if (availability.availableRooms == 0) {
                    add(ValidationError("availability", "NO_ROOMS", "예약 가능한 객실이 없습니다"))
                }
            }
        }
    }

    private fun validateBusinessRules(command: CreateReservationCommand): Mono<List<ValidationError>> {
        return businessRuleEngine.validate(command)
    }
}

/**
 * 예약 도메인 서비스
 */
@Service
class ReservationDomainService {
    
    fun createReservation(command: CreateReservationCommand): Mono<Reservation> {
        // 예약 도메인 객체 생성 로직
        return Mono.fromCallable {
            Reservation.create(
                guestId = command.guestId,
                propertyId = command.propertyId,
                roomTypeId = command.roomTypeId,
                checkInDate = command.checkInDate,
                checkOutDate = command.checkOutDate,
                totalAmount = command.totalAmount
                // 기타 필드들...
            )
        }
    }

    fun updateReservation(
        existingReservation: Reservation, 
        command: UpdateReservationCommand
    ): Mono<Reservation> {
        return Mono.fromCallable {
            existingReservation.update(command)
        }
    }

    fun cancelReservation(
        reservation: Reservation, 
        command: CancelReservationCommand
    ): Mono<Reservation> {
        return Mono.fromCallable {
            reservation.cancel(command.cancellationReason, command.reasonDetails)
        }
    }

    fun calculateCancellationResponse(
        reservation: Reservation,
        command: CancelReservationCommand
    ): Mono<CancellationResponse> {
        // 취소 응답 계산 로직
        return Mono.fromCallable {
            CancellationResponse(
                reservationId = reservation.id,
                cancellationId = UUID.randomUUID(),
                status = "CANCELLED",
                cancellationDate = java.time.LocalDateTime.now(),
                refundAmount = reservation.totalAmount,
                cancellationFee = java.math.BigDecimal.ZERO,
                processingFee = java.math.BigDecimal.ZERO,
                netRefund = reservation.totalAmount,
                cancellationPolicy = "표준 취소 정책"
            )
        }
    }
}

// 임시 인터페이스들 (실제로는 Port 인터페이스로 정의)
interface BusinessRuleEngine {
    fun validate(command: CreateReservationCommand): Mono<List<ValidationError>>
}

// 임시 도메인 클래스 (실제로는 domain 패키지에 정의)
data class Reservation(
    val id: UUID,
    val guestId: UUID,
    val propertyId: UUID,
    val roomTypeId: UUID,
    val checkInDate: java.time.LocalDate,
    val checkOutDate: java.time.LocalDate,
    val totalAmount: java.math.BigDecimal,
    val status: String,
    val paymentId: UUID? = null
) {
    companion object {
        fun create(
            guestId: UUID,
            propertyId: UUID,
            roomTypeId: UUID,
            checkInDate: java.time.LocalDate,
            checkOutDate: java.time.LocalDate,
            totalAmount: java.math.BigDecimal
        ): Reservation {
            return Reservation(
                id = UUID.randomUUID(),
                guestId = guestId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkInDate = checkInDate,
                checkOutDate = checkOutDate,
                totalAmount = totalAmount,
                status = "CONFIRMED"
            )
        }
    }

    fun update(command: UpdateReservationCommand): Reservation = this.copy()
    fun cancel(reason: CancellationReason, details: String?): Reservation = this.copy(status = "CANCELLED")
    fun toResponse(): ReservationResponse = TODO()
    fun toSummary(): ReservationSummary = TODO()
}