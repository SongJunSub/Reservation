package com.example.reservation.application.port.inbound

import com.example.reservation.application.usecase.reservation.*
import reactor.core.publisher.Mono
import java.util.*

/**
 * 예약 유스케이스 포트 (인바운드)
 * 실무 릴리즈 급 구현: 포트와 어댑터 패턴
 */
interface ReservationUseCase {
    
    /**
     * 예약 생성
     */
    fun createReservation(command: CreateReservationCommand): Mono<ReservationResponse>
    
    /**
     * 예약 수정
     */
    fun updateReservation(command: UpdateReservationCommand): Mono<ReservationResponse>
    
    /**
     * 예약 취소
     */
    fun cancelReservation(command: CancelReservationCommand): Mono<CancellationResponse>
    
    /**
     * 예약 조회
     */
    fun getReservation(reservationId: UUID, includeHistory: Boolean = false): Mono<ReservationDetailResponse>
    
    /**
     * 확인번호로 예약 조회
     */
    fun getReservationByConfirmation(confirmationNumber: String): Mono<ReservationResponse>
    
    /**
     * 예약 검색
     */
    fun searchReservations(criteria: ReservationSearchCriteria): Mono<ReservationSearchResult>
    
    /**
     * 환불 금액 계산
     */
    fun calculateRefund(reservationId: UUID, cancellationReason: CancellationReason): Mono<RefundCalculationResult>
}