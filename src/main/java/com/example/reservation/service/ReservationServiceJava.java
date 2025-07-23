package com.example.reservation.controller;

// 같은 패키지에 두어서 package-private 클래스에 접근 가능
import com.example.reservation.domain.reservation.Reservation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ReservationServiceJava {
    
    private final com.example.reservation.service.ReservationService kotlinService;
    
    public ReservationServiceJava(com.example.reservation.service.ReservationService kotlinService) {
        this.kotlinService = kotlinService;
    }
    
    public List<Reservation> findAll() {
        return kotlinService.findAll();
    }
    
    public Reservation findById(UUID id) {
        return kotlinService.findById(id);
    }
    
    public boolean delete(UUID id) {
        return kotlinService.delete(id);
    }
    
    // Java MVC용 메서드들
    public Reservation createFromJavaRequest(CreateReservationRequestJava request) {
        return kotlinService.create(new CreateReservationRequest(
            request.getGuestName(),
            request.getRoomNumber(), 
            request.getCheckInDate(),
            request.getCheckOutDate(),
            request.getTotalAmount()
        ));
    }
    
    public Reservation updateFromJavaRequest(UUID id, UpdateReservationRequestJava request) {
        return kotlinService.update(id, new UpdateReservationRequest(
            request.getGuestName(),
            request.getRoomNumber(),
            request.getCheckInDate(), 
            request.getCheckOutDate(),
            request.getTotalAmount()
        ));
    }
    
    // Java WebFlux용 메서드들
    public Reservation createFromWebFluxJavaRequest(CreateReservationRequestWebFluxJava request) {
        return kotlinService.create(new CreateReservationRequest(
            request.getGuestName(),
            request.getRoomNumber(),
            request.getCheckInDate(),
            request.getCheckOutDate(), 
            request.getTotalAmount()
        ));
    }
    
    public Reservation updateFromWebFluxJavaRequest(UUID id, UpdateReservationRequestWebFluxJava request) {
        return kotlinService.update(id, new UpdateReservationRequest(
            request.getGuestName(),
            request.getRoomNumber(),
            request.getCheckInDate(),
            request.getCheckOutDate(),
            request.getTotalAmount()
        ));
    }
}