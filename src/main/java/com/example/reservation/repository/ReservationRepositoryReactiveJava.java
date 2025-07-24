package com.example.reservation.repository;

import com.example.reservation.domain.reservation_java.Reservation;
import com.example.reservation.domain.reservation_java.ReservationStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Java R2DBC 기반 리액티브 예약 Repository
 * 실시간 조회와 고성능 처리에 최적화
 * 
 * Kotlin vs Java 리액티브 프로그래밍 비교:
 * 1. Kotlin: 코루틴과 suspend 함수 사용 가능
 * 2. Java: Reactor의 Mono/Flux 타입 명시적 사용
 * 3. Kotlin: 더 간결한 람다 문법
 * 4. Java: 더 명시적인 타입 선언과 제네릭
 */
@Repository
public interface ReservationRepositoryReactiveJava extends R2dbcRepository<Reservation, Long> {
    
    /**
     * 확인 번호로 예약 조회 (리액티브)
     * Kotlin: fun findByConfirmationNumber(confirmationNumber: String): Mono<Reservation>
     * Java: Mono<Reservation> findByConfirmationNumber(String confirmationNumber)
     */
    Mono<Reservation> findByConfirmationNumber(String confirmationNumber);
    
    /**
     * 고객 이메일로 예약 조회 (리액티브 스트림)
     * 리액티브 스트림은 백프레셔 지원으로 대량 데이터 처리에 적합
     */
    Flux<Reservation> findByGuestEmail(String email);
    
    /**
     * 예약 상태와 체크인 날짜 범위로 조회
     * 리액티브 스트림의 장점: 데이터를 스트리밍으로 처리하여 메모리 효율적
     */
    Flux<Reservation> findByStatusAndCheckInDateBetween(
            ReservationStatus status,
            LocalDate startDate,
            LocalDate endDate
    );
    
    /**
     * 체크인 날짜로 정렬된 예약 스트림
     * 대용량 데이터도 스트리밍으로 처리 가능
     */
    Flux<Reservation> findAllByOrderByCheckInDateAsc();
    
    /**
     * 특정 상태의 예약 수 카운트 (리액티브)
     * Mono<Long>: 단일 값을 비동기로 반환
     */
    Mono<Long> countByStatus(ReservationStatus status);
    
    /**
     * 오늘 체크인 예정 예약 스트림 (실시간)
     * 실시간 대시보드나 알림 시스템에 활용
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE check_in_date = CURRENT_DATE " +
           "AND status = 'CONFIRMED' " +
           "ORDER BY created_at ASC")
    Flux<Reservation> findTodayCheckInsStream();
    
    /**
     * 활성 예약 실시간 모니터링 스트림
     * 호텔 운영진을 위한 실시간 현황 모니터링
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND check_out_date >= CURRENT_DATE " +
           "ORDER BY check_in_date ASC")
    Flux<Reservation> findActiveReservationsStream();
    
    /**
     * 특정 객실의 예약 현황 스트림
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE room_id = :roomId " +
           "AND status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND check_out_date >= CURRENT_DATE " +
           "ORDER BY check_in_date ASC")
    Flux<Reservation> findRoomReservationsStream(@Param("roomId") Long roomId);
    
    /**
     * 최근 예약 실시간 피드 (한정된 개수)
     * 실시간 예약 현황을 위한 스트림
     */
    @Query("SELECT * FROM reservations_java " +
           "ORDER BY created_at DESC " +
           "LIMIT :limit")
    Flux<Reservation> findRecentReservationsStream(@Param("limit") Integer limit);
    
    /**
     * 고객의 예약 이력 스트림
     * 고객 서비스를 위한 실시간 이력 조회
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE guest_email = :email " +
           "ORDER BY created_at DESC")  
    Flux<Reservation> findGuestReservationHistoryStream(@Param("email") String email);
    
    /**
     * 예약 상태 변경 모니터링을 위한 스트림
     * 감사나 알림 시스템을 위한 변경 사항 추적
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE last_modified_at >= :fromDate " +
           "ORDER BY last_modified_at ASC")
    Flux<Reservation> findReservationUpdatesStream(@Param("fromDate") LocalDate fromDate);
    
    /**
     * 체크인 예정 알림용 스트림 (X일 전)
     * 고객 알림 서비스를 위한 예약 정보
     */
    @Query("SELECT * FROM reservations_java " +
           "WHERE check_in_date = :targetDate " +
           "AND status = 'CONFIRMED' " +
           "ORDER BY created_at ASC")
    Flux<Reservation> findUpcomingCheckInsStream(@Param("targetDate") LocalDate targetDate);
    
