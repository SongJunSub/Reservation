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
public class ReservationServiceJavaImpl {
    
    private final ReservationBusinessServiceJava businessService;
    
    // Java 생성자 주입
    public ReservationServiceJavaImpl(ReservationBusinessServiceJava businessService) {
        this.businessService = businessService;
    }
    
    /**
     * 페이징된 예약 목록 조회
     */
    public Page<Reservation> findAll(Pageable pageable) {
        return businessService.getAllReservations(pageable);
    }
    
    /**
     * 모든 예약 목록 조회 (하위 호환성)
     * Java의 경우 명시적 타입 변환 필요
     */
    public List<Reservation> findAll() {
        return businessService.getAllReservations(Pageable.unpaged()).getContent();
    }
    
    /**
     * ID로 예약 조회 (UUID -> Long 변환)
     * Java Optional 사용 패턴
     */
    public Optional<Reservation> findById(UUID id) {
        // UUID를 Long ID로 변환하는 로직
        Long longId = (long) id.hashCode();
        return businessService.getReservationById(longId);
    }
    
    /**
     * Long ID로 예약 조회
     */
    public Optional<Reservation> findById(Long id) {
        return businessService.getReservationById(id);
    }
    
    /**
     * 확인번호로 예약 조회
     */
    public Optional<Reservation> findByConfirmationNumber(String confirmationNumber) {
        return businessService.getReservationByConfirmationNumber(confirmationNumber);
    }
    
    /**
     * 예약 생성
     * Java의 경우 예외 처리가 더 명시적
     */
    public Reservation create(CreateReservationRequest request) {
        try {
            return businessService.createReservation(request);
        } catch (Exception e) {
            // Java의 명시적 예외 처리
            throw new ReservationCreationException("예약 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 예약 수정 (UUID 버전)
     */
    public Optional<Reservation> update(UUID id, UpdateReservationRequest request) {
        Long longId = (long) id.hashCode();
        return businessService.updateReservation(longId, request);
    }
    
    /**
     * 예약 수정 (Long 버전)
     */
    public Optional<Reservation> update(Long id, UpdateReservationRequest request) {
        return businessService.updateReservation(id, request);
    }
    
    /**
     * 예약 삭제 (UUID 버전)
     */
    public boolean delete(UUID id) {
        Long longId = (long) id.hashCode();
        return businessService.deleteReservation(longId);
    }
    
    /**
     * 예약 삭제 (Long 버전)
     */
    public boolean delete(Long id) {
        return businessService.deleteReservation(id);
    }
    
    /**
     * 예약 취소
     * Java의 null 처리 방식
     */
    public Optional<Reservation> cancel(Long id, String reason) {
        String actualReason = (reason != null && !reason.trim().isEmpty()) ? reason : "고객 요청";
        return businessService.cancelReservation(id, actualReason);
    }
    
    // === 리액티브 스트림 메서드들 ===
    
    /**
     * 실시간 예약 스트림
     * Java의 명시적 제네릭 타입
     */
    public Flux<Reservation> getReservationStream() {
        return businessService.getReservationStream();
    }
    
    /**
     * 고객별 예약 이력 스트림
     */
    public Flux<Reservation> getGuestReservationStream(String email) {
        return businessService.getGuestReservationStream(email);
    }
    
    /**
     * 오늘 체크인 스트림
     */
    public Flux<Reservation> getTodayCheckInsStream() {
        return businessService.getTodayCheckInsStream();
    }
    
    // === Java 전용 편의 메서드들 ===
    
    /**
     * 예약 존재 여부 확인
     * Java의 boolean 메서드 패턴
     */
    public boolean existsById(Long id) {
        return businessService.getReservationById(id).isPresent();
    }
    
    /**
     * 확인번호로 예약 존재 여부 확인
     */
    public boolean existsByConfirmationNumber(String confirmationNumber) {
        return businessService.getReservationByConfirmationNumber(confirmationNumber).isPresent();
    }
    
    /**
     * 안전한 예약 조회 (예외 대신 Optional 반환)
     */
    public Optional<Reservation> findByIdSafely(Long id) {
        try {
            return businessService.getReservationById(id);
        } catch (Exception e) {
            // 예외를 로깅하고 Empty Optional 반환
            System.err.println("예약 조회 중 오류 발생: " + e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * 예약 수 계산
     */
    public long count() {
        return findAll().size(); // 실제로는 repository.count() 사용
    }
    
    // === Java 전용 예외 클래스 ===
    
    /**
     * 예약 생성 실패 예외
     */
    public static class ReservationCreationException extends RuntimeException {
        public ReservationCreationException(String message) {
            super(message);
        }
        
        public ReservationCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 예약 수정 실패 예외
     */
    public static class ReservationUpdateException extends RuntimeException {
        public ReservationUpdateException(String message) {
            super(message);
        }
        
        public ReservationUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}