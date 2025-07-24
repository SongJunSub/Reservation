package com.example.reservation.repository;

import com.example.reservation.domain.reservation_java.Reservation;
import com.example.reservation.domain.reservation_java.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Java JPA 기반 예약 Repository
 * 복잡한 쿼리와 배치 처리에 최적화
 * 
 * Kotlin과의 주요 차이점:
 * 1. 명시적 타입 선언 필요
 * 2. Optional 사용으로 null safety 처리
 * 3. 메서드명이 더 verbose
 * 4. 제네릭 타입 명시 필요
 */
@Repository
public interface ReservationRepositoryJava extends JpaRepository<Reservation, Long>, 
                                                  JpaSpecificationExecutor<Reservation> {
    
    /**
     * 확인 번호로 예약 조회
     * Kotlin: fun findByConfirmationNumber(confirmationNumber: String): Optional<Reservation>
     * Java: Optional<Reservation> findByConfirmationNumber(String confirmationNumber)
     */
    Optional<Reservation> findByConfirmationNumber(String confirmationNumber);
    
    /**
     * 고객 이메일과 예약 상태로 조회
     * Kotlin에서는 List<ReservationStatus> 타입 추론 가능
     * Java에서는 명시적 타입 선언 필요
     */
    List<Reservation> findByGuestEmailAndStatusIn(
            String email, 
            List<ReservationStatus> statuses
    );
    
    /**
     * 체크인 날짜 범위로 예약 조회
     * Java의 경우 매개변수명을 통한 의미 전달이 중요
     */
    List<Reservation> findByCheckInDateBetween(
            LocalDate startDate, 
            LocalDate endDate
    );
    
    /**
     * 체크아웃 날짜 범위로 예약 조회
     */
    List<Reservation> findByCheckOutDateBetween(
            LocalDate startDate,
            LocalDate endDate
    );
    
    /**
     * 오늘 체크인 예정인 확정된 예약 조회
     * JPQL 쿼리는 Kotlin과 동일하지만 Java의 멀티라인 문자열 처리 방식 다름
     */
    @Query("SELECT r FROM Reservation r " +
           "WHERE r.checkInDate = CURRENT_DATE " +
           "AND r.status = 'CONFIRMED' " +
           "ORDER BY r.createdAt ASC")
    List<Reservation> findTodayCheckIns();
    
    /**
     * 오늘 체크아웃 예정인 활성 예약 조회
     */
    @Query("SELECT r FROM Reservation r " +
           "WHERE r.checkOutDate = CURRENT_DATE " +
           "AND r.status IN ('CONFIRMED', 'CHECKED_IN') " +
           "ORDER BY r.createdAt ASC")
    List<Reservation> findTodayCheckOuts();
    
    /**
     * 특정 객실의 활성 예약 조회
     * Java는 @Param 애노테이션 필수
     */
    @Query("SELECT r FROM Reservation r " +
           "WHERE r.room.id = :roomId " +
           "AND r.status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND r.checkOutDate >= CURRENT_DATE " +
           "ORDER BY r.checkInDate ASC")
    List<Reservation> findActiveReservationsByRoomId(@Param("roomId") Long roomId);
    
    /**
     * 예약 상태별 통계 조회
     * Object[]를 사용한 프로젝션 - Kotlin과 동일한 방식
     */
    @Query("SELECT r.status, COUNT(r) " +
           "FROM Reservation r " +
           "WHERE r.createdAt >= :fromDate " +
           "GROUP BY r.status")
    List<Object[]> countReservationsByStatusSince(@Param("fromDate") LocalDate fromDate);
    
    /**
     * 고객의 예약 이력 조회 (페이징)
     * Spring Data의 Pageable 인터페이스 활용
     */
    Page<Reservation> findByGuestEmailOrderByCreatedAtDesc(
            String email, 
            Pageable pageable
    );
    
    /**
     * 총 매출 계산 (특정 기간)
     * BigDecimal 사용으로 정확한 금전 계산
     */
    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) " +
           "FROM Reservation r " +
           "WHERE r.status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED') " +
           "AND r.checkInDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalRevenue(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    /**
     * 취소된 예약 조회 (환불 처리용)
     */
    @Query("SELECT r FROM Reservation r " +
           "WHERE r.status = 'CANCELLED' " +
           "AND r.paymentStatus = 'PAID' " +
           "AND r.lastModifiedAt >= :fromDate")
    List<Reservation> findCancelledReservationsForRefund(@Param("fromDate") LocalDate fromDate);
    
    /**
     * 예약 존재 여부 확인 (객실, 날짜 겹침)
     * boolean 반환 타입 - Kotlin에서는 Boolean으로 자동 변환
     */
    @Query("SELECT COUNT(r) > 0 " +
           "FROM Reservation r " +
           "WHERE r.room.id = :roomId " +
           "AND r.status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND (" +
           "    (r.checkInDate <= :checkInDate AND r.checkOutDate > :checkInDate) OR " +
           "    (r.checkInDate < :checkOutDate AND r.checkOutDate >= :checkOutDate) OR " +
           "    (r.checkInDate >= :checkInDate AND r.checkOutDate <= :checkOutDate)" +
           ")")
    boolean existsOverlappingReservation(
            @Param("roomId") Long roomId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );
    
    // Java 전용 편의 메서드들
    
    /**
     * 예약 존재 여부를 Optional로 반환하는 Java 스타일 메서드
     * Kotlin에서는 nullable 타입으로 간단히 처리 가능
     */
    default Optional<Reservation> findReservationByConfirmationNumber(String confirmationNumber) {
        return findByConfirmationNumber(confirmationNumber);
    }
    
    /**
     * 예약 상태 확인을 위한 편의 메서드
     * Java의 경우 이런 헬퍼 메서드가 유용함
     */
    default boolean isReservationExists(String confirmationNumber) {
        return findByConfirmationNumber(confirmationNumber).isPresent();
    }
    
    /**
     * Stream API를 활용한 Java 스타일 데이터 처리 예시
     */
    default List<Reservation> findActiveReservationsForGuest(String email) {
        return findByGuestEmailAndStatusIn(
                email, 
                List.of(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)
        );
    }
}