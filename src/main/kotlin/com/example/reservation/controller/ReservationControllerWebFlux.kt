package com.example.reservation.controller

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.service.ReservationService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

@RestController
@RequestMapping("/api/webflux/reservations")
class ReservationControllerWebFlux(
    private val reservationService: ReservationService
) {

    @GetMapping
    fun getAllReservations(): Flux<Reservation> {
        return Flux.fromIterable(reservationService.findAll())
    }

    @GetMapping("/{id}")
    fun getReservation(@PathVariable id: UUID): Mono<Reservation> {
        val reservation = reservationService.findById(id)
        return if (reservation != null) {
            Mono.just(reservation)
        } else {
            Mono.empty()
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createReservation(@RequestBody request: CreateReservationRequestWebFlux): Mono<Reservation> {
        return Mono.fromCallable {
            reservationService.createFromWebFluxRequest(request)
        }
    }

    @PutMapping("/{id}")
    fun updateReservation(
        @PathVariable id: UUID,
        @RequestBody request: UpdateReservationRequestWebFlux
    ): Mono<Reservation> {
        return Mono.fromCallable {
            reservationService.updateFromWebFluxRequest(id, request)
        }.filter { it != null }
            .cast(Reservation::class.java)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteReservation(@PathVariable id: UUID): Mono<Void> {
        return Mono.fromCallable {
            reservationService.delete(id)
        }.filter { it }
            .then()
    }

    // 추가적인 리액티브 엔드포인트들
    @GetMapping("/stream")
    fun streamAllReservations(): Flux<Reservation> {
        return Flux.fromIterable(reservationService.findAll())
            .delayElements(java.time.Duration.ofSeconds(1)) // 1초마다 하나씩 스트리밍
    }

    @GetMapping("/guest/{guestName}")
    fun getReservationsByGuest(@PathVariable guestName: String): Flux<Reservation> {
        return Flux.fromIterable(reservationService.findAll())
            .filter { it.guestDetails.primaryGuestFirstName.contains(guestName, ignoreCase = true) }
    }

    @GetMapping("/count")
    fun getReservationCount(): Mono<Long> {
        return Flux.fromIterable(reservationService.findAll())
            .count()
    }
}

// WebFlux용 DTO (Kotlin data class의 간결함 활용)
data class CreateReservationRequestWebFlux(
    val guestName: String,
    val roomNumber: String,
    val checkInDate: String,
    val checkOutDate: String,
    val totalAmount: Double
)

data class UpdateReservationRequestWebFlux(
    val guestName: String?,
    val roomNumber: String?,
    val checkInDate: String?,
    val checkOutDate: String?,
    val totalAmount: Double?
)