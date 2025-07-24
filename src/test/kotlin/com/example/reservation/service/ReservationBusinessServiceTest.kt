package com.example.reservation.service

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.exception.ValidationException
import com.example.reservation.exception.ReservationNotFoundException
import com.example.reservation.exception.ReservationNotModifiableException
import com.example.reservation.repository.ReservationRepository
import com.example.reservation.repository.ReservationRepositoryReactive
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.dao.DataAccessException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Service 계층 단위 테스트
 * MockK를 사용한 Kotlin 스타일 모킹
 * 
 * 테스트 전략:
 * 1. MockK로 의존성 모킹
 * 2. 비즈니스 로직 검증 중심
 * 3. 예외 상황 처리 검증
 * 4. 캐싱 동작 검증 (스프링 부트 테스트 컨텍스트 필요시)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationBusinessServiceTest {

    private val reservationRepository: ReservationRepository = mockk()
    private val reservationRepositoryReactive: ReservationRepositoryReactive = mockk()
    private val notificationService: NotificationService = mockk()
    private val auditService: AuditService = mockk()
    private val paymentService: PaymentService = mockk()
    private val roomAvailabilityService: RoomAvailabilityService = mockk()

    private lateinit var reservationBusinessService: ReservationBusinessService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        
        reservationBusinessService = ReservationBusinessService(
            reservationRepository = reservationRepository,
            reservationRepositoryReactive = reservationRepositoryReactive,
            notificationService = notificationService,
            auditService = auditService,
            paymentService = paymentService,
            roomAvailabilityService = roomAvailabilityService
        )

        // 기본 Mock 동작 설정
        every { notificationService.sendReservationConfirmation(any()) } just runs
        every { auditService.logReservationCreated(any()) } just runs
        every { auditService.logReservationUpdated(any()) } just runs
        every { auditService.logReservationCancelled(any(), any()) } just runs
        every { paymentService.processRefund(any(), any()) } just runs
    }

    @Test
    fun `유효한 예약 생성 요청시 예약이 성공적으로 생성된다`() {
        // given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 200000.0
        )

        val savedReservation = createMockReservation(1L, request)
        
        every { reservationRepository.existsOverlappingReservation(any(), any(), any()) } returns false
        every { reservationRepository.save(any<Reservation>()) } returns savedReservation

        // when
        val result = reservationBusinessService.createReservation(request)

        // then
        assertNotNull(result)
        assertEquals("홍길동", result.guest.firstName)
        assertEquals("101", result.room.roomNumber)
        assertEquals(ReservationStatus.PENDING, result.status)
        
        verify(exactly = 1) { reservationRepository.save(any<Reservation>()) }
        verify(exactly = 1) { notificationService.sendReservationConfirmation(any()) }
        verify(exactly = 1) { auditService.logReservationCreated(any()) }
    }

    @Test
    fun `체크인 날짜가 과거인 경우 ValidationException이 발생한다`() {
        // given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101", 
            checkInDate = LocalDate.now().minusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(1).toString(),
            totalAmount = 200000.0
        )

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            reservationBusinessService.createReservation(request)
        }
        
        assertTrue(exception.message!!.contains("체크인 날짜는 오늘 이후여야 합니다"))
        
        verify(exactly = 0) { reservationRepository.save(any<Reservation>()) }
    }

    @Test
    fun `체크아웃 날짜가 체크인 날짜보다 이른 경우 예외가 발생한다`() {
        // given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(3).toString(),
            checkOutDate = LocalDate.now().plusDays(1).toString(),
            totalAmount = 200000.0
        )

        // when & then
        val exception = assertThrows<IllegalArgumentException> {
            reservationBusinessService.createReservation(request)
        }
        
        assertTrue(exception.message!!.contains("체크아웃 날짜는 체크인 날짜 이후여야 합니다"))
    }

    @Test
    fun `객실이 이미 예약된 경우 예외가 발생한다`() {
        // given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 200000.0
        )

        every { reservationRepository.existsOverlappingReservation(any(), any(), any()) } returns true

        // when & then
        val exception = assertThrows<IllegalStateException> {
            reservationBusinessService.createReservation(request)
        }
        
        assertTrue(exception.message!!.contains("객실이 이미 예약되어 있습니다"))
        
        verify(exactly = 0) { reservationRepository.save(any<Reservation>()) }
    }

    @Test
    fun `존재하는 예약 ID로 조회시 예약이 반환된다`() {
        // given
        val reservationId = 1L
        val mockReservation = createMockReservation(reservationId)
        
        every { reservationRepository.findById(reservationId) } returns Optional.of(mockReservation)

        // when
        val result = reservationBusinessService.getReservationById(reservationId)

        // then
        assertNotNull(result)
        assertEquals(reservationId, result.id)
        
        verify(exactly = 1) { reservationRepository.findById(reservationId) }
    }

    @Test
    fun `존재하지 않는 예약 ID로 조회시 null이 반환된다`() {
        // given
        val reservationId = 999L
        
        every { reservationRepository.findById(reservationId) } returns Optional.empty()

        // when
        val result = reservationBusinessService.getReservationById(reservationId)

        // then
        assertEquals(null, result)
    }

    @Test
    fun `유효한 예약 수정 요청시 예약이 성공적으로 수정된다`() {
        // given
        val reservationId = 1L
        val existingReservation = createMockReservation(reservationId)
        val updateRequest = UpdateReservationRequest(
            guestName = "김철수",
            roomNumber = "102",
            checkInDate = LocalDate.now().plusDays(2).toString(),
            checkOutDate = LocalDate.now().plusDays(4).toString(),
            totalAmount = 250000.0
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(existingReservation)
        every { reservationRepository.save(any<Reservation>()) } returnsArgument 0

        // when
        val result = reservationBusinessService.updateReservation(reservationId, updateRequest)

        // then
        assertNotNull(result)
        assertEquals(LocalDate.now().plusDays(2), result.checkInDate)
        assertEquals(BigDecimal.valueOf(250000.0), result.totalAmount)
        
        verify(exactly = 1) { reservationRepository.save(any<Reservation>()) }
        verify(exactly = 1) { auditService.logReservationUpdated(any()) }
    }

    @Test
    fun `취소된 예약을 수정하려고 하면 예외가 발생한다`() {
        // given
        val reservationId = 1L
        val cancelledReservation = createMockReservation(reservationId).copy(
            status = ReservationStatus.CANCELLED
        )
        val updateRequest = UpdateReservationRequest(totalAmount = 300000.0)

        every { reservationRepository.findById(reservationId) } returns Optional.of(cancelledReservation)

        // when & then
        val exception = assertThrows<IllegalStateException> {
            reservationBusinessService.updateReservation(reservationId, updateRequest)
        }
        
        assertTrue(exception.message!!.contains("취소된 예약은 수정할 수 없습니다"))
        
        verify(exactly = 0) { reservationRepository.save(any<Reservation>()) }
    }

    @Test
    fun `유효한 예약 취소 요청시 예약이 성공적으로 취소된다`() {
        // given
        val reservationId = 1L
        val existingReservation = createMockReservation(reservationId)
        val reason = "고객 사정으로 인한 취소"

        every { reservationRepository.findById(reservationId) } returns Optional.of(existingReservation)
        every { reservationRepository.save(any<Reservation>()) } returnsArgument 0

        // when
        val result = reservationBusinessService.cancelReservation(reservationId, reason)

        // then
        assertNotNull(result)
        assertEquals(ReservationStatus.CANCELLED, result.status)
        
        verify(exactly = 1) { reservationRepository.save(any<Reservation>()) }
        verify(exactly = 1) { paymentService.processRefund(any(), any()) }
        verify(exactly = 1) { auditService.logReservationCancelled(any(), reason) }
    }

    @Test
    fun `이미 취소된 예약을 다시 취소하려고 하면 예외가 발생한다`() {
        // given
        val reservationId = 1L
        val cancelledReservation = createMockReservation(reservationId).copy(
            status = ReservationStatus.CANCELLED
        )

        every { reservationRepository.findById(reservationId) } returns Optional.of(cancelledReservation)

        // when & then
        val exception = assertThrows<IllegalStateException> {
            reservationBusinessService.cancelReservation(reservationId, "중복 취소")
        }
        
        assertTrue(exception.message!!.contains("이미 취소된 예약입니다"))
        
        verify(exactly = 0) { reservationRepository.save(any<Reservation>()) }
    }

    @Test
    fun `페이징된 예약 목록 조회가 정상 동작한다`() {
        // given
        val pageable = PageRequest.of(0, 10)
        val mockReservations = listOf(
            createMockReservation(1L),
            createMockReservation(2L),
            createMockReservation(3L)
        )
        val page = PageImpl(mockReservations, pageable, 3)

        every { reservationRepository.findAll(pageable) } returns page

        // when
        val result = reservationBusinessService.getAllReservations(pageable)

        // then
        assertEquals(3, result.content.size)
        assertEquals(3, result.totalElements)
        
        verify(exactly = 1) { reservationRepository.findAll(pageable) }
    }

    @Test
    fun `데이터베이스 오류 발생시 재시도 로직이 동작한다`() {
        // given
        val request = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 200000.0
        )

        every { reservationRepository.existsOverlappingReservation(any(), any(), any()) } returns false
        every { reservationRepository.save(any<Reservation>()) } throws DataAccessException("DB 연결 오류") andThenThrows DataAccessException("DB 연결 오류") andThen createMockReservation(1L, request)

        // when
        val result = reservationBusinessService.createReservation(request)

        // then
        assertNotNull(result)
        
        // 3번 시도 (처음 2번 실패, 3번째 성공)
        verify(exactly = 3) { reservationRepository.save(any<Reservation>()) }
    }

    // === 테스트 헬퍼 메서드들 ===

    private fun createMockReservation(id: Long, request: CreateReservationRequest? = null): Reservation {
        return mockk<Reservation> {
            every { this@mockk.id } returns id
            every { confirmationNumber } returns "CONF-$id"
            every { status } returns ReservationStatus.PENDING
            every { checkInDate } returns (request?.let { LocalDate.parse(it.checkInDate) } ?: LocalDate.now().plusDays(1))
            every { checkOutDate } returns (request?.let { LocalDate.parse(it.checkOutDate) } ?: LocalDate.now().plusDays(3))
            every { totalAmount } returns (request?.let { BigDecimal.valueOf(it.totalAmount) } ?: BigDecimal("200000"))
            every { guest } returns mockk {
                every { firstName } returns (request?.guestName?.split(" ")?.get(0) ?: "테스트")
                every { lastName } returns (request?.guestName?.split(" ")?.getOrNull(1) ?: "고객")
                every { email } returns "test@example.com"
            }
            every { room } returns mockk {
                every { roomNumber } returns (request?.roomNumber ?: "101")
                every { this@mockk.id } returns 1L
            }
            every { copy(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns this@mockk
        }
    }
}