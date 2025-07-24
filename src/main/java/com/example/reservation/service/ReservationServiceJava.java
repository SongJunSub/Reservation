package com.example.reservation.service;

import com.example.reservation.controller.CreateReservationRequest;
import com.example.reservation.controller.UpdateReservationRequest;
import com.example.reservation.domain.reservation_java.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Java 예약 서비스 파사드
 * 컨트롤러와 비즈니스 서비스 사이의 인터페이스 역할
 * 
 * Kotlin과의 주요 차이점:
 * 1. Optional 사용으로 null safety 처리
 * 2. 명시적 타입 선언
 * 3. 메서드 오버로딩 패턴
 * 4. 빌더 패턴 활용
 */
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