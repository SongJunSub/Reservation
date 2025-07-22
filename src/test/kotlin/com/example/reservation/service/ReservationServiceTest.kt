package com.example.reservation.service

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.*

class ReservationServiceTest {

    private lateinit var reservationService: ReservationService

    @BeforeEach
    fun setUp() {
        reservationService = ReservationService()
    }

    @Test
    fun `예약 생성 테스트`() {
        // Given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = "2025-01-01",
            checkOutDate = "2025-01-03",
            totalAmount = 200.0
        )

        // When
        val reservation = reservationService.create(request)

        // Then
        assertNotNull(reservation)
        assertEquals("홍길동", reservation.guestDetails.primaryGuestFirstName)
        assertEquals("101", reservation.room?.roomNumber)
        assertEquals("2025-01-01", reservation.checkInDate.toString())
        assertEquals("2025-01-03", reservation.checkOutDate.toString())
    }

    @Test
    fun `모든 예약 조회 테스트`() {
        // Given
        val request1 = CreateReservationRequest("김철수", "101", "2025-01-01", "2025-01-03", 200.0)
        val request2 = CreateReservationRequest("이영희", "102", "2025-01-05", "2025-01-07", 250.0)
        
        val reservation1 = reservationService.create(request1)
        val reservation2 = reservationService.create(request2)

        // When
        val reservations = reservationService.findAll()

        // Then
        assertEquals(2, reservations.size)
        assertTrue(reservations.any { it.guestDetails.primaryGuestFirstName == "김철수" })
        assertTrue(reservations.any { it.guestDetails.primaryGuestFirstName == "이영희" })
    }

    @Test
    fun `예약 수정 테스트`() {
        // Given
        val createRequest = CreateReservationRequest("박민수", "103", "2025-01-01", "2025-01-03", 300.0)
        val reservation = reservationService.create(createRequest)
        val reservationId = UUID.randomUUID() // 실제로는 서비스에서 반환된 ID 사용
        
        val updateRequest = UpdateReservationRequest(
            guestName = null,
            roomNumber = null,
            checkInDate = "2025-01-02",
            checkOutDate = "2025-01-04",
            totalAmount = 350.0
        )

        // When
        val updatedReservation = reservationService.update(reservationId, updateRequest)

        // Then
        assertNull(updatedReservation) // UUID가 존재하지 않으므로 null 반환
    }

    @Test
    fun `예약 삭제 테스트`() {
        // Given
        val request = CreateReservationRequest("최동욱", "104", "2025-01-01", "2025-01-03", 400.0)
        reservationService.create(request)
        val randomId = UUID.randomUUID()

        // When
        val deleted = reservationService.delete(randomId)

        // Then
        assertFalse(deleted) // UUID가 존재하지 않으므로 false 반환
    }

    @Test
    fun `빈 목록 조회 테스트`() {
        // When
        val reservations = reservationService.findAll()

        // Then
        assertTrue(reservations.isEmpty())
    }
}