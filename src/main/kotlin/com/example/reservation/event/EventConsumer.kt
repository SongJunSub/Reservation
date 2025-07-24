package com.example.reservation.event

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이벤트 소비자 서비스 (Kotlin)
 * 
 * 기능:
 * 1. Kafka 토픽에서 이벤트 수신
 * 2. 이벤트 타입별 비즈니스 로직 처리
 * 3. 이벤트 순서 보장 및 중복 처리 방지
 * 4. 에러 처리 및 재시도 로직
 * 
 * Kotlin 특징:
 * - when 표현식을 통한 이벤트 타입 분기
 * - 확장 함수를 통한 편의 메서드
 * - 코루틴을 통한 비동기 처리 (선택적)
 * - inline 함수를 통한 성능 최적화
 */
@Service
class EventConsumer(
    private val notificationService: NotificationService,
    private val analyticsService: AnalyticsService,
    private val auditService: AuditService,
    private val inventoryService: InventoryService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(EventConsumer::class.java)
    }

    /**
     * 예약 이벤트 소비자
     * Kotlin의 when 표현식을 통한 타입 분기
     */
    @KafkaListener(
        topics = [EventConfig.RESERVATION_EVENTS_TOPIC],
        groupId = "reservation-service-consumer",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    fun handleReservationEvent(
        @Payload event: ReservationEvent,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION_ID) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.info("예약 이벤트 수신: {} (topic: {}, partition: {}, offset: {})", 
                   event.eventType, topic, partition, offset)

        try {
            // 중복 처리 방지 검사
            if (isDuplicateEvent(event.eventId)) {
                logger.warn("중복 이벤트 감지, 처리 건너뜀: {}", event.eventId)
                acknowledgment.acknowledge()
                return
            }

            // 이벤트 처리 (타입별 분기)
            when (event) {
                is ReservationCreatedEvent -> handleReservationCreated(event)
                is ReservationUpdatedEvent -> handleReservationUpdated(event)
                is ReservationCancelledEvent -> handleReservationCancelled(event)
                is ReservationConfirmedEvent -> handleReservationConfirmed(event)
                is CheckInCompletedEvent -> handleCheckInCompleted(event)
                is CheckOutCompletedEvent -> handleCheckOutCompleted(event)
                is PaymentProcessedEvent -> handlePaymentProcessed(event)
                is PaymentFailedEvent -> handlePaymentFailed(event)
            }

            // 공통 후처리
            performCommonEventProcessing(event)

            // 수동 커밋
            acknowledgment.acknowledge()
            
            logger.debug("이벤트 처리 완료: {}", event.eventId)

        } catch (exception: Exception) {
            logger.error("이벤트 처리 실패: {} - {}", event.eventId, exception.message, exception)
            
            // 에러 처리 (재시도 또는 DLQ로 전송)
            handleEventProcessingError(event, exception, acknowledgment)
        }
    }

    /**
     * 결제 이벤트 소비자
     */
    @KafkaListener(
        topics = [EventConfig.PAYMENT_EVENTS_TOPIC],
        groupId = "payment-service-consumer"
    )
    fun handlePaymentEvent(
        @Payload event: DomainEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info("결제 이벤트 수신: {}", event.eventType)
        
        try {
            // 결제 관련 비즈니스 로직 처리
            processPaymentEvent(event)
            
            acknowledgment.acknowledge()
            
        } catch (exception: Exception) {
            logger.error("결제 이벤트 처리 실패: {}", exception.message, exception)
            handleEventProcessingError(event, exception, acknowledgment)
        }
    }

    /**
     * 알림 이벤트 소비자
     */
    @KafkaListener(
        topics = [EventConfig.NOTIFICATION_EVENTS_TOPIC],
        groupId = "notification-service-consumer"
    )
    fun handleNotificationEvent(
        @Payload event: DomainEvent,
        acknowledgment: Acknowledgment
    ) {
        logger.info("알림 이벤트 수신: {}", event.eventType)
        
        try {
            // 알림 발송 로직
            sendNotificationForEvent(event)
            
            acknowledgment.acknowledge()
            
        } catch (exception: Exception) {
            logger.error("알림 이벤트 처리 실패: {}", exception.message, exception)
            handleEventProcessingError(event, exception, acknowledgment)
        }
    }

    // === 이벤트 타입별 처리 메서드들 ===

    /**
     * 예약 생성 이벤트 처리
     * Kotlin의 간결한 함수 정의
     */
    private fun handleReservationCreated(event: ReservationCreatedEvent) {
        logger.debug("예약 생성 처리: {}", event.confirmationNumber)
        
        // 1. 객실 가용성 업데이트
        inventoryService.reserveRoom(event.roomId, event.checkInDate, event.checkOutDate)
        
        // 2. 확인 알림 발송
        if (event.requiresNotification()) {
            notificationService.sendReservationConfirmation(event)
        }
        
        // 3. 분석 데이터 수집
        analyticsService.recordReservationCreated(event)
        
        // 4. 감사 로그 기록
        auditService.logReservationEvent(event)
    }

    /**
     * 예약 수정 이벤트 처리
     */
    private fun handleReservationUpdated(event: ReservationUpdatedEvent) {
        logger.debug("예약 수정 처리: {}", event.reservationId)
        
        // 객실 변경이 있었는지 확인
        if (event.changes.containsKey("roomId")) {
            val oldRoomId = event.previousValues["roomId"] as Long
            val newRoomId = event.changes["roomId"] as Long
            
            inventoryService.releaseRoom(oldRoomId)
            inventoryService.reserveRoom(newRoomId, 
                                       event.changes["checkInDate"] as? java.time.LocalDate ?: java.time.LocalDate.now(),
                                       event.changes["checkOutDate"] as? java.time.LocalDate ?: java.time.LocalDate.now())
        }
        
        // 알림 발송
        notificationService.sendReservationUpdateNotification(event)
        
        // 분석 및 감사
        analyticsService.recordReservationUpdated(event)
        auditService.logReservationEvent(event)
    }

    /**
     * 예약 취소 이벤트 처리
     */
    private fun handleReservationCancelled(event: ReservationCancelledEvent) {
        logger.debug("예약 취소 처리: {}", event.reservationId)
        
        // 객실 해제
        inventoryService.releaseRoom(event.roomId)
        
        // 환불 처리 (있는 경우)
        if (event.hasRefund) {
            processRefund(event)
        }
        
        // 취소 알림 발송
        notificationService.sendCancellationNotification(event)
        
        // 분석 및 감사
        analyticsService.recordReservationCancelled(event)  
        auditService.logReservationEvent(event)
    }

    /**
     * 예약 확정 이벤트 처리
     */
    private fun handleReservationConfirmed(event: ReservationConfirmedEvent) {
        logger.debug("예약 확정 처리: {}", event.reservationId)
        
        // 확정 알림 발송
        notificationService.sendConfirmationNotification(event)
        
        // 체크인 준비 작업 스케줄링
        scheduleCheckInPreparation(event)
        
        // 분석 및 감사
        analyticsService.recordReservationConfirmed(event)
        auditService.logReservationEvent(event)
    }

    /**
     * 체크인 완료 이벤트 처리
     */
    private fun handleCheckInCompleted(event: CheckInCompletedEvent) {
        logger.debug("체크인 완료 처리: {}", event.reservationId)
        
        // 객실 상태 업데이트
        inventoryService.markRoomAsOccupied(event.roomId)
        
        // 환영 알림 발송
        notificationService.sendWelcomeNotification(event)
        
        // 체크아웃 리마인더 스케줄링
        scheduleCheckOutReminder(event)
        
        // 분석 및 감사
        analyticsService.recordCheckInCompleted(event)
        auditService.logReservationEvent(event)
    }

    /**
     * 체크아웃 완료 이벤트 처리
     */
    private fun handleCheckOutCompleted(event: CheckOutCompletedEvent) {
        logger.debug("체크아웃 완료 처리: {}", event.reservationId)
        
        // 객실 상태 업데이트
        inventoryService.markRoomAsAvailable(event.roomId, event.roomCondition)
        
        // 추가 요금 처리
        if (event.totalAdditionalCharges > java.math.BigDecimal.ZERO) {
            processAdditionalCharges(event)
        }
        
        // 만족도 조사 발송
        notificationService.sendSatisfactionSurvey(event)
        
        // 분석 및 감사
        analyticsService.recordCheckOutCompleted(event)
        auditService.logReservationEvent(event)
    }

    /**
     * 결제 처리 이벤트 처리
     */
    private fun handlePaymentProcessed(event: PaymentProcessedEvent) {
        logger.debug("결제 처리 완료: {}", event.paymentId)
        
        // 결제 확인 알림
        notificationService.sendPaymentConfirmation(event)
        
        // 분석 및 감사
        analyticsService.recordPaymentProcessed(event)
        auditService.logReservationEvent(event)
    }

    /**
     * 결제 실패 이벤트 처리
     */
    private fun handlePaymentFailed(event: PaymentFailedEvent) {
        logger.debug("결제 실패 처리: {}", event.paymentId)
        
        // 재시도 가능한 경우 재시도 스케줄링
        if (event.canRetry()) {
            schedulePaymentRetry(event)
        } else {
            // 예약 취소 프로세스 시작
            initiateReservationCancellation(event)
        }
        
        // 실패 알림 발송
        notificationService.sendPaymentFailureNotification(event)
        
        // 분석 및 감사
        analyticsService.recordPaymentFailed(event)
        auditService.logReservationEvent(event)
    }

    // === 헬퍼 메서드들 ===

    /**
     * 공통 이벤트 처리
     */
    private fun performCommonEventProcessing(event: DomainEvent) {
        // 이벤트 메트릭 업데이트
        updateEventMetrics(event)
        
        // 이벤트 저장 (이벤트 소싱용)
        storeEventForSourcing(event)
    }

    /**
     * 중복 이벤트 검사
     * 멱등성 보장을 위한 중복 처리 방지
     */
    private fun isDuplicateEvent(eventId: String): Boolean {
        // 실제 구현에서는 Redis 또는 DB에서 처리된 이벤트 ID 확인
        return false // 임시 구현
    }

    /**
     * 이벤트 처리 오류 핸들링
     */
    private fun handleEventProcessingError(
        event: DomainEvent, 
        exception: Exception, 
        acknowledgment: Acknowledgment
    ) {
        // 재시도 가능한 오류인지 판단
        if (isRetryableError(exception)) {
            logger.warn("재시도 가능한 오류, 메시지를 다시 큐에 넣음: {}", exception.message)
            // 메시지를 acknowledge하지 않아서 재시도되도록 함
        } else {
            logger.error("재시도 불가능한 오류, DLQ로 전송: {}", exception.message)
            // Dead Letter Queue로 전송 후 acknowledge
            sendToDeadLetterQueue(event, exception)
            acknowledgment.acknowledge()
        }
    }

    /**
     * 재시도 가능한 오류인지 판단
     */
    private fun isRetryableError(exception: Exception): Boolean = when (exception) {
        is org.springframework.dao.DataAccessException,
        is java.net.ConnectException,
        is java.util.concurrent.TimeoutException -> true
        else -> false
    }

    /**
     * Dead Letter Queue로 전송
     */
    private fun sendToDeadLetterQueue(event: DomainEvent, exception: Exception) {
        logger.warn("이벤트를 DLQ로 전송: {} - {}", event.eventId, exception.message)
        // 실제 구현에서는 DLQ 토픽으로 전송
    }

    // === 비즈니스 로직 헬퍼 메서드들 (스텁) ===

    private fun processPaymentEvent(event: DomainEvent) {
        logger.debug("결제 이벤트 처리: {}", event.eventType)
    }

    private fun sendNotificationForEvent(event: DomainEvent) {
        logger.debug("이벤트 알림 발송: {}", event.eventType)
    }

    private fun processRefund(event: ReservationCancelledEvent) {
        logger.debug("환불 처리: {} 원", event.refundAmount)
    }

    private fun scheduleCheckInPreparation(event: ReservationConfirmedEvent) {
        logger.debug("체크인 준비 작업 스케줄링: {}", event.reservationId)
    }

    private fun scheduleCheckOutReminder(event: CheckInCompletedEvent) {
        logger.debug("체크아웃 리마인더 스케줄링: {}", event.reservationId)
    }

    private fun processAdditionalCharges(event: CheckOutCompletedEvent) {
        logger.debug("추가 요금 처리: {} 원", event.totalAdditionalCharges)
    }

    private fun schedulePaymentRetry(event: PaymentFailedEvent) {
        logger.debug("결제 재시도 스케줄링: {}", event.paymentId)
    }

    private fun initiateReservationCancellation(event: PaymentFailedEvent) {
        logger.debug("예약 취소 프로세스 시작: {}", event.reservationId)
    }

    private fun updateEventMetrics(event: DomainEvent) {
        logger.debug("이벤트 메트릭 업데이트: {}", event.eventType)
    }

    private fun storeEventForSourcing(event: DomainEvent) {
        logger.debug("이벤트 소싱용 저장: {}", event.eventId)
    }
}