    /**
     * 실시간 점유율 계산을 위한 활성 예약 수
     * 호텔 운영 효율성 모니터링
     */
    @Query("SELECT COUNT(*) FROM reservations_java " +
           "WHERE status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND check_in_date <= CURRENT_DATE " +
           "AND check_out_date > CURRENT_DATE")
    Mono<Long> countCurrentOccupancy();
    
    /**
     * 예약 가능 여부 확인 (비동기)
     * 실시간 예약 시스템을 위한 중복 확인
     */
    @Query("SELECT COUNT(*) FROM reservations_java " +
           "WHERE room_id = :roomId " +
           "AND status IN ('CONFIRMED', 'CHECKED_IN') " +
           "AND (" +
           "    (check_in_date <= :checkInDate AND check_out_date > :checkInDate) OR " +
           "    (check_in_date < :checkOutDate AND check_out_date >= :checkOutDate) OR " +
           "    (check_in_date >= :checkInDate AND check_out_date <= :checkOutDate)" +
           ")")
    Mono<Long> countOverlappingReservations(
            @Param("roomId") Long roomId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate
    );
    
    /**
     * 실시간 매출 계산 스트림
     * 재무 대시보드를 위한 실시간 매출 계산
     */
    @Query("SELECT COALESCE(SUM(total_amount), 0) " +
           "FROM reservations_java " +
           "WHERE status IN ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED') " +
           "AND check_in_date BETWEEN :startDate AND :endDate")
    Mono<BigDecimal> calculateRevenueStream(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    // Java 리액티브 프로그래밍 전용 편의 메서드들
    
    /**
     * 예약 존재 여부를 Mono<Boolean>으로 반환
     * Java의 경우 명시적인 변환 메서드가 유용
     */
    default Mono<Boolean> existsByConfirmationNumber(String confirmationNumber) {
        return findByConfirmationNumber(confirmationNumber)
                .hasElement(); // Mono를 Mono<Boolean>으로 변환
    }
    
    /**
     * 고객의 활성 예약만 필터링하여 반환
     * Java Stream API와 Reactor 조합 예시
     */
    default Flux<Reservation> findActiveReservationsForGuest(String email) {
        return findByGuestEmail(email)
                .filter(reservation -> 
                    reservation.getStatus() == ReservationStatus.CONFIRMED ||
                    reservation.getStatus() == ReservationStatus.CHECKED_IN
                );
    }
    
    /**
     * 예약 상태 변경을 실시간으로 추적하는 스트림
     * 비즈니스 이벤트 처리를 위한 리액티브 스트림
     */
    default Flux<Reservation> monitorReservationStatusChanges() {
        return findReservationUpdatesStream(LocalDate.now().minusDays(1))
                .distinctUntilChanged(Reservation::getStatus); // 상태 변경시만 emit
    }
    
    /**
     * 실시간 예약 통계를 위한 스트림 조합
     * 여러 스트림을 조합하여 복합 정보 제공
     */
    default Mono<ReservationStats> calculateRealTimeStats() {
        Mono<Long> totalReservations = count();
        Mono<Long> activeReservations = countCurrentOccupancy();
        Mono<Long> todayCheckIns = findTodayCheckInsStream().count();
        
        return Mono.zip(totalReservations, activeReservations, todayCheckIns)
                .map(tuple -> new ReservationStats(
                    tuple.getT1(), // total
                    tuple.getT2(), // active  
                    tuple.getT3()  // today check-ins
                ));
    }
    
    /**
     * 예약 통계를 위한 간단한 데이터 클래스
     * Java Record를 사용한 불변 데이터 구조
     */
    record ReservationStats(Long totalReservations, Long activeReservations, Long todayCheckIns) {}
}