package com.example.reservation.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicInteger

/**
 * 메트릭 설정 (Kotlin)
 * 
 * 기능:
 * 1. Prometheus 메트릭 레지스트리 설정
 * 2. 커스텀 비즈니스 메트릭 정의
 * 3. JVM 및 시스템 메트릭 자동 수집
 * 4. 애플리케이션별 태그 및 레이블 설정
 * 
 * Kotlin 특징:
 * - 간결한 Bean 정의와 람다 표현식
 * - 확장 함수를 통한 메트릭 편의 메서드
 * - data class를 통한 메트릭 정보 구조화
 * - when 표현식을 통한 조건부 메트릭 수집
 */
@Configuration
class MetricsConfig {

    companion object {
        // 메트릱 이름 상수
        const val RESERVATION_CREATED_COUNTER = "reservation.created.total"
        const val RESERVATION_CANCELLED_COUNTER = "reservation.cancelled.total"
        const val RESERVATION_CONFIRMED_COUNTER = "reservation.confirmed.total"
        const val CHECK_IN_TIMER = "reservation.checkin.duration"
        const val CHECK_OUT_TIMER = "reservation.checkout.duration"
        const val PAYMENT_PROCESSED_COUNTER = "payment.processed.total"
        const val PAYMENT_FAILED_COUNTER = "payment.failed.total"
        const val CACHE_HIT_COUNTER = "cache.hits.total"
        const val CACHE_MISS_COUNTER = "cache.misses.total"
        const val DATABASE_QUERY_TIMER = "database.query.duration"
        const val KAFKA_EVENT_PUBLISHED_COUNTER = "kafka.events.published.total"
        const val KAFKA_EVENT_CONSUMED_COUNTER = "kafka.events.consumed.total"
        
        // 게이지 메트릭
        const val ACTIVE_RESERVATIONS_GAUGE = "reservations.active.current"
        const val AVAILABLE_ROOMS_GAUGE = "rooms.available.current"
        const val OCCUPANCY_RATE_GAUGE = "occupancy.rate.current"
        const val REVENUE_TODAY_GAUGE = "revenue.today.current"
    }

    /**
     * Prometheus 메터 레지스트리 설정
     * Kotlin의 간결한 Bean 정의
     */
    @Bean
    fun prometheusMeterRegistry(): PrometheusMeterRegistry {
        return PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }

    /**
     * 메터 레지스트리 커스터마이저
     * 공통 태그 및 JVM 메트릭 자동 등록
     */
    @Bean
    fun metricsCommonTags(): MeterRegistryCustomizer<MeterRegistry> {
        return MeterRegistryCustomizer { registry ->
            // 공통 태그 추가
            registry.config().commonTags(
                "application", "reservation-service",
                "version", getApplicationVersion(),
                "environment", getEnvironment(),
                "instance", getInstanceId()
            )
            
            // JVM 메트릭 자동 등록
            JvmMemoryMetrics().bindTo(registry)
            JvmGcMetrics().bindTo(registry)
            JvmThreadMetrics().bindTo(registry)
            ClassLoaderMetrics().bindTo(registry)
            ProcessorMetrics().bindTo(registry)
            UptimeMetrics().bindTo(registry)
        }
    }

    /**
     * 커스텀 비즈니스 메트릭 수집기
     * Kotlin의 클래스 위임과 확장 함수 활용
     */
    @Bean
    fun reservationMetrics(meterRegistry: MeterRegistry): ReservationMetrics {
        return ReservationMetrics(meterRegistry)
    }

    // === 헬퍼 메서드들 ===

    private fun getApplicationVersion(): String = 
        this::class.java.`package`?.implementationVersion ?: "unknown"

    private fun getEnvironment(): String = 
        System.getProperty("spring.profiles.active", "unknown")

    private fun getInstanceId(): String = 
        System.getProperty("server.port", "8080") + "-" + 
        System.getProperty("user.name", "unknown")
}

/**
 * 예약 시스템 메트릭 수집기 (Kotlin)
 * 
 * 비즈니스 도메인별 메트릭 수집 및 관리
 * Kotlin의 확장 함수와 람다를 활용한 간편한 메트릭 API 제공
 */
class ReservationMetrics(private val meterRegistry: MeterRegistry) {

    // 카운터 메트릭들
    private val reservationCreatedCounter: Counter = Counter.builder(MetricsConfig.RESERVATION_CREATED_COUNTER)
        .description("총 생성된 예약 수")
        .register(meterRegistry)

    private val reservationCancelledCounter: Counter = Counter.builder(MetricsConfig.RESERVATION_CANCELLED_COUNTER)
        .description("총 취소된 예약 수")
        .register(meterRegistry)

