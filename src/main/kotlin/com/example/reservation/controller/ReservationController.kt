package com.example.reservation.controller

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.service.ReservationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/reservations")
class ReservationController(
    private val reservationService: ReservationService
) {

    @GetMapping
    fun getAllReservations(): ResponseEntity<List<Reservation>> {
        val reservations = reservationService.findAll()
        return ResponseEntity.ok(reservations)
    }

    @GetMapping("/{id}")
    fun getReservation(@PathVariable id: UUID): ResponseEntity<Reservation> {
        val reservation = reservationService.findById(id)
        return if (reservation != null) {
            ResponseEntity.ok(reservation)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping
    fun createReservation(@RequestBody request: CreateReservationRequest): ResponseEntity<Reservation> {
        val reservation = reservationService.create(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation)
    }

    @PutMapping("/{id}")
    fun updateReservation(
        @PathVariable id: UUID,
        @RequestBody request: UpdateReservationRequest
    ): ResponseEntity<Reservation> {
        val updatedReservation = reservationService.update(id, request)
        return if (updatedReservation != null) {
            ResponseEntity.ok(updatedReservation)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteReservation(@PathVariable id: UUID): ResponseEntity<Void> {
        val deleted = reservationService.delete(id)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

data class CreateReservationRequest(
    val guestName: String,
    val roomNumber: String,
    val checkInDate: String,
    val checkOutDate: String,
    val totalAmount: Double
)

data class UpdateReservationRequest(
    val guestName: String?,
    val roomNumber: String?,
    val checkInDate: String?,
    val checkOutDate: String?,
    val totalAmount: Double?
)