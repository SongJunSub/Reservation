package com.example.reservation.infrastructure.persistence

import com.example.reservation.application.port.outbound.ReservationPort
import com.example.reservation.application.service.Reservation
import com.example.reservation.application.usecase.reservation.ReservationSearchCriteria
import com.example.reservation.infrastructure.persistence.entity.ReservationEntity
import com.example.reservation.infrastructure.persistence.mapper.ReservationMapper
import com.example.reservation.infrastructure.persistence.repository.ReservationJpaRepository
import com.example.reservation.infrastructure.persistence.repository.ReservationR2dbcRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import org.slf4j.LoggerFactory
import java.util.*

/**
 * 예약 리포지토리 구현체
 * 실무 릴리즈 급 구현: 하이브리드 JPA + R2DBC 아키텍처
 */
@Repository
@Transactional
class ReservationRepositoryImpl(
    private val jpaRepository: ReservationJpaRepository,
    private val r2dbcRepository: ReservationR2dbcRepository,
    private val reservationMapper: ReservationMapper
) : ReservationPort {

    private val logger = LoggerFactory.getLogger(ReservationRepositoryImpl::class.java)

    /**
     * 예약 저장 (JPA 사용 - 트랜잭션 보장이 중요한 쓰기 작업)
     */
    override fun save(reservation: Reservation): Mono<Reservation> {
        logger.debug("예약 저장 시작: reservationId={}", reservation.id)
        
        return Mono.fromCallable {
            val entity = reservationMapper.toEntity(reservation)
            val savedEntity = jpaRepository.save(entity)
            reservationMapper.toDomain(savedEntity)
        }
        .doOnSuccess { saved ->
            logger.debug("예약 저장 완료: reservationId={}", saved.id)
        }
        .doOnError { error ->
            logger.error("예약 저장 실패: reservationId={}, error={}", reservation.id, error.message)
        }
    }

    /**
     * ID로 예약 조회 (R2DBC 사용 - 빠른 단건 조회)
     */
    override fun findById(id: UUID): Mono<Reservation> {
        logger.debug("예약 조회 시작: reservationId={}", id)
        
        return r2dbcRepository.findById(id)
            .map { entity -> reservationMapper.toDomain(entity) }
            .doOnSuccess { reservation ->
                logger.debug("예약 조회 완료: reservationId={}, status={}", 
                           reservation?.id, reservation?.status)
            }
            .doOnError { error ->
                logger.error("예약 조회 실패: reservationId={}, error={}", id, error.message)
            }
    }

    /**
     * 확인번호로 예약 조회 (R2DBC + 캐싱)
     */
    override fun findByConfirmationNumber(confirmationNumber: String): Mono<Reservation> {
        logger.debug("확인번호로 예약 조회: confirmationNumber={}", confirmationNumber)
        
        return r2dbcRepository.findByConfirmationNumber(confirmationNumber)
            .map { entity -> reservationMapper.toDomain(entity) }
            .doOnSuccess { reservation ->
                logger.debug("확인번호 조회 완료: confirmationNumber={}, reservationId={}", 
                           confirmationNumber, reservation?.id)
            }
    }

    /**
     * 고객 ID로 예약 목록 조회 (R2DBC - 읽기 성능 최적화)
     */
    override fun findByGuestId(guestId: UUID): Flux<Reservation> {
        logger.debug("고객별 예약 조회: guestId={}", guestId)
        
        return r2dbcRepository.findByGuestIdOrderByCreatedAtDesc(guestId)
            .map { entity -> reservationMapper.toDomain(entity) }
            .doOnComplete {
                logger.debug("고객별 예약 조회 완료: guestId={}", guestId)
            }
    }

    /**
     * 시설 ID로 예약 목록 조회
     */
    override fun findByPropertyId(propertyId: UUID): Flux<Reservation> {
        logger.debug("시설별 예약 조회: propertyId={}", propertyId)
        
        return r2dbcRepository.findByPropertyIdOrderByCheckInDateDesc(propertyId)
            .map { entity -> reservationMapper.toDomain(entity) }
            .doOnComplete {
                logger.debug("시설별 예약 조회 완료: propertyId={}", propertyId)
            }
    }

    /**
     * 복합 조건 검색 (R2DBC + 동적 쿼리)
     */
    override fun search(criteria: ReservationSearchCriteria): Flux<Reservation> {
        logger.debug("예약 검색 시작: criteria={}", criteria)
        
        return r2dbcRepository.findByCriteria(criteria)
            .map { entity -> reservationMapper.toDomain(entity) }
            .doOnComplete {
                logger.debug("예약 검색 완료")
            }
    }

    /**
     * 조건별 개수 조회 (R2DBC)
     */
    override fun count(criteria: ReservationSearchCriteria): Mono<Long> {
        return r2dbcRepository.countByCriteria(criteria)
            .doOnSuccess { count ->
                logger.debug("예약 개수 조회 완료: count={}", count)
            }
    }

    /**
     * 예약 소프트 삭제 (JPA - 데이터 무결성 보장)
     */
    override fun deleteById(id: UUID): Mono<Void> {
        logger.warn("예약 소프트 삭제: reservationId={}", id)
        
        return Mono.fromRunnable {
            jpaRepository.findById(id).ifPresent { entity ->
                entity.isDeleted = true
                entity.deletedAt = java.time.LocalDateTime.now()
                jpaRepository.save(entity)
            }
        }
        .doOnSuccess {
            logger.info("예약 삭제 완료: reservationId={}", id)
        }
    }

    /**
     * 예약 존재 여부 확인 (R2DBC - 빠른 존재성 체크)
     */
    override fun existsById(id: UUID): Mono<Boolean> {
        return r2dbcRepository.existsById(id)
            .doOnSuccess { exists ->
                logger.debug("예약 존재 확인: reservationId={}, exists={}", id, exists)
            }
    }

    /**
     * 확인번호 존재 여부 확인
     */
    override fun existsByConfirmationNumber(confirmationNumber: String): Mono<Boolean> {
        return r2dbcRepository.existsByConfirmationNumber(confirmationNumber)
            .doOnSuccess { exists ->
                logger.debug("확인번호 존재 확인: confirmationNumber={}, exists={}", confirmationNumber, exists)
            }
    }

    /**
     * 배치 저장 (대량 데이터 처리용)
     */
    fun saveAll(reservations: List<Reservation>): Flux<Reservation> {
        logger.info("예약 배치 저장 시작: count={}", reservations.size)
        
        return Flux.fromIterable(reservations)
            .map { reservation -> reservationMapper.toEntity(reservation) }
            .collectList()
            .flatMapMany { entities ->
                Mono.fromCallable {
                    jpaRepository.saveAll(entities)
                }.flatMapMany { savedEntities ->
                    Flux.fromIterable(savedEntities)
                        .map { entity -> reservationMapper.toDomain(entity) }
                }
            }
            .doOnComplete {
                logger.info("예약 배치 저장 완료: count={}", reservations.size)
            }
    }

    /**
     * 통계 쿼리 (R2DBC - 분석용)
     */
    fun getReservationStatsByDateRange(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): Mono<ReservationStats> {
        return r2dbcRepository.getStatsByDateRange(startDate, endDate)
            .doOnSuccess { stats ->
                logger.debug("예약 통계 조회 완료: period={} to {}, totalReservations={}", 
                           startDate, endDate, stats.totalReservations)
            }
    }

    /**
     * 캐시 무효화 (Redis 캐시 사용시)
     */
    fun invalidateCache(reservationId: UUID) {
        logger.debug("예약 캐시 무효화: reservationId={}", reservationId)
        // 실제 구현에서는 Redis 캐시 삭제
    }

    /**
     * 성능 모니터링용 메트릭 수집
     */
    private fun recordMetrics(operation: String, duration: Long, success: Boolean) {
        // 실제 구현에서는 Micrometer로 메트릭 수집
        logger.debug("Repository 메트릭: operation={}, duration={}ms, success={}", 
                   operation, duration, success)
    }
}

/**
 * 예약 통계 데이터 클래스
 */
data class ReservationStats(
    val totalReservations: Long,
    val totalRevenue: java.math.BigDecimal,
    val averageReservationValue: java.math.BigDecimal,
    val cancellationRate: Double,
    val occupancyRate: Double,
    val topPropertyId: UUID? = null,
    val peakBookingDay: java.time.LocalDate? = null
)