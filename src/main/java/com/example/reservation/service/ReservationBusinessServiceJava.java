package com.example.reservation.service;

import com.example.reservation.controller.CreateReservationRequest;
import com.example.reservation.controller.UpdateReservationRequest;
import com.example.reservation.domain.guest.Address;
import com.example.reservation.domain.guest.Guest;
import com.example.reservation.domain.reservation_java.Reservation;
import com.example.reservation.domain.reservation_java.ReservationStatus;
import com.example.reservation.domain.reservation_java.PaymentStatus;
import com.example.reservation.domain.reservation_java.ReservationSource;
import com.example.reservation.domain.reservation_java.ReservationGuestDetails;
import com.example.reservation.domain.room.Property;
import com.example.reservation.domain.room.PropertyType;
import com.example.reservation.domain.room.PropertyCategory;
import com.example.reservation.domain.room.Room;
import com.example.reservation.domain.room.RoomType;
import com.example.reservation.domain.room.BedType;
import com.example.reservation.repository.ReservationRepositoryJava;
import com.example.reservation.repository.ReservationRepositoryReactiveJava;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Java 프로덕션급 예약 비즈니스 서비스
 * 
 * Kotlin vs Java 비교 포인트:
 * 1. 명시적 타입 선언 vs 타입 추론
 * 2. Optional 사용 vs nullable 타입
 * 3. 빌더 패턴 vs 네임드 파라미터
 * 4. Stream API vs 컬렉션 함수
 * 5. 예외 처리 패턴 차이
 * 6. 불변성 처리 방식
 * 
 * 주요 특징:
 * - 트랜잭션 관리 및 데이터 일관성 보장
 * - 캐싱 전략으로 성능 최적화  
 * - 재시도 로직으로 안정성 향상
 * - 비동기 후속 처리로 응답성 개선
 * - 포괄적인 비즈니스 규칙 검증
 */
@Service
@Transactional
public class ReservationBusinessServiceJava {

    private final ReservationRepositoryJava reservationRepository;
    private final ReservationRepositoryReactiveJava reservationRepositoryReactive;
    private final NotificationServiceJava notificationService;
    private final AuditServiceJava auditService;
    private final PaymentServiceJava paymentService;
    private final RoomAvailabilityServiceJava roomAvailabilityService;

    // Java의 생성자 주입 - Kotlin보다 verbose하지만 명시적
    public ReservationBusinessServiceJava(
            ReservationRepositoryJava reservationRepository,
            ReservationRepositoryReactiveJava reservationRepositoryReactive,
            NotificationServiceJava notificationService,
            AuditServiceJava auditService,
            PaymentServiceJava paymentService,
            RoomAvailabilityServiceJava roomAvailabilityService) {
        this.reservationRepository = reservationRepository;
        this.reservationRepositoryReactive = reservationRepositoryReactive;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.paymentService = paymentService;
        this.roomAvailabilityService = roomAvailabilityService;
    }

