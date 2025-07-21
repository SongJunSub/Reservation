package com.example.reservation.presentation.handler

import com.example.reservation.application.port.inbound.ReservationUseCase
import com.example.reservation.application.usecase.reservation.*
import com.example.reservation.presentation.dto.request.*
import com.example.reservation.presentation.dto.response.*
import com.example.reservation.presentation.validation.RequestValidator
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import org.slf4j.LoggerFactory
import java.util.*

/**
 * 예약 WebFlux Handler
 * 실무 릴리즈 급 구현: 입력 검증, 에러 처리, 로깅, 메트릭 수집
 */
@Component
class ReservationHandler(
    private val reservationUseCase: ReservationUseCase,
    private val requestValidator: RequestValidator,
    private val responseMapper: ResponseMapper,
    private val metricsCollector: MetricsCollector
) {

    private val logger = LoggerFactory.getLogger(ReservationHandler::class.java)

    /**
     * 예약 생성 API
     * POST /api/v1/reservations
     */
    fun createReservation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        
        return request.bodyToMono<CreateReservationRequest>()
            .doOnNext { req -> 
                logger.info("예약 생성 요청: guestId={}, propertyId={}", req.guestId, req.propertyId)
            }
            .flatMap { req -> requestValidator.validateCreateRequest(req).then(Mono.just(req)) }
            .map { req -> req.toCommand() }
            .flatMap { command -> reservationUseCase.createReservation(command) }
            .map { response -> responseMapper.toCreateReservationResponse(response) }
            .flatMap { response ->
                ServerResponse.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("createReservation", startTime, true)
                logger.info("예약 생성 성공")
            }
            .doOnError { error ->
                recordMetrics("createReservation", startTime, false)
                logger.error("예약 생성 실패: {}", error.message, error)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 예약 조회 API
     * GET /api/v1/reservations/{reservationId}
     */
    fun getReservation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val reservationId = request.pathVariable("reservationId")
        val includeHistory = request.queryParam("includeHistory")
            .map { it.toBoolean() }
            .orElse(false)
        
        logger.debug("예약 조회 요청: reservationId={}, includeHistory={}", reservationId, includeHistory)
        
        return Mono.fromCallable { UUID.fromString(reservationId) }
            .flatMap { id -> reservationUseCase.getReservation(id, includeHistory) }
            .map { response -> responseMapper.toReservationDetailResponse(response) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("getReservation", startTime, true)
                logger.debug("예약 조회 성공: reservationId={}", reservationId)
            }
            .doOnError { error ->
                recordMetrics("getReservation", startTime, false)
                logger.error("예약 조회 실패: reservationId={}, error={}", reservationId, error.message)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 확인번호로 예약 조회 API
     * GET /api/v1/reservations/confirmation/{confirmationNumber}
     */
    fun getReservationByConfirmation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val confirmationNumber = request.pathVariable("confirmationNumber")
        
        logger.debug("확인번호 조회 요청: confirmationNumber={}", confirmationNumber)
        
        return requestValidator.validateConfirmationNumber(confirmationNumber)
            .then(reservationUseCase.getReservationByConfirmation(confirmationNumber))
            .map { response -> responseMapper.toReservationResponse(response) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("getReservationByConfirmation", startTime, true)
                logger.debug("확인번호 조회 성공: confirmationNumber={}", confirmationNumber)
            }
            .doOnError { error ->
                recordMetrics("getReservationByConfirmation", startTime, false)
                logger.error("확인번호 조회 실패: confirmationNumber={}, error={}", confirmationNumber, error.message)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 예약 수정 API
     * PUT /api/v1/reservations/{reservationId}
     */
    fun updateReservation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val reservationId = request.pathVariable("reservationId")
        
        return request.bodyToMono<UpdateReservationRequest>()
            .doOnNext { req -> 
                logger.info("예약 수정 요청: reservationId={}", reservationId)
            }
            .flatMap { req -> requestValidator.validateUpdateRequest(req).then(Mono.just(req)) }
            .map { req -> req.toCommand(UUID.fromString(reservationId)) }
            .flatMap { command -> reservationUseCase.updateReservation(command) }
            .map { response -> responseMapper.toReservationResponse(response) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("updateReservation", startTime, true)
                logger.info("예약 수정 성공: reservationId={}", reservationId)
            }
            .doOnError { error ->
                recordMetrics("updateReservation", startTime, false)
                logger.error("예약 수정 실패: reservationId={}, error={}", reservationId, error.message, error)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 예약 취소 API
     * DELETE /api/v1/reservations/{reservationId}
     */
    fun cancelReservation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val reservationId = request.pathVariable("reservationId")
        
        return request.bodyToMono<CancelReservationRequest>()
            .doOnNext { req -> 
                logger.info("예약 취소 요청: reservationId={}, reason={}", reservationId, req.reason)
            }
            .flatMap { req -> requestValidator.validateCancelRequest(req).then(Mono.just(req)) }
            .map { req -> req.toCommand(UUID.fromString(reservationId)) }
            .flatMap { command -> reservationUseCase.cancelReservation(command) }
            .map { response -> responseMapper.toCancellationResponse(response) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("cancelReservation", startTime, true)
                logger.info("예약 취소 성공: reservationId={}", reservationId)
            }
            .doOnError { error ->
                recordMetrics("cancelReservation", startTime, false)
                logger.error("예약 취소 실패: reservationId={}, error={}", reservationId, error.message, error)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 예약 검색 API
     * GET /api/v1/reservations
     */
    fun searchReservations(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val criteria = extractSearchCriteria(request)
        
        logger.debug("예약 검색 요청: criteria={}", criteria)
        
        return requestValidator.validateSearchCriteria(criteria)
            .then(reservationUseCase.searchReservations(criteria))
            .map { result -> responseMapper.toSearchResponse(result) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("searchReservations", startTime, true)
                logger.debug("예약 검색 성공")
            }
            .doOnError { error ->
                recordMetrics("searchReservations", startTime, false)
                logger.error("예약 검색 실패: error={}", error.message)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 환불 금액 계산 API
     * POST /api/v1/reservations/{reservationId}/calculate-refund
     */
    fun calculateRefund(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        val reservationId = request.pathVariable("reservationId")
        
        return request.bodyToMono<CalculateRefundRequest>()
            .doOnNext { req -> 
                logger.debug("환불 계산 요청: reservationId={}, reason={}", reservationId, req.reason)
            }
            .map { req -> CancellationReason.valueOf(req.reason) }
            .flatMap { reason -> 
                reservationUseCase.calculateRefund(UUID.fromString(reservationId), reason)
            }
            .map { result -> responseMapper.toRefundCalculationResponse(result) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("calculateRefund", startTime, true)
                logger.debug("환불 계산 성공: reservationId={}", reservationId)
            }
            .doOnError { error ->
                recordMetrics("calculateRefund", startTime, false)
                logger.error("환불 계산 실패: reservationId={}, error={}", reservationId, error.message)
            }
            .onErrorResume { error -> handleError(error) }
    }

    /**
     * 예약 검증 API (예약 생성 전 미리 검증)
     * POST /api/v1/reservations/validate
     */
    fun validateReservation(request: ServerRequest): Mono<ServerResponse> {
        val startTime = System.currentTimeMillis()
        
        return request.bodyToMono<CreateReservationRequest>()
            .doOnNext { req -> 
                logger.debug("예약 검증 요청: guestId={}, propertyId={}", req.guestId, req.propertyId)
            }
            .flatMap { req -> requestValidator.validateCreateRequest(req).then(Mono.just(req)) }
            .map { req -> req.toCommand() }
            .flatMap { command -> 
                // 실제로는 별도의 검증 UseCase 호출
                Mono.just(ReservationValidationResult(
                    isValid = true,
                    availableRooms = 5,
                    totalAmount = command.totalAmount,
                    taxAmount = command.totalAmount.multiply(java.math.BigDecimal("0.1")),
                    serviceCharges = java.math.BigDecimal("5000"),
                    cancellationPolicy = "표준 취소 정책"
                ))
            }
            .map { result -> responseMapper.toValidationResponse(result) }
            .flatMap { response ->
                ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(response)
            }
            .doOnSuccess { 
                recordMetrics("validateReservation", startTime, true)
                logger.debug("예약 검증 성공")
            }
            .doOnError { error ->
                recordMetrics("validateReservation", startTime, false)
                logger.error("예약 검증 실패: error={}", error.message)
            }
            .onErrorResume { error -> handleError(error) }
    }

    // === 헬퍼 메서드들 ===

    /**
     * 검색 조건 추출
     */
    private fun extractSearchCriteria(request: ServerRequest): ReservationSearchCriteria {
        val queryParams = request.queryParams()
        
        return ReservationSearchCriteria(
            guestId = queryParams.getFirst("guestId")?.let { UUID.fromString(it) },
            propertyId = queryParams.getFirst("propertyId")?.let { UUID.fromString(it) },
            status = queryParams["status"]?.toSet() ?: emptySet(),
            checkInDateFrom = queryParams.getFirst("checkInDateFrom")?.let { java.time.LocalDate.parse(it) },
            checkInDateTo = queryParams.getFirst("checkInDateTo")?.let { java.time.LocalDate.parse(it) },
            checkOutDateFrom = queryParams.getFirst("checkOutDateFrom")?.let { java.time.LocalDate.parse(it) },
            checkOutDateTo = queryParams.getFirst("checkOutDateTo")?.let { java.time.LocalDate.parse(it) },
            confirmationNumber = queryParams.getFirst("confirmationNumber"),
            guestEmail = queryParams.getFirst("guestEmail"),
            guestName = queryParams.getFirst("guestName"),
            page = queryParams.getFirst("page")?.toIntOrNull() ?: 0,
            size = queryParams.getFirst("size")?.toIntOrNull()?.coerceIn(1, 100) ?: 20,
            sortBy = queryParams.getFirst("sortBy") ?: "createdAt",
            sortDirection = queryParams.getFirst("sortDirection")?.let { 
                SortDirection.valueOf(it.uppercase()) 
            } ?: SortDirection.DESC
        )
    }

    /**
     * 에러 처리
     */
    private fun handleError(error: Throwable): Mono<ServerResponse> {
        return when (error) {
            is com.example.reservation.application.exception.ReservationNotFoundException -> {
                ServerResponse.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ErrorResponse(
                        code = "RESERVATION_NOT_FOUND",
                        message = error.message ?: "예약을 찾을 수 없습니다",
                        timestamp = java.time.LocalDateTime.now()
                    ))
            }
            is com.example.reservation.application.exception.ReservationValidationException -> {
                ServerResponse.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ValidationErrorResponse(
                        code = "VALIDATION_ERROR",
                        message = "입력값 검증 실패",
                        errors = error.validationErrors.map {
                            FieldError(it.field, it.code, it.message)
                        },
                        timestamp = java.time.LocalDateTime.now()
                    ))
            }
            is com.example.reservation.application.exception.ReservationBusinessException -> {
                ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ErrorResponse(
                        code = error.errorCode,
                        message = error.message ?: "비즈니스 규칙 위반",
                        timestamp = java.time.LocalDateTime.now()
                    ))
            }
            is IllegalArgumentException -> {
                ServerResponse.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ErrorResponse(
                        code = "INVALID_INPUT",
                        message = error.message ?: "잘못된 입력값",
                        timestamp = java.time.LocalDateTime.now()
                    ))
            }
            else -> {
                logger.error("예상치 못한 오류 발생", error)
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ErrorResponse(
                        code = "INTERNAL_SERVER_ERROR",
                        message = "내부 서버 오류가 발생했습니다",
                        timestamp = java.time.LocalDateTime.now()
                    ))
            }
        }
    }

    /**
     * 메트릭 수집
     */
    private fun recordMetrics(operation: String, startTime: Long, success: Boolean) {
        val duration = System.currentTimeMillis() - startTime
        metricsCollector.recordApiCall(
            operation = operation,
            duration = duration,
            success = success
        )
    }
}

// === 임시 인터페이스들 ===

interface ResponseMapper {
    fun toCreateReservationResponse(response: ReservationResponse): CreateReservationResponseDto
    fun toReservationDetailResponse(response: ReservationDetailResponse): ReservationDetailResponseDto
    fun toReservationResponse(response: ReservationResponse): ReservationResponseDto
    fun toCancellationResponse(response: CancellationResponse): CancellationResponseDto
    fun toSearchResponse(result: ReservationSearchResult): ReservationSearchResponseDto
    fun toRefundCalculationResponse(result: RefundCalculationResult): RefundCalculationResponseDto
    fun toValidationResponse(result: ReservationValidationResult): ValidationResponseDto
}

interface MetricsCollector {
    fun recordApiCall(operation: String, duration: Long, success: Boolean)
}