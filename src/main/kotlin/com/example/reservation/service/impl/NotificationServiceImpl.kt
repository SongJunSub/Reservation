package com.example.reservation.service.impl

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.service.NotificationService
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

/**
 * 알림 서비스 구현체
 * 실제 운영환경에서는 이메일, SMS, 푸시 알림 등을 발송
 */
@Service
class NotificationServiceImpl : NotificationService {
    
    private val logger = LoggerFactory.getLogger(NotificationServiceImpl::class.java)
    
    override fun sendReservationConfirmation(reservation: Reservation) {
        logger.info("=== 예약 확인 알림 전송 ===")
        logger.info("확인번호: ${reservation.confirmationNumber}")
        logger.info("고객: ${reservation.guest.firstName} ${reservation.guest.lastName}")
        logger.info("체크인: ${reservation.checkInDate}")
        logger.info("체크아웃: ${reservation.checkOutDate}")
        logger.info("객실: ${reservation.room.roomNumber}")
        logger.info("금액: ${reservation.totalAmount}")
        
        // 실제 구현에서는 이메일 템플릿을 사용하여 발송
        // emailService.sendConfirmationEmail(reservation.guest.email, reservation)
        // smsService.sendConfirmationSms(reservation.guest.phoneNumber, reservation)
    }
    
    override fun sendCancellationNotification(reservation: Reservation, reason: String?) {
        logger.info("=== 예약 취소 알림 전송 ===")
        logger.info("확인번호: ${reservation.confirmationNumber}")
        logger.info("고객: ${reservation.guest.firstName} ${reservation.guest.lastName}")
        logger.info("취소 사유: ${reason ?: "사유 없음"}")
        
        // 실제 구현에서는 취소 알림 이메일 발송
        // emailService.sendCancellationEmail(reservation.guest.email, reservation, reason)
    }
}