    /**
     * 예약 생성 - 포괄적인 비즈니스 로직
     * Java의 경우 더 많은 타입 선언과 예외 처리 필요
     */
    @Retryable(value = {DataAccessException.class}, maxAttempts = 3)
    public Reservation createReservation(CreateReservationRequest request) {
        // 1. 비즈니스 규칙 검증
        validateReservationRequest(request);
        
        // 2. 객실 가용성 확인
        checkRoomAvailability(request.getRoomNumber(), request.getCheckInDate(), request.getCheckOutDate());
        
        // 3. 고객 정보 생성 또는 조회
        Guest guest = createOrUpdateGuest(request);
        
        // 4. 객실 정보 생성 (실제로는 DB에서 조회)
        Room room = createTempRoom(request);
        
        // 5. 예약 엔티티 생성 - Java는 빌더 패턴이나 생성자 사용
        Reservation reservation = Reservation.builder()
                .id(0L) // JPA가 자동 할당
                .confirmationNumber(generateConfirmationNumber())
                .guest(guest)
                .room(room)
                .checkInDate(LocalDate.parse(request.getCheckInDate()))
                .checkOutDate(LocalDate.parse(request.getCheckOutDate()))
                .numberOfGuests(1)
                .numberOfAdults(1)
                .numberOfChildren(0)
                .totalAmount(BigDecimal.valueOf(request.getTotalAmount()))
                .roomRate(BigDecimal.valueOf(request.getTotalAmount()))
                .status(ReservationStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .guestDetails(createGuestDetails(request))
                .source(ReservationSource.DIRECT)
                .createdAt(LocalDateTime.now())
                .lastModifiedAt(LocalDateTime.now())
                .build();
        
        // 6. 데이터베이스에 저장
        Reservation savedReservation = reservationRepository.save(reservation);
        
        // 7. 비동기 후속 처리
        processReservationCreatedAsync(savedReservation);
        
        return savedReservation;
    }

    /**
     * 예약 조회 - 캐싱 적용
     * Java의 Optional 사용 vs Kotlin의 nullable 타입
     */
    @Cacheable(value = "reservations", key = "#id")
    @Transactional(readOnly = true)
    public Optional<Reservation> getReservationById(Long id) {
        return reservationRepository.findById(id);
    }

    /**
     * 확인번호로 예약 조회 - 캐싱 적용
     * Java의 경우 Optional 체이닝 방식
     */
    @Cacheable(value = "reservations-by-confirmation", key = "#confirmationNumber")
    @Transactional(readOnly = true)
    public Optional<Reservation> getReservationByConfirmationNumber(String confirmationNumber) {
        return reservationRepository.findByConfirmationNumber(confirmationNumber);
    }

    /**
     * 모든 예약 조회 - 페이징 지원
     * Java Generic 타입 명시 필요
     */
    @Transactional(readOnly = true)
    public Page<Reservation> getAllReservations(Pageable pageable) {
        return reservationRepository.findAll(pageable);
    }

    /**
     * 예약 수정 - 캐시 무효화
     * Java의 Optional 체이닝과 예외 처리
     */
    @CacheEvict(value = "reservations", key = "#id")
    public Optional<Reservation> updateReservation(Long id, UpdateReservationRequest request) {
        Optional<Reservation> existingReservationOpt = reservationRepository.findById(id);
        
        if (existingReservationOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Reservation existingReservation = existingReservationOpt.get();
        
        // 비즈니스 규칙 검증
        validateReservationUpdate(existingReservation, request);
        
        // Java 빌더 패턴을 사용한 객체 수정
        Reservation.ReservationBuilder builder = existingReservation.toBuilder()
                .lastModifiedAt(LocalDateTime.now());
        
        // Optional 기반 조건부 업데이트
        Optional.ofNullable(request.getCheckInDate())
                .ifPresent(checkIn -> builder.checkInDate(LocalDate.parse(checkIn)));
        
        Optional.ofNullable(request.getCheckOutDate())
                .ifPresent(checkOut -> builder.checkOutDate(LocalDate.parse(checkOut)));
        
        Optional.ofNullable(request.getTotalAmount())
                .ifPresent(amount -> {
                    BigDecimal amountDecimal = BigDecimal.valueOf(amount);
                    builder.totalAmount(amountDecimal).roomRate(amountDecimal);
                });
        
        Reservation updatedReservation = builder.build();
        Reservation savedReservation = reservationRepository.save(updatedReservation);
        
        // 비동기 후속 처리
        processReservationUpdatedAsync(savedReservation);
        
        return Optional.of(savedReservation);
    }

    /**
     * 예약 취소 - 복합 캐시 무효화
     * Java의 더 verbose한 캐시 설정
     */
    @Caching(evict = {
        @CacheEvict(value = "reservations", key = "#id"),
        @CacheEvict(value = "reservations-by-confirmation", key = "#result.map(r -> r.getConfirmationNumber()).orElse('')"),
        @CacheEvict(value = "guest-reservations", allEntries = true)
    })
    public Optional<Reservation> cancelReservation(Long id, String reason) {
        Optional<Reservation> reservationOpt = reservationRepository.findById(id);
        
        if (reservationOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Reservation reservation = reservationOpt.get();
        
        // 취소 가능 여부 검증
        validateCancellation(reservation);
        
        // 환불 금액 계산
        BigDecimal refundAmount = calculateRefundAmount(reservation);
        
        // 예약 상태 변경 - Java 빌더 패턴
        Reservation cancelledReservation = reservation.toBuilder()
                .status(ReservationStatus.CANCELLED)
                .lastModifiedAt(LocalDateTime.now())
                .build();
        
        Reservation savedReservation = reservationRepository.save(cancelledReservation);
        
        // 비동기 후속 처리
        processCancellationAsync(savedReservation, refundAmount, reason);
        
        return Optional.of(savedReservation);
    }

    /**
     * 예약 삭제
     * Java의 명시적 boolean 반환
     */
    @CacheEvict(value = "reservations", key = "#id")
    public boolean deleteReservation(Long id) {
        if (reservationRepository.existsById(id)) {
            reservationRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // === 리액티브 메서드들 ===

    /**
     * 실시간 예약 스트림
     * Java의 명시적 제네릭 타입
     */
    public Flux<Reservation> getReservationStream() {
        return reservationRepositoryReactive.findAllByOrderByCheckInDateAsc();
    }

    /**
     * 고객별 예약 이력 스트림
     */
    public Flux<Reservation> getGuestReservationStream(String email) {
        return reservationRepositoryReactive.findByGuestEmail(email);
    }

    /**
     * 오늘 체크인 스트림
     */
    public Flux<Reservation> getTodayCheckInsStream() {
        return reservationRepositoryReactive.findTodayCheckInsStream();
    }

    // === 비즈니스 통계 메서드들 ===

    /**
     * 예약 통계 계산
     * Java의 명시적 객체 생성과 계산
     */
    @Transactional(readOnly = true)
    public ReservationStats calculateReservationStats(LocalDate startDate, LocalDate endDate) {
        BigDecimal totalRevenue = reservationRepository.calculateTotalRevenue(startDate, endDate);
        long reservationCount = reservationRepository.countByCheckInDateBetween(startDate, endDate);
        BigDecimal averageRate = reservationCount > 0 
            ? totalRevenue.divide(BigDecimal.valueOf(reservationCount)) 
            : BigDecimal.ZERO;
        
        return new ReservationStats(
            reservationCount,
            totalRevenue,
            averageRate,
            startDate + " to " + endDate
        );
    }

    /**
     * 실시간 점유율 계산
     * Java의 명시적 타입 변환과 계산
     */
    public Mono<Double> getCurrentOccupancyRate() {
        return reservationRepositoryReactive.countCurrentOccupancy()
                .map(activeReservations -> {
                    double totalRooms = 100.0; // 전체 객실 수
                    return (activeReservations.doubleValue() / totalRooms) * 100.0;
                });
    }

    // === Private 헬퍼 메서드들 ===

    private void validateReservationRequest(CreateReservationRequest request) {
        LocalDate checkIn = LocalDate.parse(request.getCheckInDate());
        LocalDate checkOut = LocalDate.parse(request.getCheckOutDate());
        
        if (!checkIn.isAfter(LocalDate.now().minusDays(1))) {
            throw new IllegalArgumentException("체크인 날짜는 오늘 이후여야 합니다");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("체크아웃 날짜는 체크인 날짜 이후여야 합니다");
        }
        if (request.getTotalAmount() <= 0) {
            throw new IllegalArgumentException("예약 금액은 0보다 커야 합니다");
        }
    }

    private void checkRoomAvailability(String roomNumber, String checkIn, String checkOut) {
        LocalDate checkInDate = LocalDate.parse(checkIn);
        LocalDate checkOutDate = LocalDate.parse(checkOut);
        
        // 임시 로직: 객실 ID를 1로 가정
        boolean hasConflict = reservationRepository.existsOverlappingReservation(1L, checkInDate, checkOutDate);
        
        if (hasConflict) {
            throw new IllegalStateException("선택한 날짜에 객실이 이미 예약되어 있습니다");
        }
    }

    private void validateReservationUpdate(Reservation reservation, UpdateReservationRequest request) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("취소된 예약은 수정할 수 없습니다");
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("완료된 예약은 수정할 수 없습니다");
        }
    }

    private void validateCancellation(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다");
        }
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("완료된 예약은 취소할 수 없습니다");
        }
    }

    private BigDecimal calculateRefundAmount(Reservation reservation) {
        long daysUntilCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), reservation.getCheckInDate());
        
