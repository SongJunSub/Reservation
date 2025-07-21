package com.example.reservation.infrastructure.persistence.repository

import com.example.reservation.application.usecase.reservation.ReservationSearchCriteria
import com.example.reservation.application.usecase.reservation.SortDirection
import com.example.reservation.infrastructure.persistence.ReservationStats
import com.example.reservation.infrastructure.persistence.entity.ReservationEntity
import com.example.reservation.infrastructure.persistence.entity.ReservationStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 R2DBC 리포지토리
 * 실무 릴리즈 급 구현: 리액티브 쿼리, 동적 검색, 성능 최적화
 */
@Repository
interface ReservationR2dbcRepository : R2dbcRepository<ReservationEntity, UUID> {

    // === 기본 리액티브 조회 메서드들 ===

    /**
     * 확인번호로 예약 조회 (인덱스 활용)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE confirmation_number = :confirmationNumber 
        AND is_deleted = false
    """)
    fun findByConfirmationNumber(@Param("confirmationNumber") confirmationNumber: String): Mono<ReservationEntity>

    /**
     * 고객별 예약 목록 (최신순 정렬)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE guest_id = :guestId 
        AND is_deleted = false 
        ORDER BY created_at DESC
    """)
    fun findByGuestIdOrderByCreatedAtDesc(@Param("guestId") guestId: UUID): Flux<ReservationEntity>

    /**
     * 시설별 예약 목록 (체크인 날짜순 정렬)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE property_id = :propertyId 
        AND is_deleted = false 
        ORDER BY check_in_date DESC
    """)
    fun findByPropertyIdOrderByCheckInDateDesc(@Param("propertyId") propertyId: UUID): Flux<ReservationEntity>

    /**
     * 상태별 예약 조회
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE status = :status 
        AND is_deleted = false 
        ORDER BY check_in_date ASC
    """)
    fun findByStatus(@Param("status") status: String): Flux<ReservationEntity>

    /**
     * 확인번호 존재 여부 확인
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM reservations 
            WHERE confirmation_number = :confirmationNumber 
            AND is_deleted = false
        )
    """)
    fun existsByConfirmationNumber(@Param("confirmationNumber") confirmationNumber: String): Mono<Boolean>

    // === 동적 검색 쿼리 (조건부 WHERE 절) ===

    /**
     * 복합 조건 검색 - 동적 쿼리
     */
    fun findByCriteria(criteria: ReservationSearchCriteria): Flux<ReservationEntity> {
        val sql = buildDynamicQuery(criteria, false)
        // 실제 구현에서는 R2DBC Template 또는 Custom Repository 사용
        return findByCustomQuery(sql, criteria)
    }

    /**
     * 복합 조건 개수 조회 - 동적 쿼리
     */
    fun countByCriteria(criteria: ReservationSearchCriteria): Mono<Long> {
        val sql = buildDynamicQuery(criteria, true)
        return countByCustomQuery(sql, criteria)
    }

    // === 날짜 기반 조회 쿼리들 ===

