package com.example.reservation.service.impl

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.service.AuditService
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 감사 서비스 구현체
 * 모든 비즈니스 이벤트를 추적하고 로깅
 */
@Service
class AuditServiceImpl : AuditService {
    
    private val logger = LoggerFactory.getLogger(AuditServiceImpl::class.java)
    
    override fun logReservationCreated(reservation: Reservation) {
        val auditLog = createAuditLog(
            action = "RESERVATION_CREATED",
            entityId = reservation.id.toString(),
            details = mapOf(
                "confirmationNumber" to reservation.confirmationNumber,
                "guestEmail" to reservation.guest.email,
                "roomNumber" to reservation.room.roomNumber,
                "checkInDate" to reservation.checkInDate.toString(),
                "checkOutDate" to reservation.checkOutDate.toString(),
                "totalAmount" to reservation.totalAmount.toString()
            )
        )
        
        logger.info("감사 로그: $auditLog")
        
        // 실제 구현에서는 감사 데이터베이스에 저장
        // auditRepository.save(auditLog)
        // elasticsearchService.indexAuditLog(auditLog)
    }
    
    override fun logReservationUpdated(reservation: Reservation) {
        val auditLog = createAuditLog(
            action = "RESERVATION_UPDATED",
            entityId = reservation.id.toString(),
            details = mapOf(
                "confirmationNumber" to reservation.confirmationNumber,
                "lastModifiedAt" to reservation.lastModifiedAt.toString()
            )
        )
        
        logger.info("감사 로그: $auditLog")
    }
    
    override fun logReservationCancelled(reservation: Reservation, reason: String?) {
        val auditLog = createAuditLog(
            action = "RESERVATION_CANCELLED",
            entityId = reservation.id.toString(),
            details = mapOf(
                "confirmationNumber" to reservation.confirmationNumber,
                "reason" to (reason ?: "사유 없음"),
                "cancelledAt" to LocalDateTime.now().toString()
            )
        )
        
        logger.warn("예약 취소 감사 로그: $auditLog")
    }
    
    private fun createAuditLog(
        action: String,
        entityId: String,
        details: Map<String, String>
    ): AuditLog {
        return AuditLog(
            timestamp = LocalDateTime.now(),
            action = action,
            entityType = "RESERVATION",
            entityId = entityId,
            userId = getCurrentUserId(), // 실제로는 Security Context에서 가져옴
            details = details
        )
    }
    
    private fun getCurrentUserId(): String {
        // 실제로는 Spring Security Context에서 현재 사용자 ID 추출
        // return SecurityContextHolder.getContext().authentication.name
        return "system" // 임시값
    }
    
    /**
     * 감사 로그 데이터 클래스
     */
    data class AuditLog(
        val timestamp: LocalDateTime,
        val action: String,
        val entityType: String,
        val entityId: String,
        val userId: String,
        val details: Map<String, String>
    )
}