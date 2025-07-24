package com.example.reservation.repository

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

/**
 * JPA 기반 예약 Repository
 * 복잡한 쿼리와 배치 처리에 최적화
 */
@Repository
interface ReservationRepository : JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {
    
    /**
     * 확인 번호로 예약 조회
     */
    fun findByConfirmationNumber(confirmationNumber: String): Optional<Reservation>
    
    /**
     * 고객 이메일과 예약 상태로 조회
     */
    fun findByGuestEmailAndStatusIn(
        email: String, 
        statuses: List<ReservationStatus>
    ): List<Reservation>
    
    /**
     * 체크인 날짜 범위로 예약 조회
     */
    fun findByCheckInDateBetween(
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<Reservation>
    
    /**
     * 체크아웃 날짜 범위로 예약 조회
     */
    fun findByCheckOutDateBetween(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Reservation>
    
    /**
     * 오늘 체크인 예정인 확정된 예약 조회
     */
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.checkInDate = CURRENT_DATE 
        AND r.status = 'CONFIRMED'
        ORDER BY r.createdAt ASC
    """)
    fun findTodayCheckIns(): List<Reservation>
    
    /**
     * 오늘 체크아웃 예정인 활성 예약 조회
     */
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.checkOutDate = CURRENT_DATE 
        AND r.status IN ('CONFIRMED', 'CHECKED_IN')
        ORDER BY r.createdAt ASC
    """)
    fun findTodayCheckOuts(): List<Reservation>
    
    /**
     * 특정 객실의 활성 예약 조회
     */
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.room.id = :roomId 
        AND r.status IN ('CONFIRMED', 'CHECKED_IN')
        AND r.checkOutDate >= CURRENT_DATE
        ORDER BY r.checkInDate ASC
    """)
    fun findActiveReservationsByRoomId(@Param("roomId") roomId: Long): List<Reservation>
    
    /**
     * 예약 상태별 통계 조회
     */
    @Query("""
        SELECT r.status, COUNT(r) 
        FROM Reservation r 
        WHERE r.createdAt >= :fromDate
        GROUP BY r.status
    """)
    fun countReservationsByStatusSince(@Param("fromDate") fromDate: LocalDate): List<Array<Any>>
    
    /**
     * 고객의 예약 이력 조회 (페이징)
     */
    fun findByGuestEmailOrderByCreatedAtDesc(
        email: String, 
        pageable: Pageable
    ): Page<Reservation>
    
    /**
     * 총 매출 계산 (특정 기간)
     */
    @Query("""
        SELECT COALESCE(SUM(r.totalAmount), 0) 
        FROM Reservation r 
        WHERE r.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED')
        AND r.checkInDate BETWEEN :startDate AND :endDate
    """)
    fun calculateTotalRevenue(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): java.math.BigDecimal
    
    /**
     * 취소된 예약 조회 (환불 처리용)
     */
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.status = 'CANCELLED'
        AND r.paymentStatus = 'PAID'
        AND r.lastModifiedAt >= :fromDate
    """)
    fun findCancelledReservationsForRefund(@Param("fromDate") fromDate: LocalDate): List<Reservation>
    
    /**
     * 예약 존재 여부 확인 (객실, 날짜 겹침)
     */
    @Query("""
        SELECT COUNT(r) > 0 
        FROM Reservation r 
        WHERE r.room.id = :roomId
        AND r.status IN ('CONFIRMED', 'CHECKED_IN')
        AND (
            (r.checkInDate <= :checkInDate AND r.checkOutDate > :checkInDate) OR
            (r.checkInDate < :checkOutDate AND r.checkOutDate >= :checkOutDate) OR
            (r.checkInDate >= :checkInDate AND r.checkOutDate <= :checkOutDate)
        )
    """)
    fun existsOverlappingReservation(
        @Param("roomId") roomId: Long,
        @Param("checkInDate") checkInDate: LocalDate,
        @Param("checkOutDate") checkOutDate: LocalDate
    ): Boolean
}