    private val reservationConfirmedCounter: Counter = Counter.builder(MetricsConfig.RESERVATION_CONFIRMED_COUNTER)
        .description("총 확정된 예약 수")
        .register(meterRegistry)

    private val paymentProcessedCounter: Counter = Counter.builder(MetricsConfig.PAYMENT_PROCESSED_COUNTER)
        .description("총 처리된 결제 수")
        .register(meterRegistry)

    private val paymentFailedCounter: Counter = Counter.builder(MetricsConfig.PAYMENT_FAILED_COUNTER)
        .description("총 실패한 결제 수")
        .register(meterRegistry)

    private val kafkaEventPublishedCounter: Counter = Counter.builder(MetricsConfig.KAFKA_EVENT_PUBLISHED_COUNTER)
        .description("총 발행된 Kafka 이벤트 수")
        .register(meterRegistry)

    private val kafkaEventConsumedCounter: Counter = Counter.builder(MetricsConfig.KAFKA_EVENT_CONSUMED_COUNTER)
        .description("총 소비된 Kafka 이벤트 수")
        .register(meterRegistry)

    // 타이머 메트릭들
    private val checkInTimer: Timer = Timer.builder(MetricsConfig.CHECK_IN_TIMER)
        .description("체크인 처리 시간")
        .register(meterRegistry)

    private val checkOutTimer: Timer = Timer.builder(MetricsConfig.CHECK_OUT_TIMER)
        .description("체크아웃 처리 시간")
        .register(meterRegistry)

    private val databaseQueryTimer: Timer = Timer.builder(MetricsConfig.DATABASE_QUERY_TIMER)
        .description("데이터베이스 쿼리 실행 시간")
        .register(meterRegistry)

    // 게이지 메트릭들 (동적 값 참조)
    private val activeReservationsCount = AtomicInteger(0)
    private val availableRoomsCount = AtomicInteger(0)
    private var occupancyRate = 0.0
    private var revenueToday = 0.0

    init {
        // 게이지 메트릭 등록
        Gauge.builder(MetricsConfig.ACTIVE_RESERVATIONS_GAUGE)
            .description("현재 활성 예약 수")
            .register(meterRegistry) { activeReservationsCount.get().toDouble() }

        Gauge.builder(MetricsConfig.AVAILABLE_ROOMS_GAUGE)
            .description("현재 이용 가능한 객실 수")
            .register(meterRegistry) { availableRoomsCount.get().toDouble() }

        Gauge.builder(MetricsConfig.OCCUPANCY_RATE_GAUGE)
            .description("현재 객실 점유율")
            .register(meterRegistry) { occupancyRate }

        Gauge.builder(MetricsConfig.REVENUE_TODAY_GAUGE)
            .description("오늘 매출")
            .register(meterRegistry) { revenueToday }
    }

    // === 카운터 메트릭 메서드들 ===

    /**
     * 예약 생성 메트릭 증가
     * Kotlin의 기본 매개변수와 확장 함수 스타일
     */
    fun incrementReservationCreated(tags: Map<String, String> = emptyMap()) {
        reservationCreatedCounter.increment(
            io.micrometer.core.instrument.Tags.of(tags.map { (k, v) -> 
                io.micrometer.core.instrument.Tag.of(k, v) 
            })
        )
    }

    fun incrementReservationCancelled(reason: String = "unknown", tags: Map<String, String> = emptyMap()) {
        val allTags = tags + ("cancellation_reason" to reason)
        reservationCancelledCounter.increment(
            io.micrometer.core.instrument.Tags.of(allTags.map { (k, v) -> 
                io.micrometer.core.instrument.Tag.of(k, v) 
            })
        )
    }

    fun incrementReservationConfirmed(source: String = "unknown") {
        reservationConfirmedCounter.increment(
            io.micrometer.core.instrument.Tags.of("source", source)
        )
    }

