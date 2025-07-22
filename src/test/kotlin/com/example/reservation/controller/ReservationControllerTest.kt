package com.example.reservation.controller

import com.example.reservation.service.ReservationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.mockito.Mockito.*
import java.util.*

@WebMvcTest(ReservationController::class, excludeAutoConfiguration = [org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration::class])
class ReservationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var reservationService: ReservationService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `GET 모든 예약 조회 API 테스트`() {
        // Given
        `when`(reservationService.findAll()).thenReturn(emptyList())

        // When & Then
        mockMvc.perform(get("/api/reservations"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `GET 단일 예약 조회 API 테스트 - 존재하지 않는 경우`() {
        // Given
        val reservationId = UUID.randomUUID()
        `when`(reservationService.findById(reservationId)).thenReturn(null)

        // When & Then
        mockMvc.perform(get("/api/reservations/$reservationId"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE 예약 삭제 API 테스트 - 존재하지 않는 경우`() {
        // Given
        val reservationId = UUID.randomUUID()
        `when`(reservationService.delete(reservationId)).thenReturn(false)

        // When & Then
        mockMvc.perform(delete("/api/reservations/$reservationId"))
            .andExpect(status().isNotFound)
    }
}