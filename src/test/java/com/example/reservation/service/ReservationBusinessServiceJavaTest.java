package com.example.reservation.service;

import com.example.reservation.controller.CreateReservationRequest;
import com.example.reservation.controller.UpdateReservationRequest;
import com.example.reservation.domain.reservation_java.Reservation;
import com.example.reservation.domain.reservation_java.ReservationStatus;
import com.example.reservation.repository.ReservationRepositoryJava;
import com.example.reservation.repository.ReservationRepositoryReactiveJava;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Java Service 계층 단위 테스트
 * Mockito를 사용한 Java 스타일 모킹
 * 
 * Kotlin vs Java 테스트 비교:
 * 1. Mockito vs MockK - Java는 더 verbose한 설정 필요
 * 2. Optional 처리 - Java의 명시적 Optional 검증
 * 3. 예외 처리 - assertThrows vs assertThrows (유사하지만 타입 처리 차이)
 * 4. 빌더 패턴 활용 - Java에서 더 일반적
 */
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationBusinessServiceJavaTest {

    @Mock
    private ReservationRepositoryJava reservationRepository;

    @Mock
    private ReservationRepositoryReactiveJava reservationRepositoryReactive;

    @Mock
    private NotificationServiceJava notificationService;

    @Mock
    private AuditServiceJava auditService;

    @Mock
    private PaymentServiceJava paymentService;

    @Mock
    private RoomAvailabilityServiceJava roomAvailabilityService;

    private ReservationBusinessServiceJava reservationBusinessService;

    @BeforeEach
    void setUp() {
        reservationBusinessService = new ReservationBusinessServiceJava(
                reservationRepository,
                reservationRepositoryReactive,
                notificationService,
                auditService,
                paymentService,
                roomAvailabilityService
        );

        // 기본 Mock 동작 설정
        doNothing().when(notificationService).sendReservationConfirmation(any());
        doNothing().when(auditService).logReservationCreated(any());
        doNothing().when(auditService).logReservationUpdated(any());
        doNothing().when(auditService).logReservationCancelled(any(), any());
        doNothing().when(paymentService).processRefund(any(), any());
    }

    @Test
    void 유효한_예약_생성_요청시_예약이_성공적으로_생성된다() {
        // given
        CreateReservationRequest request = CreateReservationRequest.builder()
                .guestName("홍길동")
                .roomNumber("101")
                .checkInDate(LocalDate.now().plusDays(1).toString())
                .checkOutDate(LocalDate.now().plusDays(3).toString())
                .totalAmount(200000.0)
                .build();

        Reservation savedReservation = createMockReservation(1L, request);
        
        when(reservationRepository.existsOverlappingReservation(anyLong(), any(), any()))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class)))
                .thenReturn(savedReservation);

        // when
        Reservation result = reservationBusinessService.createReservation(request);

        // then
        assertNotNull(result);
        assertEquals("홍길동", getFirstName(result));
        assertEquals("101", result.getRoom().getRoomNumber());
        assertEquals(ReservationStatus.PENDING, result.getStatus());
        
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(notificationService, times(1)).sendReservationConfirmation(any());
        verify(auditService, times(1)).logReservationCreated(any());
    }

    @Test
    void 체크인_날짜가_과거인_경우_IllegalArgumentException이_발생한다() {
        // given
        CreateReservationRequest request = CreateReservationRequest.builder()
                .guestName("홍길동")
                .roomNumber("101")
                .checkInDate(LocalDate.now().minusDays(1).toString())
                .checkOutDate(LocalDate.now().plusDays(1).toString())
                .totalAmount(200000.0)
                .build();

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reservationBusinessService.createReservation(request)
        );
        
        assertTrue(exception.getMessage().contains("체크인 날짜는 오늘 이후여야 합니다"));
        
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void 체크아웃_날짜가_체크인_날짜보다_이른_경우_예외가_발생한다() {
        // given
        CreateReservationRequest request = CreateReservationRequest.builder()
                .guestName("홍길동")
                .roomNumber("101")
                .checkInDate(LocalDate.now().plusDays(3).toString())
                .checkOutDate(LocalDate.now().plusDays(1).toString())
                .totalAmount(200000.0)
                .build();

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reservationBusinessService.createReservation(request)
        );
        
        assertTrue(exception.getMessage().contains("체크아웃 날짜는 체크인 날짜 이후여야 합니다"));
    }

    @Test
    void 객실이_이미_예약된_경우_예외가_발생한다() {
        // given
        CreateReservationRequest request = CreateReservationRequest.builder()
                .guestName("홍길동")
                .roomNumber("101")
                .checkInDate(LocalDate.now().plusDays(1).toString())
                .checkOutDate(LocalDate.now().plusDays(3).toString())
                .totalAmount(200000.0)
                .build();

        when(reservationRepository.existsOverlappingReservation(anyLong(), any(), any()))
                .thenReturn(true);

        // when & then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reservationBusinessService.createReservation(request)
        );
        
        assertTrue(exception.getMessage().contains("객실이 이미 예약되어 있습니다"));
        
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void 존재하는_예약_ID로_조회시_Optional에_예약이_반환된다() {
        // given
        Long reservationId = 1L;
        Reservation mockReservation = createMockReservation(reservationId);
        
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(mockReservation));

        // when
        Optional<Reservation> result = reservationBusinessService.getReservationById(reservationId);

        // then
        assertTrue(result.isPresent());
        assertEquals(reservationId, result.get().getId());
        
        verify(reservationRepository, times(1)).findById(reservationId);
    }

    @Test
    void 존재하지_않는_예약_ID로_조회시_빈_Optional이_반환된다() {
        // given
        Long reservationId = 999L;
        
        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.empty());

        // when
        Optional<Reservation> result = reservationBusinessService.getReservationById(reservationId);

        // then
        assertFalse(result.isPresent());
    }

    @Test
    void 유효한_예약_수정_요청시_예약이_성공적으로_수정된다() {
        // given
        Long reservationId = 1L;
        Reservation existingReservation = createMockReservation(reservationId);
        UpdateReservationRequest updateRequest = UpdateReservationRequest.builder()
                .guestName("김철수")
                .roomNumber("102")
                .checkInDate(LocalDate.now().plusDays(2).toString())
                .checkOutDate(LocalDate.now().plusDays(4).toString())
                .totalAmount(250000.0)
                .build();

        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(existingReservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Optional<Reservation> result = reservationBusinessService.updateReservation(reservationId, updateRequest);

        // then
        assertTrue(result.isPresent());
        assertEquals(LocalDate.now().plusDays(2), result.get().getCheckInDate());
        assertEquals(BigDecimal.valueOf(250000.0), result.get().getTotalAmount());
        
        verify(reservationRepository, times(1)).save(any(Reservation.class));
        verify(auditService, times(1)).logReservationUpdated(any());
    }

    @Test
    void 취소된_예약을_수정하려고_하면_예외가_발생한다() {
        // given
        Long reservationId = 1L;
        Reservation cancelledReservation = createMockReservation(reservationId);
        // Mocked 객체이므로 상태 설정을 시뮬레이션
        when(cancelledReservation.getStatus()).thenReturn(ReservationStatus.CANCELLED);
        
        UpdateReservationRequest updateRequest = UpdateReservationRequest.builder()
                .totalAmount(300000.0)
                .build();

        when(reservationRepository.findById(reservationId))
                .thenReturn(Optional.of(cancelledReservation));

        // when & then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reservationBusinessService.updateReservation(reservationId, updateRequest)
        );
        
        assertTrue(exception.getMessage().contains("취소된 예약은 수정할 수 없습니다"));
        
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void 페이징된_예약_목록_조회가_정상_동작한다() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        List<Reservation> mockReservations = List.of(
                createMockReservation(1L),
                createMockReservation(2L),
                createMockReservation(3L)
        );
        PageImpl<Reservation> page = new PageImpl<>(mockReservations, pageable, 3);

        when(reservationRepository.findAll(pageable)).thenReturn(page);

        // when
        var result = reservationBusinessService.getAllReservations(pageable);

        // then
        assertEquals(3, result.getContent().size());
        assertEquals(3, result.getTotalElements());
        
        verify(reservationRepository, times(1)).findAll(pageable);
    }

    @Test
    void 데이터베이스_오류_발생시_재시도_로직이_동작한다() {
        // given
        CreateReservationRequest request = CreateReservationRequest.builder()
                .guestName("홍길동")
                .roomNumber("101")
                .checkInDate(LocalDate.now().plusDays(1).toString())
                .checkOutDate(LocalDate.now().plusDays(3).toString())
                .totalAmount(200000.0)
                .build();

        when(reservationRepository.existsOverlappingReservation(anyLong(), any(), any()))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class)))
                .thenThrow(new DataAccessException("DB 연결 오류") {})
                .thenThrow(new DataAccessException("DB 연결 오류") {})
                .thenReturn(createMockReservation(1L, request));

        // when
        Reservation result = reservationBusinessService.createReservation(request);

        // then
        assertNotNull(result);
        
        // 3번 시도 (처음 2번 실패, 3번째 성공)
        verify(reservationRepository, times(3)).save(any(Reservation.class));
    }

    // === 테스트 헬퍼 메서드들 ===

    /**
     * Mock Reservation 생성 헬퍼
     * Java의 경우 Mockito의 mock() 메서드 사용
     */
    private Reservation createMockReservation(Long id, CreateReservationRequest request) {
        Reservation mockReservation = mock(Reservation.class);
        
        when(mockReservation.getId()).thenReturn(id);
        when(mockReservation.getConfirmationNumber()).thenReturn("CONF-" + id);
        when(mockReservation.getStatus()).thenReturn(ReservationStatus.PENDING);
        when(mockReservation.getCheckInDate()).thenReturn(
                request != null ? LocalDate.parse(request.getCheckInDate()) : LocalDate.now().plusDays(1)
        );
        when(mockReservation.getCheckOutDate()).thenReturn(
                request != null ? LocalDate.parse(request.getCheckOutDate()) : LocalDate.now().plusDays(3)
        );
        when(mockReservation.getTotalAmount()).thenReturn(
                request != null ? BigDecimal.valueOf(request.getTotalAmount()) : BigDecimal.valueOf(200000)
        );

        // Guest Mock
        var mockGuest = mock(com.example.reservation.domain.guest.Guest.class);
        String firstName = request != null ? request.getGuestName().split(" ")[0] : "테스트";
        String lastName = request != null && request.getGuestName().split(" ").length > 1 
                ? request.getGuestName().split(" ")[1] : "고객";
        
        when(mockGuest.getFirstName()).thenReturn(firstName);
        when(mockGuest.getLastName()).thenReturn(lastName);
        when(mockGuest.getEmail()).thenReturn("test@example.com");
        when(mockReservation.getGuest()).thenReturn(mockGuest);

        // Room Mock
        var mockRoom = mock(com.example.reservation.domain.room.Room.class);
        when(mockRoom.getRoomNumber()).thenReturn(request != null ? request.getRoomNumber() : "101");
        when(mockRoom.getId()).thenReturn(1L);
        when(mockReservation.getRoom()).thenReturn(mockRoom);

        return mockReservation;
    }

    private Reservation createMockReservation(Long id) {
        return createMockReservation(id, null);
    }

    /**
     * Guest firstName 추출 헬퍼 메서드
     * Java의 null safety 처리 방식 보여줌
     */
    private String getFirstName(Reservation reservation) {
        return Optional.ofNullable(reservation)
                .map(Reservation::getGuest)
                .map(com.example.reservation.domain.guest.Guest::getFirstName)
                .orElse("Unknown");
    }
}