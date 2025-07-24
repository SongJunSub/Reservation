package com.example.reservation.event

import com.example.reservation.event.EventConfig.Companion.ANALYTICS_EVENTS_TOPIC
import com.example.reservation.event.EventConfig.Companion.AUDIT_EVENTS_TOPIC
import com.example.reservation.event.EventConfig.Companion.NOTIFICATION_EVENTS_TOPIC
import com.example.reservation.event.EventConfig.Companion.PAYMENT_EVENTS_TOPIC
import com.example.reservation.event.EventConfig.Companion.RESERVATION_EVENTS_TOPIC
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import org.springframework.util.concurrent.ListenableFutureCallback
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

/**
 * 이벤트 발행자 서비스 (Kotlin)
 * 
 * 기능:
 * 1. 도메인 이벤트를 Kafka로 발행
 * 2. 토픽별 라우팅 및 파티셔닝
 * 3. 비동기 발송 및 콜백 처리
 * 4. 이벤트 메타데이터 자동 추가
 * 
 * Kotlin 특징:
 * - 확장 함수를 통한 편의 메서드
 * - when 표현식을 통한 토픽 라우팅
 * - 코루틴을 통한 비동기 처리 (선택적)
 * - inline 함수를 통한 성능 최적화
 */
@Service
class EventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(EventPublisher::class.java)
    }

    /**
     * 도메인 이벤트 발행 (주 메서드)
     * Kotlin의 기본 매개변수와 when 표현식 활용
     */
    fun publishEvent(
        event: DomainEvent,
        partitionKey: String? = null,
        headers: Map<String, Any> = emptyMap()
    ): CompletableFuture<SendResult<String, Any>> {
        
        // 토픽 결정
        val topic = determineTopicForEvent(event)
        
        // 파티션 키 결정 (null이면 라운드 로빈)
        val key = partitionKey ?: event.aggregateId
        
        // 메타데이터 자동 추가
        val enrichedEvent = enrichEventWithMetadata(event, headers)
        
        logger.debug("이벤트 발행: {} -> {} (key: {})", event.eventType, topic, key)
        
        // Kafka로 이벤트 발송
        val future = kafkaTemplate.send(topic, key, enrichedEvent)
        
        // 콜백 설정
        future.addCallback(object : ListenableFutureCallback<SendResult<String, Any>> {
            override fun onSuccess(result: SendResult<String, Any>?) {
                logger.info("이벤트 발송 성공: {} -> {} (offset: {})", 
                          event.eventType, topic, result?.recordMetadata?.offset())
                
                // 메트릭 수집 (실제 구현에서는 Micrometer 사용)
                recordEventPublishSuccess(event.eventType, topic)
            }

            override fun onFailure(ex: Throwable) {
                logger.error("이벤트 발송 실패: {} -> {} : {}", 
                           event.eventType, topic, ex.message, ex)
                
                // 메트릭 수집
                recordEventPublishFailure(event.eventType, topic, ex)
                
                // Dead Letter Queue 처리 (실제 구현에서 추가)
                handleFailedEvent(event, topic, ex)
            }
        })
        
        return future
    }

    /**
     * 예약 이벤트 발행 (타입 안전한 메서드)
     * Kotlin의 타입 안전성 활용
     */
    fun publishReservationEvent(event: ReservationEvent): CompletableFuture<SendResult<String, Any>> {
        return publishEvent(
            event = event,
            partitionKey = event.reservationId.toString(),
            headers = mapOf(
                "eventCategory" to "reservation",
                "severity" to event.getSeverity().name,
                "requiresNotification" to event.requiresNotification()
            )
        )
    }

    /**
     * 배치 이벤트 발행
     * Kotlin 컬렉션 함수와 병렬 처리
     */
    fun publishEvents(events: List<DomainEvent>): List<CompletableFuture<SendResult<String, Any>>> {
        logger.info("배치 이벤트 발행 시작: {} events", events.size)
        
        return events.map { event ->
            publishEvent(event)
        }.also {
            logger.info("배치 이벤트 발행 완료: {} futures created", it.size)
        }
    }

    /**
     * 지연 이벤트 발행
     * 특정 시간 후에 이벤트 발행 (스케줄링)
     */
    fun publishDelayedEvent(
        event: DomainEvent,
        delayMillis: Long
    ): CompletableFuture<SendResult<String, Any>> {
        logger.debug("지연 이벤트 예약: {} ({}ms 후)", event.eventType, delayMillis)
        
        return CompletableFuture.supplyAsync {
            Thread.sleep(delayMillis)
            publishEvent(event).get() // 블로킹 호출
        }
    }

    /**
     * 조건부 이벤트 발행
     * 조건을 만족할 때만 이벤트 발행
     */
    inline fun publishEventIf(
        event: DomainEvent,
        condition: (DomainEvent) -> Boolean
    ): CompletableFuture<SendResult<String, Any>>? {
        return if (condition(event)) {
            logger.debug("조건부 이벤트 발행: {} (조건 만족)", event.eventType)
            publishEvent(event)
        } else {
            logger.debug("조건부 이벤트 발행: {} (조건 불만족)", event.eventType)
            null
        }
    }

    /**
     * 트랜잭션 이벤트 발행
     * 트랜잭션 커밋 후에 이벤트 발행
     */
    @org.springframework.transaction.event.TransactionalEventListener(
        phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
    )
    fun publishAfterTransaction(event: DomainEvent) {
        logger.debug("트랜잭션 완료 후 이벤트 발행: {}", event.eventType)
        publishEvent(event)
    }

    // === Private 헬퍼 메서드들 ===

    /**
     * 이벤트 타입에 따른 토픽 결정
     * Kotlin when 표현식의 간결함
     */
    private fun determineTopicForEvent(event: DomainEvent): String = when (event) {
        is ReservationEvent -> RESERVATION_EVENTS_TOPIC
        is PaymentEvent -> PAYMENT_EVENTS_TOPIC
        is NotificationEvent -> NOTIFICATION_EVENTS_TOPIC
        is AuditEvent -> AUDIT_EVENTS_TOPIC
        is AnalyticsEvent -> ANALYTICS_EVENTS_TOPIC
        else -> RESERVATION_EVENTS_TOPIC // 기본 토픽
    }

    /**
     * 이벤트에 메타데이터 추가
     * Kotlin의 copy 메서드와 + 연산자 활용
     */
    private fun enrichEventWithMetadata(
        event: DomainEvent, 
        additionalHeaders: Map<String, Any>
    ): DomainEvent {
        val enrichedMetadata = event.metadata + mapOf(
            "publishedAt" to LocalDateTime.now(),
            "publisher" to "reservation-service",
            "environment" to getEnvironment(),
            "traceId" to getCurrentTraceId()
        ) + additionalHeaders

        return when (event) {
            is ReservationEvent -> event.withMetadata(enrichedMetadata)
            else -> event // 다른 이벤트 타입은 구현에 따라 처리
        }
    }

    /**
     * 실패한 이벤트 처리
     */
    private fun handleFailedEvent(event: DomainEvent, topic: String, exception: Throwable) {
        // Dead Letter Queue로 전송하거나 재시도 로직 구현
        logger.warn("실패한 이벤트 처리 필요: {} -> {}", event.eventType, topic)
    }

    /**
     * 성공 메트릭 기록
     */
    private fun recordEventPublishSuccess(eventType: String, topic: String) {
        // Micrometer 또는 기타 메트릭 시스템과 연동
        logger.debug("메트릭 기록: 이벤트 발송 성공 - {} -> {}", eventType, topic)
    }

    /**
     * 실패 메트릭 기록
     */
    private fun recordEventPublishFailure(eventType: String, topic: String, exception: Throwable) {
        // Micrometer 또는 기타 메트릭 시스템과 연동
        logger.debug("메트릭 기록: 이벤트 발송 실패 - {} -> {} : {}", eventType, topic, exception.message)
    }

    /**
     * 현재 환경 정보 조회
     */
    private fun getEnvironment(): String = 
        System.getProperty("spring.profiles.active", "unknown")

    /**
     * 현재 트레이스 ID 조회 (분산 추적)
     */
    private fun getCurrentTraceId(): String = 
        // 실제 구현에서는 Sleuth/Zipkin 등의 트레이스 ID 사용
        Thread.currentThread().name + "-" + System.nanoTime()
}

// === 다른 이벤트 타입들 (인터페이스만 정의) ===

interface PaymentEvent : DomainEvent
interface NotificationEvent : DomainEvent  
interface AuditEvent : DomainEvent
interface AnalyticsEvent : DomainEvent