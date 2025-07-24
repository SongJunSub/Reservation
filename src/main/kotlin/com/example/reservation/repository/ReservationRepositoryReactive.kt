package com.example.reservation.repository

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate

/**
 * R2DBC 기반 리액티브 예약 Repository
 * 실시간 조회와 고성능 처리에 최적화
 */
@Repository
interface ReservationRepositoryReactive : R2dbcRepository<Reservation, Long> {
    
    /**
     * 확인 번호로 예약 조회 (리액티브)
     */
    fun findByConfirmationNumber(confirmationNumber: String): Mono<Reservation>
    
    /**
     * 고객 이메일로 예약 조회 (리액티브 스트림)
     */
    fun findByGuestEmail(email: String): Flux<Reservation>
    
    /**
     * 예약 상태와 체크인 날짜 범위로 조회
     */
    fun findByStatusAndCheckInDateBetween(
        status: ReservationStatus,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flux<Reservation>
    
    /**
     * 체크인 날짜로 정렬된 예약 스트림
     */
    fun findAllByOrderByCheckInDateAsc(): Flux<Reservation>
    
    /**
     * 특정 상태의 예약 수 카운트 (리액티브)
     */
    fun countByStatus(status: ReservationStatus): Mono<Long>
    
    /**
     * 오늘 체크인 예정 예약 스트림 (실시간)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE check_in_date = CURRENT_DATE 
        AND status = 'CONFIRMED'
        ORDER BY created_at ASC
    """)
    fun findTodayCheckInsStream(): Flux<Reservation>
    
    /**
     * 활성 예약 실시간 모니터링 스트림
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE status IN ('CONFIRMED', 'CHECKED_IN')
        AND check_out_date >= CURRENT_DATE
        ORDER BY check_in_date ASC
    """)
    fun findActiveReservationsStream(): Flux<Reservation>
    
    /**
     * 특정 객실의 예약 현황 스트림
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE room_id = :roomId
        AND status IN ('CONFIRMED', 'CHECKED_IN')
        AND check_out_date >= CURRENT_DATE
        ORDER BY check_in_date ASC
    """)
    fun findRoomReservationsStream(@Param("roomId") roomId: Long): Flux<Reservation>
    
    /**
     * 최근 예약 실시간 피드 (한정된 개수)
     */
    @Query("""
        SELECT * FROM reservations 
        ORDER BY created_at DESC 
        LIMIT :limit
    """)
    fun findRecentReservationsStream(@Param("limit") limit: Int): Flux<Reservation>
    
    /**
     * 고객의 예약 이력 스트림
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE guest_email = :email
        ORDER BY created_at DESC
    """)  
    fun findGuestReservationHistoryStream(@Param("email") email: String): Flux<Reservation>
    
    /**
     * 예약 상태 변경 모니터링을 위한 스트림
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE last_modified_at >= :fromDate
        ORDER BY last_modified_at ASC
    """)
    fun findReservationUpdatesStream(@Param("fromDate") fromDate: LocalDate): Flux<Reservation>
    
    /**
     * 체크인 예정 알림용 스트림 (X일 전)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE check_in_date = :targetDate
        AND status = 'CONFIRMED'
        ORDER BY created_at ASC
    """)
    fun findUpcomingCheckInsStream(@Param("targetDate") targetDate: LocalDate): Flux<Reservation>
    
    /**
     * 실시간 점유율 계산을 위한 활성 예약 수
     */
    @Query("""
        SELECT COUNT(*) FROM reservations 
        WHERE status IN ('CONFIRMED', 'CHECKED_IN')
        AND check_in_date <= CURRENT_DATE
        AND check_out_date > CURRENT_DATE
    """)
    fun countCurrentOccupancy(): Mono<Long>
    
    /**
     * 예약 가능 여부 확인 (비동기)
     */
    @Query("""
        SELECT COUNT(*) FROM reservations 
        WHERE room_id = :roomId
        AND status IN ('CONFIRMED', 'CHECKED_IN')
        AND (
            (check_in_date <= :checkInDate AND check_out_date > :checkInDate) OR
            (check_in_date < :checkOutDate AND check_out_date >= :checkOutDate) OR
            (check_in_date >= :checkInDate AND check_out_date <= :checkOutDate)
        )
    """)
    fun countOverlappingReservations(
        @Param("roomId") roomId: Long,
        @Param("checkInDate") checkInDate: LocalDate,
        @Param("checkOutDate") checkOutDate: LocalDate
    ): Mono<Long>
    
    /**
     * 실시간 매출 계산 스트림
     */
    @Query("""
        SELECT COALESCE(SUM(total_amount), 0) 
        FROM reservations 
        WHERE status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED')
        AND check_in_date BETWEEN :startDate AND :endDate
    """)
    fun calculateRevenueStream(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Mono<java.math.BigDecimal>
}