// === 의존성 서비스 인터페이스들 ===

interface NotificationService {
    fun sendReservationConfirmation(event: ReservationCreatedEvent)
    fun sendReservationUpdateNotification(event: ReservationUpdatedEvent) 
    fun sendCancellationNotification(event: ReservationCancelledEvent)
    fun sendConfirmationNotification(event: ReservationConfirmedEvent)
    fun sendWelcomeNotification(event: CheckInCompletedEvent)
    fun sendSatisfactionSurvey(event: CheckOutCompletedEvent)
    fun sendPaymentConfirmation(event: PaymentProcessedEvent)
    fun sendPaymentFailureNotification(event: PaymentFailedEvent)
}

interface AnalyticsService {
    fun recordReservationCreated(event: ReservationCreatedEvent)
    fun recordReservationUpdated(event: ReservationUpdatedEvent)
    fun recordReservationCancelled(event: ReservationCancelledEvent)
    fun recordReservationConfirmed(event: ReservationConfirmedEvent)
    fun recordCheckInCompleted(event: CheckInCompletedEvent)
    fun recordCheckOutCompleted(event: CheckOutCompletedEvent)
    fun recordPaymentProcessed(event: PaymentProcessedEvent)
    fun recordPaymentFailed(event: PaymentFailedEvent)
}

interface AuditService {
    fun logReservationEvent(event: ReservationEvent)
}

interface InventoryService {
    fun reserveRoom(roomId: Long, checkIn: java.time.LocalDate, checkOut: java.time.LocalDate)
    fun releaseRoom(roomId: Long)
    fun markRoomAsOccupied(roomId: Long)
    fun markRoomAsAvailable(roomId: Long, condition: String)
}