package com.example.reservation.service

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.controller.CreateReservationRequestWebFlux
import com.example.reservation.controller.UpdateReservationRequestWebFlux
import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.domain.reservation.PaymentStatus
import com.example.reservation.domain.reservation.ReservationSource
import com.example.reservation.domain.reservation.ReservationGuestDetails
import com.example.reservation.domain.reservation.ReservationPreferences
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class ReservationService {

    private val reservations = ConcurrentHashMap<UUID, Reservation>()

    fun findAll(): List<Reservation> {
        return reservations.values.toList()
    }

    fun findById(id: UUID): Reservation? {
        return reservations[id]
    }

    fun create(request: CreateReservationRequest): Reservation {
        // Create temporary guest and room objects
        val tempGuest = com.example.reservation.domain.guest.Guest(
            firstName = request.guestName.split(" ").first(),
            lastName = request.guestName.split(" ").lastOrNull() ?: "",
            email = "guest@example.com"
        )
        
        val tempAddress = com.example.reservation.domain.guest.Address(
            street = "123 Main St",
            city = "City",
            postalCode = "12345",
            countryCode = "US"
        )
        
        val tempProperty = com.example.reservation.domain.room.Property(
            name = "Sample Hotel",
            type = com.example.reservation.domain.room.PropertyType.HOTEL,
            category = com.example.reservation.domain.room.PropertyCategory.BUSINESS,
            starRating = 4,
            address = tempAddress
        )
        
        val tempRoom = com.example.reservation.domain.room.Room(
            property = tempProperty,
            roomNumber = request.roomNumber,
            name = "Standard Room",
            type = com.example.reservation.domain.room.RoomType.STANDARD,
            bedType = com.example.reservation.domain.room.BedType.QUEEN,
            maxOccupancy = 2,
            standardOccupancy = 2,
            baseRate = BigDecimal.valueOf(request.totalAmount),
            size = 25.0,
            floor = 1
        )
        
        val reservation = Reservation(
            id = 0L, // JPA will assign the actual ID
            confirmationNumber = generateConfirmationNumber(),
            guest = tempGuest,
            room = tempRoom,
            checkInDate = LocalDate.parse(request.checkInDate),
            checkOutDate = LocalDate.parse(request.checkOutDate),
            numberOfGuests = 1,
            numberOfAdults = 1,
            numberOfChildren = 0,
            totalAmount = BigDecimal.valueOf(request.totalAmount),
            roomRate = BigDecimal.valueOf(request.totalAmount),
            status = ReservationStatus.PENDING,
            paymentStatus = PaymentStatus.PENDING,
            guestDetails = ReservationGuestDetails(
                primaryGuestFirstName = request.guestName.split(" ").first(),
                primaryGuestLastName = request.guestName.split(" ").lastOrNull() ?: "",
                primaryGuestEmail = "guest@example.com"
            ),
            source = ReservationSource.DIRECT
        )
        
        val uuid = UUID.randomUUID()
        reservations[uuid] = reservation
        return reservation
    }

    fun update(id: UUID, request: UpdateReservationRequest): Reservation? {
        val existingReservation = reservations[id] ?: return null
        
        val updatedReservation = existingReservation.copy(
            checkInDate = request.checkInDate?.let { LocalDate.parse(it) } ?: existingReservation.checkInDate,
            checkOutDate = request.checkOutDate?.let { LocalDate.parse(it) } ?: existingReservation.checkOutDate,
            totalAmount = request.totalAmount?.let { BigDecimal.valueOf(it) } ?: existingReservation.totalAmount,
            roomRate = request.totalAmount?.let { BigDecimal.valueOf(it) } ?: existingReservation.roomRate,
            lastModifiedAt = LocalDateTime.now()
        )
        
        reservations[id] = updatedReservation
        return updatedReservation
    }

    fun delete(id: UUID): Boolean {
        return reservations.remove(id) != null
    }

    // WebFlux용 메서드들
    fun createFromWebFluxRequest(request: CreateReservationRequestWebFlux): Reservation {
        return create(CreateReservationRequest(
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            totalAmount = request.totalAmount
        ))
    }

    fun updateFromWebFluxRequest(id: UUID, request: UpdateReservationRequestWebFlux): Reservation? {
        return update(id, UpdateReservationRequest(
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            totalAmount = request.totalAmount
        ))
    }


    private fun generateConfirmationNumber(): String {
        return "CONF-${UUID.randomUUID().toString().substring(0, 8).uppercase()}"
    }
}