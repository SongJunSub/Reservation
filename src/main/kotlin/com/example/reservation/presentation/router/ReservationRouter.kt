package com.example.reservation.presentation.router

import com.example.reservation.presentation.handler.ReservationHandler
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.RouterOperation
import org.springdoc.core.annotations.RouterOperations
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.reactive.function.server.*

/**
 * 예약 라우터 설정
 * 실무 릴리즈 급 구현: RESTful API 설계, OpenAPI 문서화, 버전 관리
 */
@Configuration
@Tag(name = "Reservation API", description = "예약 관리 API")
class ReservationRouter {

    /**
     * 예약 관련 라우트 설정
     */
    @Bean
    @RouterOperations(
        RouterOperation(
            path = "/api/v1/reservations",
            method = [RequestMethod.POST],
            operation = Operation(
                operationId = "createReservation",
                summary = "예약 생성",
                description = "새로운 예약을 생성합니다",
                tags = ["Reservation API"],
                requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "예약 생성 요청 데이터",
                    required = true,
                    content = [Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = com.example.reservation.presentation.dto.request.CreateReservationRequest::class)
                    )]
                ),
                responses = [
                    ApiResponse(
                        responseCode = "201",
                        description = "예약 생성 성공",
                        content = [Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = com.example.reservation.presentation.dto.response.CreateReservationResponseDto::class)
                        )]
                    ),
                    ApiResponse(
                        responseCode = "400",
                        description = "잘못된 요청",
                        content = [Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = com.example.reservation.presentation.dto.response.ValidationErrorResponse::class)
                        )]
                    ),
                    ApiResponse(
                        responseCode = "422",
                        description = "비즈니스 규칙 위반",
                        content = [Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = com.example.reservation.presentation.dto.response.ErrorResponse::class)
                        )]
                    )
                ]
            )
        ),
        RouterOperation(
            path = "/api/v1/reservations/{reservationId}",
            method = [RequestMethod.GET],
            operation = Operation(
                operationId = "getReservation",
                summary = "예약 조회",
                description = "예약 ID로 예약 정보를 조회합니다",
                tags = ["Reservation API"],
                parameters = [
                    Parameter(name = "reservationId", description = "예약 ID", required = true),
                    Parameter(name = "includeHistory", description = "변경 이력 포함 여부", required = false, schema = Schema(type = "boolean"))
                ],
                responses = [
                    ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content = [Content(
                            mediaType = "application/json",
                            schema = Schema(implementation = com.example.reservation.presentation.dto.response.ReservationDetailResponseDto::class)
                        )]
                    ),
                    ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음")
                ]
            )
        )
    )
    fun reservationRoutes(reservationHandler: ReservationHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v1/reservations") { builder ->
                builder
                    // 예약 생성
                    .POST("", accept(MediaType.APPLICATION_JSON), reservationHandler::createReservation)
                    
                    // 예약 검증 (생성 전 미리 검증)
                    .POST("/validate", accept(MediaType.APPLICATION_JSON), reservationHandler::validateReservation)
                    
                    // 예약 검색
                    .GET("", reservationHandler::searchReservations)
                    
                    // 확인번호로 예약 조회
                    .GET("/confirmation/{confirmationNumber}", reservationHandler::getReservationByConfirmation)
                    
                    // 특정 예약 조회
                    .GET("/{reservationId}", reservationHandler::getReservation)
                    
                    // 예약 수정
                    .PUT("/{reservationId}", accept(MediaType.APPLICATION_JSON), reservationHandler::updateReservation)
                    
                    // 예약 취소
                    .DELETE("/{reservationId}", accept(MediaType.APPLICATION_JSON), reservationHandler::cancelReservation)
                    
                    // 환불 금액 계산
                    .POST("/{reservationId}/calculate-refund", accept(MediaType.APPLICATION_JSON), reservationHandler::calculateRefund)
            }
            .build()
    }

    /**
     * 고객 관련 라우트 설정
     */
    @Bean
    fun guestRoutes(guestHandler: GuestHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v1/guests") { builder ->
                builder
                    // 고객 등록
                    .POST("", accept(MediaType.APPLICATION_JSON), guestHandler::createGuest)
                    
                    // 고객 조회
                    .GET("/{guestId}", guestHandler::getGuest)
                    
                    // 고객 정보 수정
                    .PUT("/{guestId}", accept(MediaType.APPLICATION_JSON), guestHandler::updateGuest)
                    
                    // 고객 검색
                    .GET("", guestHandler::searchGuests)
                    
                    // 고객별 예약 목록
                    .GET("/{guestId}/reservations", guestHandler::getGuestReservations)
                    
                    // 고객 계정 비활성화
                    .DELETE("/{guestId}", guestHandler::deactivateGuest)
                    
                    // GDPR 데이터 삭제 요청
                    .DELETE("/{guestId}/data", guestHandler::deleteGuestData)
            }
            .build()
    }

    /**
     * 결제 관련 라우트 설정
     */
    @Bean
    fun paymentRoutes(paymentHandler: PaymentHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v1/payments") { builder ->
                builder
                    // 결제 처리
                    .POST("", accept(MediaType.APPLICATION_JSON), paymentHandler::processPayment)
                    
                    // 결제 승인
                    .POST("/{paymentId}/capture", accept(MediaType.APPLICATION_JSON), paymentHandler::capturePayment)
                    
                    // 결제 환불
                    .POST("/{paymentId}/refund", accept(MediaType.APPLICATION_JSON), paymentHandler::refundPayment)
                    
                    // 결제 취소
                    .DELETE("/{paymentId}", paymentHandler::cancelPayment)
                    
                    // 결제 상태 조회
                    .GET("/{paymentId}/status", paymentHandler::getPaymentStatus)
                    
                    // 결제 내역 조회
                    .GET("", paymentHandler::getPaymentHistory)
            }
            .build()
    }

    /**
     * 객실/시설 관련 라우트 설정
     */
    @Bean
    fun roomRoutes(roomHandler: RoomHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v1/rooms") { builder ->
                builder
                    // 객실 가용성 조회
                    .GET("/availability", roomHandler::checkAvailability)
                    
                    // 객실 유형별 가용성 조회
                    .GET("/{roomTypeId}/availability", roomHandler::getRoomTypeAvailability)
                    
                    // 재고 업데이트 (관리자용)
                    .PUT("/inventory", accept(MediaType.APPLICATION_JSON), roomHandler::updateInventory)
                    
                    // 요금 업데이트 (관리자용)
                    .PUT("/rates", accept(MediaType.APPLICATION_JSON), roomHandler::updateRates)
                    
                    // 가용성 캘린더 조회
                    .GET("/calendar", roomHandler::getAvailabilityCalendar)
            }
            .build()
    }

    /**
     * 관리자 관련 라우트 설정
     */
    @Bean
    fun adminRoutes(adminHandler: AdminHandler): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v1/admin") { builder ->
                builder
                    // 대시보드 통계
                    .GET("/dashboard", adminHandler::getDashboardStats)
                    
                    // 당일 도착/출발 목록
                    .GET("/arrivals/today", adminHandler::getTodayArrivals)
                    .GET("/departures/today", adminHandler::getTodayDepartures)
                    
                    // 수익 분석
                    .GET("/analytics/revenue", adminHandler::getRevenueAnalytics)
                    
                    // 점유율 분석
                    .GET("/analytics/occupancy", adminHandler::getOccupancyAnalytics)
                    
                    // 배치 작업 (만료된 예약 정리 등)
                    .POST("/batch/cleanup-expired", adminHandler::cleanupExpiredReservations)
                    .POST("/batch/process-no-shows", adminHandler::processNoShows)
                    .POST("/batch/auto-checkout", adminHandler::autoCheckout)
                    
                    // 시스템 상태
                    .GET("/system/health", adminHandler::getSystemHealth)
                    .GET("/system/metrics", adminHandler::getSystemMetrics)
            }
            .build()
    }

    /**
     * API 버전 2 라우트 (미래 확장용)
     */
    @Bean
    fun v2Routes(): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            .path("/api/v2") { builder ->
                builder
                    .GET("/reservations", { request ->
                        ServerResponse.ok().bodyValue(mapOf(
                            "message" to "API v2는 아직 개발 중입니다",
                            "version" to "2.0.0-SNAPSHOT",
                            "availableFrom" to "2026-01-01"
                        ))
                    })
            }
            .build()
    }

    /**
     * 헬스 체크 및 정보 라우트
     */
    @Bean
    fun utilityRoutes(): RouterFunction<ServerResponse> {
        return RouterFunctions.route()
            // API 정보
            .GET("/api/info") { request ->
                ServerResponse.ok().bodyValue(mapOf(
                    "service" to "Reservation System API",
                    "version" to "1.0.0",
                    "description" to "호스피탈리티 예약 관리 시스템",
                    "contact" to mapOf(
                        "name" to "Development Team",
                        "email" to "dev@reservation.com"
                    ),
                    "documentation" to "/swagger-ui.html"
                ))
            }
            // 간단한 헬스 체크
            .GET("/health") { request ->
                ServerResponse.ok().bodyValue(mapOf(
                    "status" to "UP",
                    "timestamp" to java.time.LocalDateTime.now().toString()
                ))
            }
            // API 버전 정보
            .GET("/api/version") { request ->
                ServerResponse.ok().bodyValue(mapOf(
                    "current" to "v1",
                    "supported" to listOf("v1"),
                    "deprecated" to emptyList<String>(),
                    "sunset" to emptyMap<String, String>()
                ))
            }
            .build()
    }

    /**
     * 전역 필터 설정
     */
    @Bean
    fun globalFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> {
        return HandlerFilterFunction { request, next ->
            val startTime = System.currentTimeMillis()
            
            next.handle(request)
                .doOnTerminate {
                    val duration = System.currentTimeMillis() - startTime
                    val method = request.method()
                    val path = request.path()
                    println("API 호출: $method $path - ${duration}ms")
                }
        }
    }

    /**
     * CORS 설정
     */
    @Bean
    fun corsFilter(): HandlerFilterFunction<ServerResponse, ServerResponse> {
        return HandlerFilterFunction { request, next ->
            next.handle(request)
                .flatMap { response ->
                    ServerResponse.from(response)
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                        .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                        .header("Access-Control-Max-Age", "3600")
                        .build()
                }
        }
    }
}

// === 임시 핸들러 인터페이스들 (실제로는 별도 파일에 구현) ===

interface GuestHandler {
    fun createGuest(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getGuest(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun updateGuest(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun searchGuests(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getGuestReservations(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun deactivateGuest(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun deleteGuestData(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
}

interface PaymentHandler {
    fun processPayment(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun capturePayment(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun refundPayment(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun cancelPayment(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getPaymentStatus(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getPaymentHistory(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
}

interface RoomHandler {
    fun checkAvailability(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getRoomTypeAvailability(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun updateInventory(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun updateRates(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getAvailabilityCalendar(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
}

interface AdminHandler {
    fun getDashboardStats(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getTodayArrivals(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getTodayDepartures(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getRevenueAnalytics(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getOccupancyAnalytics(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun cleanupExpiredReservations(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun processNoShows(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun autoCheckout(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getSystemHealth(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
    fun getSystemMetrics(request: ServerRequest): reactor.core.publisher.Mono<ServerResponse>
}