    fun incrementPaymentProcessed(method: String, gateway: String) {
        paymentProcessedCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "payment_method", method,
                "payment_gateway", gateway
            )
        )
    }

    fun incrementPaymentFailed(method: String, errorCode: String) {
        paymentFailedCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "payment_method", method,
                "error_code", errorCode
            )
        )
    }

    fun incrementKafkaEventPublished(topic: String, eventType: String) {
        kafkaEventPublishedCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "topic", topic,
                "event_type", eventType
            )
        )
    }

    fun incrementKafkaEventConsumed(topic: String, eventType: String, success: Boolean) {
        kafkaEventConsumedCounter.increment(
            io.micrometer.core.instrument.Tags.of(
                "topic", topic,
                "event_type", eventType,
                "success", success.toString()
            )
        )
    }

    // === 타이머 메트릭 메서드들 ===

    /**
     * 체크인 처리 시간 측정
     * Kotlin의 고차 함수와 inline 활용
     */
    inline fun <T> timeCheckIn(operation: () -> T): T {
        return checkInTimer.recordCallable(operation)!!
    }

    inline fun <T> timeCheckOut(operation: () -> T): T {
        return checkOutTimer.recordCallable(operation)!!
    }

    inline fun <T> timeDatabaseQuery(queryType: String, operation: () -> T): T {
        return Timer.Sample.start(meterRegistry).use { sample ->
            val result = operation()
            sample.stop(Timer.builder(MetricsConfig.DATABASE_QUERY_TIMER)
                .tag("query_type", queryType)
                .register(meterRegistry))
            result
        }
    }

    // === 게이지 메트릭 업데이트 메서드들 ===

    fun updateActiveReservations(count: Int) {
        activeReservationsCount.set(count)
    }

    fun updateAvailableRooms(count: Int) {
        availableRoomsCount.set(count)
    }

    fun updateOccupancyRate(rate: Double) {
        occupancyRate = rate.coerceIn(0.0, 1.0)
    }

    fun updateRevenueToday(revenue: Double) {
        revenueToday = revenue
    }

    // === 복합 메트릭 메서드들 ===

    /**
     * 예약 생성 성공률 계산
     * Kotlin의 확장 속성 스타일
     */
    fun getReservationSuccessRate(): Double {
        val created = reservationCreatedCounter.count()
        val cancelled = reservationCancelledCounter.count()
        val confirmed = reservationConfirmedCounter.count()
        
        return if (created > 0) confirmed / created else 0.0
    }

    /**
     * 결제 성공률 계산
     */
    fun getPaymentSuccessRate(): Double {
        val processed = paymentProcessedCounter.count()
        val failed = paymentFailedCounter.count()
        val total = processed + failed
        
        return if (total > 0) processed / total else 0.0
    }

    /**
     * 메트릭 요약 정보 제공
     * Kotlin data class 활용
     */
    fun getMetricsSummary(): MetricsSummary {
        return MetricsSummary(
            reservationsCreated = reservationCreatedCounter.count().toLong(),
            reservationsCancelled = reservationCancelledCounter.count().toLong(),
            reservationsConfirmed = reservationConfirmedCounter.count().toLong(),
            paymentsProcessed = paymentProcessedCounter.count().toLong(),
            paymentsFailed = paymentFailedCounter.count().toLong(),
            kafkaEventsPublished = kafkaEventPublishedCounter.count().toLong(),
            kafkaEventsConsumed = kafkaEventConsumedCounter.count().toLong(),
            activeReservations = activeReservationsCount.get(),
            availableRooms = availableRoomsCount.get(),
            occupancyRate = occupancyRate,
            revenueToday = revenueToday,
            reservationSuccessRate = getReservationSuccessRate(),
            paymentSuccessRate = getPaymentSuccessRate()
        )
    }
}

/**
 * 메트릭 요약 정보 데이터 클래스
 * Kotlin data class의 간편함과 불변성
 */
data class MetricsSummary(
    val reservationsCreated: Long,
    val reservationsCancelled: Long,
    val reservationsConfirmed: Long,
    val paymentsProcessed: Long,
    val paymentsFailed: Long,
    val kafkaEventsPublished: Long,
    val kafkaEventsConsumed: Long,
    val activeReservations: Int,
    val availableRooms: Int,
    val occupancyRate: Double,
    val revenueToday: Double,
    val reservationSuccessRate: Double,
    val paymentSuccessRate: Double
) {
    /**
     * 전체 예약 수
     */
    val totalReservations: Long
        get() = reservationsCreated

    /**
     * 전체 결제 수
     */
    val totalPayments: Long
        get() = paymentsProcessed + paymentsFailed

    /**
     * 전체 Kafka 이벤트 수
     */
    val totalKafkaEvents: Long
        get() = kafkaEventsPublished + kafkaEventsConsumed

    /**
     * 요약을 JSON 형태로 변환 (모니터링 대시보드용)
     */
    fun toJsonString(): String {
        return """
            {
                "reservations": {
                    "created": $reservationsCreated,
                    "cancelled": $reservationsCancelled,
                    "confirmed": $reservationsConfirmed,
                    "success_rate": $reservationSuccessRate
                },
                "payments": {
                    "processed": $paymentsProcessed,
                    "failed": $paymentsFailed,
                    "success_rate": $paymentSuccessRate
                },
                "kafka": {
                    "published": $kafkaEventsPublished,
                    "consumed": $kafkaEventsConsumed
                },
                "current_state": {
                    "active_reservations": $activeReservations,
                    "available_rooms": $availableRooms,
                    "occupancy_rate": $occupancyRate,
                    "revenue_today": $revenueToday
                }
            }
        """.trimIndent()
    }
}