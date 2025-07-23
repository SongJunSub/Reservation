package com.example.reservation.controller;

import com.example.reservation.domain.reservation.Reservation;
// ReservationServiceJava는 같은 패키지에 있음
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/webflux-java/reservations")
public class ReservationControllerWebFluxJava {

    private final ReservationServiceJava reservationService;

    public ReservationControllerWebFluxJava(ReservationServiceJava reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public Flux<Reservation> getAllReservations() {
        return Flux.fromIterable(reservationService.findAll());
    }

    @GetMapping("/{id}")
    public Mono<Reservation> getReservation(@PathVariable UUID id) {
        Reservation reservation = reservationService.findById(id);
        if (reservation != null) {
            return Mono.just(reservation);
        } else {
            return Mono.empty();
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Reservation> createReservation(@RequestBody CreateReservationRequestWebFluxJava request) {
        return Mono.fromCallable(() -> 
            reservationService.createFromWebFluxJavaRequest(request)
        );
    }

    @PutMapping("/{id}")
    public Mono<Reservation> updateReservation(
            @PathVariable UUID id,
            @RequestBody UpdateReservationRequestWebFluxJava request) {
        return Mono.fromCallable(() -> 
            reservationService.updateFromWebFluxJavaRequest(id, request)
        ).filter(reservation -> reservation != null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteReservation(@PathVariable UUID id) {
        return Mono.fromCallable(() -> reservationService.delete(id))
                .filter(deleted -> deleted)
                .then();
    }

    // 추가적인 리액티브 엔드포인트들
    @GetMapping("/stream")
    public Flux<Reservation> streamAllReservations() {
        return Flux.fromIterable(reservationService.findAll())
                .delayElements(Duration.ofSeconds(1)); // 1초마다 하나씩 스트리밍
    }

    @GetMapping("/guest/{guestName}")
    public Flux<Reservation> getReservationsByGuest(@PathVariable String guestName) {
        return Flux.fromIterable(reservationService.findAll())
                .filter(reservation -> 
                    reservation.getGuestDetails().getPrimaryGuestFirstName()
                        .toLowerCase().contains(guestName.toLowerCase())
                );
    }

    @GetMapping("/count")
    public Mono<Long> getReservationCount() {
        return Flux.fromIterable(reservationService.findAll())
                .count();
    }
}

// Java WebFlux DTO 클래스들 (package-private)
class CreateReservationRequestWebFluxJava {
    private String guestName;
    private String roomNumber;
    private String checkInDate;
    private String checkOutDate;
    private Double totalAmount;

    // 기본 생성자
    public CreateReservationRequestWebFluxJava() {}

    // 전체 생성자
    public CreateReservationRequestWebFluxJava(String guestName, String roomNumber, 
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

class UpdateReservationRequestWebFluxJava {
    private String guestName;
    private String roomNumber;
    private String checkInDate;
    private String checkOutDate;
    private Double totalAmount;

    // 기본 생성자
    public UpdateReservationRequestWebFluxJava() {}

    // 전체 생성자
    public UpdateReservationRequestWebFluxJava(String guestName, String roomNumber, 
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