        if (daysUntilCheckIn >= 7) {
            return reservation.getTotalAmount(); // 100% 환불
        } else if (daysUntilCheckIn >= 3) {
            return reservation.getTotalAmount().multiply(new BigDecimal("0.5")); // 50% 환불
        } else {
            return BigDecimal.ZERO; // 환불 불가
        }
    }

    private void processReservationCreatedAsync(Reservation reservation) {
        CompletableFuture.runAsync(() -> {
            try {
                notificationService.sendReservationConfirmation(reservation);
                auditService.logReservationCreated(reservation);
            } catch (Exception e) {
                // 로깅만 하고 예외를 전파하지 않음
                System.out.println("비동기 처리 중 오류 발생: " + e.getMessage());
            }
        });
    }

    private void processReservationUpdatedAsync(Reservation reservation) {
        CompletableFuture.runAsync(() -> {
            try {
                auditService.logReservationUpdated(reservation);
            } catch (Exception e) {
                System.out.println("예약 수정 감사 로그 실패: " + e.getMessage());
            }
        });
    }

    private void processCancellationAsync(Reservation reservation, BigDecimal refundAmount, String reason) {
        CompletableFuture.runAsync(() -> {
            try {
                if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    paymentService.processRefund(reservation, refundAmount);
                }
                notificationService.sendCancellationNotification(reservation, reason);
                auditService.logReservationCancelled(reservation, reason);
            } catch (Exception e) {
                System.out.println("취소 처리 중 오류 발생: " + e.getMessage());
            }
        });
    }

    private String generateConfirmationNumber() {
        return "CONF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Guest createOrUpdateGuest(CreateReservationRequest request) {
        return Guest.builder()
                .firstName(request.getGuestName().split(" ")[0])
                .lastName(request.getGuestName().split(" ").length > 1 ? 
                         request.getGuestName().split(" ")[1] : "")
                .email("guest@example.com")
                .build();
    }

    private Room createTempRoom(CreateReservationRequest request) {
        return Room.builder()
                .property(createTempProperty())
                .roomNumber(request.getRoomNumber())
                .name("Standard Room")
                .type(RoomType.STANDARD)
                .bedType(BedType.QUEEN)
                .maxOccupancy(2)
                .standardOccupancy(2)
                .baseRate(BigDecimal.valueOf(request.getTotalAmount()))
                .size(25.0)
                .floor(1)
                .build();
    }

    private Property createTempProperty() {
        return Property.builder()
                .name("Sample Hotel")
                .type(PropertyType.HOTEL)
                .category(PropertyCategory.BUSINESS)
                .starRating(4)
                .address(createTempAddress())
                .build();
    }

    private Address createTempAddress() {
        return Address.builder()
                .street("123 Main St")
                .city("City")
                .postalCode("12345")
                .countryCode("US")
                .build();
    }

    private ReservationGuestDetails createGuestDetails(CreateReservationRequest request) {
        return ReservationGuestDetails.builder()
                .primaryGuestFirstName(request.getGuestName().split(" ")[0])
                .primaryGuestLastName(request.getGuestName().split(" ").length > 1 ? 
                                    request.getGuestName().split(" ")[1] : "")
                .primaryGuestEmail("guest@example.com")
                .build();
    }

    // === 내부 클래스 및 인터페이스 ===

    /**
     * Java Record를 사용한 통계 데이터 클래스
     * Kotlin data class와 유사하지만 더 제한적
     */
    public record ReservationStats(
            Long totalReservations,
            BigDecimal totalRevenue,
            BigDecimal averageRate,
            String period
    ) {}
}

// === 의존성 서비스 인터페이스들 (Java 버전) ===

interface NotificationServiceJava {
    void sendReservationConfirmation(Reservation reservation);
    void sendCancellationNotification(Reservation reservation, String reason);
}

interface AuditServiceJava {
    void logReservationCreated(Reservation reservation);
    void logReservationUpdated(Reservation reservation);
    void logReservationCancelled(Reservation reservation, String reason);
}

interface PaymentServiceJava {
    void processRefund(Reservation reservation, BigDecimal amount);
}

interface RoomAvailabilityServiceJava {
    boolean checkAvailability(Long roomId, LocalDate checkIn, LocalDate checkOut);
}