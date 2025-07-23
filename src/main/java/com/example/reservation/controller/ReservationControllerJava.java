package com.example.reservation.controller;

import com.example.reservation.domain.reservation.Reservation;
// ReservationServiceJava는 같은 패키지에 있음
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/java/reservations")
public class ReservationControllerJava {

    private final ReservationServiceJava reservationService;

    // 생성자 주입
    public ReservationControllerJava(ReservationServiceJava reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public ResponseEntity<List<Reservation>> getAllReservations() {
        List<Reservation> reservations = reservationService.findAll();
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reservation> getReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.findById(id);
        if (reservation != null) {
            return ResponseEntity.ok(reservation);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Reservation> createReservation(@RequestBody CreateReservationRequestJava request) {
        Reservation reservation = reservationService.createFromJavaRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservation);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reservation> updateReservation(
            @PathVariable UUID id,
            @RequestBody UpdateReservationRequestJava request) {
        Reservation updatedReservation = reservationService.updateFromJavaRequest(id, request);
        if (updatedReservation != null) {
            return ResponseEntity.ok(updatedReservation);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(@PathVariable UUID id) {
        boolean deleted = reservationService.delete(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

// Java DTO 클래스들 (package-private for same-package access)
class CreateReservationRequestJava {
    private String guestName;
    private String roomNumber;
    private String checkInDate;
    private String checkOutDate;
    private Double totalAmount;

    // 기본 생성자
    public CreateReservationRequestJava() {}

    // 전체 생성자
    public CreateReservationRequestJava(String guestName, String roomNumber, 
                                       String checkInDate, String checkOutDate, 
                                       Double totalAmount) {
        this.guestName = guestName;
        this.roomNumber = roomNumber;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalAmount = totalAmount;
    }

    // Getters
    public String getGuestName() { return guestName; }
    public String getRoomNumber() { return roomNumber; }
    public String getCheckInDate() { return checkInDate; }
    public String getCheckOutDate() { return checkOutDate; }
    public Double getTotalAmount() { return totalAmount; }

    // Setters
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}

class UpdateReservationRequestJava {
    private String guestName;
    private String roomNumber;
    private String checkInDate;
    private String checkOutDate;
    private Double totalAmount;

    // 기본 생성자
    public UpdateReservationRequestJava() {}

    // 전체 생성자
    public UpdateReservationRequestJava(String guestName, String roomNumber, 
                                       String checkInDate, String checkOutDate, 
                                       Double totalAmount) {
        this.guestName = guestName;
        this.roomNumber = roomNumber;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.totalAmount = totalAmount;
    }

    // Getters
    public String getGuestName() { return guestName; }
    public String getRoomNumber() { return roomNumber; }
    public String getCheckInDate() { return checkInDate; }
    public String getCheckOutDate() { return checkOutDate; }
    public Double getTotalAmount() { return totalAmount; }

    // Setters
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setCheckInDate(String checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(String checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}