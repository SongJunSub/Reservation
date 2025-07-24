package com.example.reservation.service

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.controller.CreateReservationRequestWebFlux
import com.example.reservation.controller.UpdateReservationRequestWebFlux
import com.example.reservation.domain.reservation.Reservation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.util.*

/**
 * 예약 서비스 파사드
 * 컨트롤러와 비즈니스 서비스 사이의 인터페이스 역할
 */
@Service
class ReservationService(
    private val businessService: ReservationBusinessService
) {

    /**
     * 페이징된 예약 목록 조회
     */
    fun findAll(pageable: Pageable): Page<Reservation> {
        return businessService.getAllReservations(pageable)
    }

    /**
     * 모든 예약 목록 조회 (하위 호환성)
     */
    fun findAll(): List<Reservation> {
        return businessService.getAllReservations(Pageable.unpaged()).content
    }

    /**
     * ID로 예약 조회 (UUID -> Long 변환)
     */
    fun findById(id: UUID): Reservation? {
        // UUID를 Long ID로 변환하는 로직 (예: hash 또는 매핑)
        val longId = id.hashCode().toLong()
        return businessService.getReservationById(longId)
    }

    /**
     * Long ID로 예약 조회
     */
    fun findById(id: Long): Reservation? {
        return businessService.getReservationById(id)
    }

    /**
     * 확인번호로 예약 조회
     */
    fun findByConfirmationNumber(confirmationNumber: String): Reservation? {
        return businessService.getReservationByConfirmationNumber(confirmationNumber)
    }

    /**
     * 예약 생성
     */
    fun create(request: CreateReservationRequest): Reservation {
        return businessService.createReservation(request)
    }

    /**
     * 예약 수정 (UUID 버전)
     */
    fun update(id: UUID, request: UpdateReservationRequest): Reservation? {
        val longId = id.hashCode().toLong()
        return businessService.updateReservation(longId, request)
    }

    /**
     * 예약 수정 (Long 버전)
     */
    fun update(id: Long, request: UpdateReservationRequest): Reservation? {
        return businessService.updateReservation(id, request)
    }

    /**
     * 예약 삭제 (UUID 버전)
     */
    fun delete(id: UUID): Boolean {
        val longId = id.hashCode().toLong()
        return businessService.deleteReservation(longId)
    }

    /**
     * 예약 삭제 (Long 버전)
     */
    fun delete(id: Long): Boolean {
        return businessService.deleteReservation(id)
    }

    /**
     * 예약 취소
     */
    fun cancel(id: Long, reason: String?): Reservation? {
        return businessService.cancelReservation(id, reason ?: "고객 요청")
    }

    // === WebFlux 호환 메서드들 ===

    /**
     * WebFlux 요청으로 예약 생성
     */
    fun createFromWebFluxRequest(request: CreateReservationRequestWebFlux): Reservation {
        return create(CreateReservationRequest(
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            totalAmount = request.totalAmount
        ))
    }

    /**
     * WebFlux 요청으로 예약 수정
     */
    fun updateFromWebFluxRequest(id: UUID, request: UpdateReservationRequestWebFlux): Reservation? {
        return update(id, UpdateReservationRequest(
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            totalAmount = request.totalAmount
        ))
    }

    // === 리액티브 스트림 메서드들 ===

    /**
     * 실시간 예약 스트림
     */
    fun getReservationStream(): Flux<Reservation> {
        return businessService.getReservationStream()
    }

    /**
     * 고객별 예약 이력 스트림
     */
    fun getGuestReservationStream(email: String): Flux<Reservation> {
        return businessService.getGuestReservationStream(email)
    }

    /**
     * 오늘 체크인 스트림
     */
    fun getTodayCheckInsStream(): Flux<Reservation> {
        return businessService.getTodayCheckInsStream()
    }
}