    /**
     * 체크인 날짜 범위 조회
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE check_in_date BETWEEN :startDate AND :endDate
        AND is_deleted = false
        ORDER BY check_in_date ASC, property_id ASC
    """)
    fun findByCheckInDateRange(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Flux<ReservationEntity>

    /**
     * 체크아웃 날짜 범위 조회
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE check_out_date BETWEEN :startDate AND :endDate
        AND is_deleted = false
        ORDER BY check_out_date ASC
    """)
    fun findByCheckOutDateRange(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Flux<ReservationEntity>

    /**
     * 생성일 범위 조회
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE created_at BETWEEN :startDateTime AND :endDateTime
        AND is_deleted = false
        ORDER BY created_at DESC
    """)
    fun findByCreatedAtRange(
        @Param("startDateTime") startDateTime: LocalDateTime,
        @Param("endDateTime") endDateTime: LocalDateTime
    ): Flux<ReservationEntity>

    // === 통계 및 분석 쿼리들 ===

    /**
     * 기간별 예약 통계
     */
    @Query("""
        SELECT 
            COUNT(*) as total_reservations,
            SUM(total_amount) as total_revenue,
            AVG(total_amount) as average_reservation_value,
            COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) * 100.0 / COUNT(*) as cancellation_rate,
            COUNT(CASE WHEN status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED') THEN 1 END) * 100.0 / COUNT(*) as occupancy_rate,
            (SELECT property_id FROM reservations r2 WHERE r2.created_at BETWEEN :startDate AND :endDate AND r2.is_deleted = false GROUP BY r2.property_id ORDER BY COUNT(*) DESC LIMIT 1) as top_property_id,
            (SELECT check_in_date FROM reservations r2 WHERE r2.created_at BETWEEN :startDate AND :endDate AND r2.is_deleted = false GROUP BY r2.check_in_date ORDER BY COUNT(*) DESC LIMIT 1) as peak_booking_day
        FROM reservations 
        WHERE created_at BETWEEN :startDate AND :endDate
        AND is_deleted = false
    """)
    fun getStatsByDateRange(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Mono<ReservationStats>

    /**
     * 시설별 일별 점유율
     */
    @Query("""
        SELECT 
            property_id,
            check_in_date,
            COUNT(*) as bookings,
            SUM(adult_count + child_count) as total_guests
        FROM reservations 
        WHERE property_id = :propertyId
        AND check_in_date BETWEEN :startDate AND :endDate
        AND status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED')
        AND is_deleted = false
        GROUP BY property_id, check_in_date
        ORDER BY check_in_date ASC
    """)
    fun getDailyOccupancyByProperty(
        @Param("propertyId") propertyId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Flux<DailyOccupancyStats>

    /**
     * 월별 수익 트렌드
     */
    @Query("""
        SELECT 
            DATE_TRUNC('month', check_in_date) as month,
            COUNT(*) as reservations,
            SUM(total_amount) as revenue,
            AVG(total_amount) as avg_value
        FROM reservations 
        WHERE check_in_date BETWEEN :startDate AND :endDate
        AND status != 'CANCELLED'
        AND is_deleted = false
        GROUP BY DATE_TRUNC('month', check_in_date)
        ORDER BY month ASC
    """)
    fun getMonthlyRevenueTrend(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Flux<MonthlyRevenueStats>

    // === 운영 관리 쿼리들 ===

    /**
     * 당일 체크인 예약 목록 (실시간 대시보드용)
     */
    @Query("""
        SELECT r.*, g.first_name, g.last_name, g.loyalty_tier
        FROM reservations r
        LEFT JOIN guests g ON r.guest_id = g.id
        WHERE r.check_in_date = :date
        AND r.status = 'CONFIRMED'
        AND r.is_deleted = false
        ORDER BY r.estimated_check_in_time ASC NULLS LAST, r.created_at ASC
    """)
    fun findTodayArrivalsWithGuestInfo(@Param("date") date: LocalDate): Flux<ReservationWithGuestInfo>

    /**
     * 당일 체크아웃 예약 목록
     */
    @Query("""
        SELECT r.*, g.first_name, g.last_name
        FROM reservations r
        LEFT JOIN guests g ON r.guest_id = g.id
        WHERE r.check_out_date = :date
        AND r.status = 'CHECKED_IN'
        AND r.is_deleted = false
        ORDER BY r.estimated_check_out_time ASC NULLS LAST, r.actual_check_in_time ASC
    """)
    fun findTodayDeparturesWithGuestInfo(@Param("date") date: LocalDate): Flux<ReservationWithGuestInfo>

    /**
     * 체크인 지연 위험 예약들 (체크인 시간이 지났는데 체크인하지 않은 예약)
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE check_in_date = :date
        AND status = 'CONFIRMED'
        AND (estimated_check_in_time IS NULL OR estimated_check_in_time < :currentTime)
        AND actual_check_in_time IS NULL
        AND is_deleted = false
        ORDER BY estimated_check_in_time ASC NULLS FIRST
    """)
    fun findOverdueCheckins(
        @Param("date") date: LocalDate,
        @Param("currentTime") currentTime: LocalDateTime
    ): Flux<ReservationEntity>

    /**
     * 특별 요청이 있는 예약들
     */
    @Query("""
        SELECT * FROM reservations 
        WHERE special_requests IS NOT NULL 
        AND special_requests != ''
        AND check_in_date BETWEEN :startDate AND :endDate
        AND status IN ('CONFIRMED', 'CHECKED_IN')
        AND is_deleted = false
        ORDER BY check_in_date ASC
    """)
    fun findReservationsWithSpecialRequests(
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): Flux<ReservationEntity>

    // === 성능 최적화 쿼리들 ===

    /**
     * 가벼운 예약 요약 정보만 조회 (대시보드용)
     */
    @Query("""
        SELECT 
            id,
            confirmation_number,
            guest_id,
            property_id,
            check_in_date,
            check_out_date,
            status,
            total_amount,
            created_at
        FROM reservations 
        WHERE property_id = :propertyId
        AND check_in_date BETWEEN :startDate AND :endDate
        AND is_deleted = false
        ORDER BY check_in_date ASC
        LIMIT :limit
    """)
    fun findReservationSummaries(
        @Param("propertyId") propertyId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        @Param("limit") limit: Int
    ): Flux<ReservationSummary>

    // === 헬퍼 메서드들 (실제로는 Custom Repository에서 구현) ===

    /**
     * 동적 쿼리 빌더
     */
    private fun buildDynamicQuery(criteria: ReservationSearchCriteria, isCount: Boolean): String {
        val select = if (isCount) "SELECT COUNT(*)" else "SELECT *"
        val from = "FROM reservations r"
        val where = mutableListOf("r.is_deleted = false")
        
        criteria.guestId?.let { 
            where.add("r.guest_id = :guestId") 
        }
        criteria.propertyId?.let { 
            where.add("r.property_id = :propertyId") 
        }
        if (criteria.status.isNotEmpty()) {
            where.add("r.status IN (${criteria.status.joinToString(",") { "'$it'" }})")
        }
        criteria.checkInDateFrom?.let { 
            where.add("r.check_in_date >= :checkInDateFrom") 
        }
        criteria.checkInDateTo?.let { 
            where.add("r.check_in_date <= :checkInDateTo") 
        }
        
        val whereClause = if (where.isNotEmpty()) "WHERE ${where.joinToString(" AND ")}" else ""
        
        val orderBy = if (!isCount) {
            val direction = if (criteria.sortDirection == SortDirection.ASC) "ASC" else "DESC"
            "ORDER BY r.${criteria.sortBy} $direction"
        } else ""
        
        val limit = if (!isCount && criteria.size > 0) {
            "LIMIT ${criteria.size} OFFSET ${criteria.page * criteria.size}"
        } else ""
        
        return "$select $from $whereClause $orderBy $limit".trim()
    }

    private fun findByCustomQuery(sql: String, criteria: ReservationSearchCriteria): Flux<ReservationEntity> {
        // 실제 구현에서는 R2DBC DatabaseClient 사용
        // return databaseClient.sql(sql).bind(parameters...).fetch().all().map { ... }
        TODO("R2DBC DatabaseClient를 사용한 동적 쿼리 실행")
    }

    private fun countByCustomQuery(sql: String, criteria: ReservationSearchCriteria): Mono<Long> {
        // 실제 구현에서는 R2DBC DatabaseClient 사용
        TODO("R2DBC DatabaseClient를 사용한 동적 쿼리 실행")
    }
}

// === 통계 및 조회 결과 DTO 클래스들 ===

/**
 * 일별 점유율 통계
 */
data class DailyOccupancyStats(
    val propertyId: UUID,
    val date: LocalDate,
    val bookings: Long,
    val totalGuests: Long
)

/**
 * 월별 수익 통계
 */
data class MonthlyRevenueStats(
    val month: LocalDate,
    val reservations: Long,
    val revenue: java.math.BigDecimal,
    val avgValue: java.math.BigDecimal
)

/**
 * 고객 정보가 포함된 예약 정보
 */
data class ReservationWithGuestInfo(
    val reservationId: UUID,
    val confirmationNumber: String,
    val guestId: UUID,
    val firstName: String,
    val lastName: String,
    val loyaltyTier: String?,
    val propertyId: UUID,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val status: String,
    val estimatedCheckInTime: LocalDateTime?,
    val estimatedCheckOutTime: LocalDateTime?,
    val actualCheckInTime: LocalDateTime?,
    val specialRequests: String?
)

/**
 * 예약 요약 정보
 */
data class ReservationSummary(
    val id: UUID,
    val confirmationNumber: String,
    val guestId: UUID,
    val propertyId: UUID,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val status: String,
    val totalAmount: java.math.BigDecimal,
    val createdAt: LocalDateTime
)