package com.example.reservation.application.port.outbound

import com.example.reservation.application.usecase.reservation.ReservationSearchCriteria
import com.example.reservation.application.service.Reservation
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * 예약 포트 (아웃바운드) - 데이터 액세스
 * 실무 릴리즈 급 구현: 리포지토리 추상화
 */
interface ReservationPort {
    
    /**
     * 예약 저장
     */
    fun save(reservation: Reservation): Mono<Reservation>
    
    /**
     * ID로 예약 조회
     */
    fun findById(id: UUID): Mono<Reservation>
    
    /**
     * 확인번호로 예약 조회
     */
    fun findByConfirmationNumber(confirmationNumber: String): Mono<Reservation>
    
    /**
     * 고객 ID로 예약 목록 조회
     */
    fun findByGuestId(guestId: UUID): Flux<Reservation>
    
    /**
     * 시설 ID로 예약 목록 조회
     */
    fun findByPropertyId(propertyId: UUID): Flux<Reservation>
    
    /**
     * 조건에 따른 예약 검색
     */
    fun search(criteria: ReservationSearchCriteria): Flux<Reservation>
    
    /**
     * 조건에 따른 예약 개수 조회
     */
    fun count(criteria: ReservationSearchCriteria): Mono<Long>
    
    /**
     * 예약 삭제 (소프트 삭제)
     */
    fun deleteById(id: UUID): Mono<Void>
    
    /**
     * 예약 존재 여부 확인
     */
    fun existsById(id: UUID): Mono<Boolean>
    
    /**
     * 확인번호 존재 여부 확인
     */
    fun existsByConfirmationNumber(confirmationNumber: String): Mono<Boolean>
}