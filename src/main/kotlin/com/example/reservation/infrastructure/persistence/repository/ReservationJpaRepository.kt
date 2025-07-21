package com.example.reservation.infrastructure.persistence.repository

import com.example.reservation.infrastructure.persistence.entity.ReservationEntity
import com.example.reservation.infrastructure.persistence.entity.ReservationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 JPA 리포지토리
 * 실무 릴리즈 급 구현: 복잡한 쿼리, 배치 처리, 통계 쿼리 지원
 */
@Repository
interface ReservationJpaRepository : JpaRepository<ReservationEntity, UUID>, 
                                   JpaSpecificationExecutor<ReservationEntity> {

    // === 기본 조회 메서드들 ===

    /**
     * 확인번호로 예약 조회 (소프트 삭제 제외)
     */
    fun findByConfirmationNumberAndIsDeletedFalse(confirmationNumber: String): Optional<ReservationEntity>

    /**
     * 고객별 예약 목록 조회 (페이징)
     */
    fun findByGuestIdAndIsDeletedFalse(
        guestId: UUID, 
        pageable: Pageable
    ): Page<ReservationEntity>

    /**
     * 시설별 예약 목록 조회
     */
    fun findByPropertyIdAndIsDeletedFalse(
        propertyId: UUID,
        pageable: Pageable
    ): Page<ReservationEntity>

    /**
     * 상태별 예약 조회
     */
    fun findByStatusAndIsDeletedFalse(
        status: ReservationStatus,
        pageable: Pageable
    ): Page<ReservationEntity>

    /**
     * 체크인 날짜 범위로 예약 조회
     */
    fun findByCheckInDateBetweenAndIsDeletedFalse(
        startDate: LocalDate,
        endDate: LocalDate,
        pageable: Pageable
    ): Page<ReservationEntity>

    /**
     * 고객 및 체크인 날짜로 중복 예약 확인
     */
    fun findByGuestIdAndCheckInDateAndStatusInAndIsDeletedFalse(
        guestId: UUID,
        checkInDate: LocalDate,
        statuses: List<ReservationStatus>
    ): List<ReservationEntity>

    // === 복잡한 쿼리들 ===

    /**
     * 수익 분석 쿼리
     */
    @Query("""
        SELECT NEW com.example.reservation.infrastructure.persistence.dto.RevenueAnalysisDto(
            r.propertyId,
            SUM(r.totalAmount),
            AVG(r.totalAmount),
            COUNT(r),
            COUNT(CASE WHEN r.status = 'CANCELLED' THEN 1 END)
        )
        FROM ReservationEntity r
        WHERE r.checkInDate BETWEEN :startDate AND :endDate
        AND r.isDeleted = false
        GROUP BY r.propertyId
        ORDER BY SUM(r.totalAmount) DESC
    """)
    fun getRevenueAnalysisByProperty(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<RevenueAnalysisDto>

    /**
     * 점유율 분석 쿼리
     */
    @Query("""
        SELECT NEW com.example.reservation.infrastructure.persistence.dto.OccupancyAnalysisDto(
            r.propertyId,
            r.checkInDate,
            COUNT(r),
            SUM(r.adultCount + r.childCount)
        )
        FROM ReservationEntity r
        WHERE r.checkInDate BETWEEN :startDate AND :endDate
        AND r.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED')
        AND r.isDeleted = false
        GROUP BY r.propertyId, r.checkInDate
        ORDER BY r.checkInDate, r.propertyId
    """)
    fun getOccupancyAnalysis(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<OccupancyAnalysisDto>

    /**
     * 취소율 분석
     */
    @Query("""
        SELECT 
            CAST(COUNT(CASE WHEN r.status = 'CANCELLED' THEN 1 END) AS double) * 100.0 / COUNT(r)
        FROM ReservationEntity r
        WHERE r.createdAt BETWEEN :startDateTime AND :endDateTime
        AND r.isDeleted = false
    """)
    fun getCancellationRate(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime
    ): Double

    /**
     * 평균 예약 전환 시간 (예약 생성부터 확정까지)
     */
    @Query("""
        SELECT AVG(
            EXTRACT(EPOCH FROM (
                CASE WHEN r.status = 'CONFIRMED' AND r.modifiedAt > r.createdAt
                THEN r.modifiedAt - r.createdAt
                ELSE INTERVAL '0'
                END
            ))
        )
        FROM ReservationEntity r
        WHERE r.createdAt BETWEEN :startDateTime AND :endDateTime
        AND r.status = 'CONFIRMED'
        AND r.isDeleted = false
    """)
    fun getAverageConfirmationTime(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime
    ): Optional<Double>

    /**
     * 리드 타임 분석 (예약 생성일과 체크인일 간격)
     */
    @Query("""
        SELECT AVG(
            EXTRACT(DAYS FROM (r.checkInDate - CAST(r.createdAt AS date)))
        )
        FROM ReservationEntity r
        WHERE r.createdAt BETWEEN :startDateTime AND :endDateTime
        AND r.status != 'CANCELLED'
        AND r.isDeleted = false
    """)
    fun getAverageLeadTime(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime
    ): Optional<Double>

    // === 배치 처리 쿼리들 ===

    /**
     * 만료된 대기 상태 예약들 자동 취소
     */
    @Modifying
    @Query("""
        UPDATE ReservationEntity r
        SET r.status = 'CANCELLED',
            r.cancelledAt = CURRENT_TIMESTAMP,
            r.cancellationReason = 'AUTO_EXPIRED',
            r.modifiedAt = CURRENT_TIMESTAMP
        WHERE r.status = 'PENDING'
        AND r.createdAt < :expirationDateTime
        AND r.isDeleted = false
    """)
    fun cancelExpiredPendingReservations(
        @Param("expirationDateTime") expirationDateTime: LocalDateTime
    ): Int

    /**
     * 노쇼 처리 (체크인 시간이 지났는데 체크인하지 않은 예약)
     */
    @Modifying
    @Query("""
        UPDATE ReservationEntity r
        SET r.status = 'NO_SHOW',
            r.modifiedAt = CURRENT_TIMESTAMP
        WHERE r.status = 'CONFIRMED'
        AND r.checkInDate < :currentDate
        AND r.actualCheckInTime IS NULL
        AND (r.estimatedCheckInTime IS NULL OR r.estimatedCheckInTime < :currentDateTime)
        AND r.isDeleted = false
    """)
    fun markNoShowReservations(
        @Param("currentDate") currentDate: LocalDate,
        @Param("currentDateTime") currentDateTime: LocalDateTime
    ): Int

    /**
     * 자동 체크아웃 처리
     */
    @Modifying
    @Query("""
        UPDATE ReservationEntity r
        SET r.status = 'CHECKED_OUT',
            r.actualCheckOutTime = :currentDateTime,
            r.modifiedAt = CURRENT_TIMESTAMP
        WHERE r.status = 'CHECKED_IN'
        AND r.checkOutDate < :currentDate
        AND r.actualCheckOutTime IS NULL
        AND r.isDeleted = false
    """)
    fun autoCheckOutReservations(
        @Param("currentDate") currentDate: LocalDate,
        @Param("currentDateTime") currentDateTime: LocalDateTime
    ): Int

    // === 데이터 무결성 검증 쿼리들 ===

    /**
     * 중복 확인번호 검증
     */
    fun existsByConfirmationNumberAndIsDeletedFalse(confirmationNumber: String): Boolean

    /**
     * 객실 오버부킹 검증
     */
    @Query("""
        SELECT COUNT(r)
        FROM ReservationEntity r
        WHERE r.propertyId = :propertyId
        AND r.roomTypeId = :roomTypeId
        AND r.checkInDate <= :checkOutDate
        AND r.checkOutDate > :checkInDate
        AND r.status IN ('CONFIRMED', 'CHECKED_IN')
        AND r.isDeleted = false
    """)
    fun countOverlappingReservations(
        @Param("propertyId") propertyId: UUID,
        @Param("roomTypeId") roomTypeId: UUID,
        @Param("checkInDate") checkInDate: LocalDate,
        @Param("checkOutDate") checkOutDate: LocalDate
    ): Long

    /**
     * 고객의 활성 예약 개수
     */
    @Query("""
        SELECT COUNT(r)
        FROM ReservationEntity r
        WHERE r.guestId = :guestId
        AND r.status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN')
        AND r.isDeleted = false
    """)
    fun countActiveReservationsByGuest(@Param("guestId") guestId: UUID): Long

    // === 관리자용 쿼리들 ===

    /**
     * 당일 도착 예약 목록
     */
    @Query("""
        SELECT r FROM ReservationEntity r
        WHERE r.checkInDate = :date
        AND r.status = 'CONFIRMED'
        AND r.isDeleted = false
        ORDER BY r.estimatedCheckInTime ASC, r.createdAt ASC
    """)
    fun findTodayArrivals(@Param("date") date: LocalDate): List<ReservationEntity>

    /**
     * 당일 출발 예약 목록
     */
    @Query("""
        SELECT r FROM ReservationEntity r
        WHERE r.checkOutDate = :date
        AND r.status = 'CHECKED_IN'
        AND r.isDeleted = false
        ORDER BY r.estimatedCheckOutTime ASC, r.actualCheckInTime ASC
    """)
    fun findTodayDepartures(@Param("date") date: LocalDate): List<ReservationEntity>

    /**
     * VIP 고객 예약 목록 (로열티 등급 기반)
     */
    @Query("""
        SELECT r FROM ReservationEntity r
        JOIN GuestEntity g ON r.guestId = g.id
        WHERE g.loyaltyTier IN ('PLATINUM', 'DIAMOND')
        AND r.checkInDate BETWEEN :startDate AND :endDate
        AND r.status != 'CANCELLED'
        AND r.isDeleted = false
        ORDER BY g.loyaltyTier DESC, r.checkInDate ASC
    """)
    fun findVipReservations(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<ReservationEntity>
}

// === DTO 클래스들 ===

/**
 * 수익 분석 DTO
 */
data class RevenueAnalysisDto(
    val propertyId: UUID,
    val totalRevenue: BigDecimal,
    val averageReservationValue: BigDecimal,
    val totalReservations: Long,
    val cancelledReservations: Long
) {
    val cancellationRate: Double
        get() = if (totalReservations > 0) 
                  (cancelledReservations.toDouble() / totalReservations) * 100 
                else 0.0
}

/**
 * 점유율 분석 DTO
 */
data class OccupancyAnalysisDto(
    val propertyId: UUID,
    val date: LocalDate,
    val roomsOccupied: Long,
    val totalGuests: Long
)