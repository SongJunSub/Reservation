package com.example.reservation.repository

import com.example.reservation.domain.guest.Guest
import com.example.reservation.domain.guest.Address
import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.domain.reservation.PaymentStatus
import com.example.reservation.domain.reservation.ReservationSource
import com.example.reservation.domain.reservation.ReservationGuestDetails
import com.example.reservation.domain.room.Room
import com.example.reservation.domain.room.Property
import com.example.reservation.domain.room.PropertyType
import com.example.reservation.domain.room.PropertyCategory
import com.example.reservation.domain.room.RoomType
import com.example.reservation.domain.room.BedType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Repository 계층 통합 테스트
 * Testcontainers를 사용한 실제 데이터베이스 테스트
 * 
 * 테스트 전략:
 * 1. Testcontainers로 실제 PostgreSQL 컨테이너 사용
 * 2. @DataJpaTest로 JPA 관련 빈만 로드하여 빠른 실행
 * 3. TestEntityManager로 테스트 데이터 준비
 * 4. 복잡한 쿼리 메서드들의 정확성 검증
 */
@DataJpaTest
@ActiveProfiles("test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationRepositoryTest {

    @Container
    companion object {
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("reservation_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
    }

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    private lateinit var testGuest: Guest
    private lateinit var testRoom: Room
    private lateinit var testReservation: Reservation

    @BeforeEach
    fun setUp() {
        // 테스트 데이터 준비
        testGuest = createTestGuest()
        testRoom = createTestRoom()
        testReservation = createTestReservation()
        
        // 엔티티 영속화
        testEntityManager.persistAndFlush(testGuest)
        testEntityManager.persistAndFlush(testRoom.property)
        testEntityManager.persistAndFlush(testRoom)
        testEntityManager.persistAndFlush(testReservation)
        testEntityManager.clear()
    }

    @Test
    fun `확인번호로 예약 조회가 정상 동작한다`() {
        // when
        val foundReservation = reservationRepository.findByConfirmationNumber(testReservation.confirmationNumber)

        // then
        assertTrue(foundReservation.isPresent)
        assertEquals(testReservation.confirmationNumber, foundReservation.get().confirmationNumber)
        assertEquals(testReservation.guest.email, foundReservation.get().guest.email)
    }

    @Test
    fun `존재하지 않는 확인번호로 조회시 빈 Optional을 반환한다`() {
        // when
        val foundReservation = reservationRepository.findByConfirmationNumber("NONEXISTENT")

        // then
        assertFalse(foundReservation.isPresent)
    }

    @Test
    fun `고객 이메일과 상태로 예약 조회가 정상 동작한다`() {
        // when
        val reservations = reservationRepository.findByGuestEmailAndStatusIn(
            testGuest.email,
            listOf(ReservationStatus.CONFIRMED, ReservationStatus.PENDING)
        )

        // then
        assertEquals(1, reservations.size)
        assertEquals(testReservation.confirmationNumber, reservations[0].confirmationNumber)
    }

    @Test
    fun `체크인 날짜 범위로 예약 조회가 정상 동작한다`() {
        // given
        val startDate = testReservation.checkInDate.minusDays(1)
        val endDate = testReservation.checkInDate.plusDays(1)

        // when
        val reservations = reservationRepository.findByCheckInDateBetween(startDate, endDate)

        // then
        assertEquals(1, reservations.size)
        assertEquals(testReservation.confirmationNumber, reservations[0].confirmationNumber)
    }

    @Test
    fun `오늘 체크인 예정 예약 조회 쿼리가 정상 동작한다`() {
        // given - 오늘 체크인하는 예약 생성
        val todayReservation = createTestReservation().copy(
            confirmationNumber = "TODAY-CHECKIN",
            checkInDate = LocalDate.now(),
            status = ReservationStatus.CONFIRMED
        )
        testEntityManager.persistAndFlush(todayReservation)
        testEntityManager.clear()

        // when
        val todayCheckIns = reservationRepository.findTodayCheckIns()

        // then
        assertTrue(todayCheckIns.isNotEmpty())
        assertTrue(todayCheckIns.any { it.confirmationNumber == "TODAY-CHECKIN" })
        todayCheckIns.forEach { reservation ->
            assertEquals(LocalDate.now(), reservation.checkInDate)
            assertEquals(ReservationStatus.CONFIRMED, reservation.status)
        }
    }

    @Test
    fun `특정 객실의 활성 예약 조회가 정상 동작한다`() {
        // given - 활성 상태의 예약 생성
        val activeReservation = createTestReservation().copy(
            confirmationNumber = "ACTIVE-RESERVATION",
            status = ReservationStatus.CONFIRMED,
            checkOutDate = LocalDate.now().plusDays(2)
        )
        testEntityManager.persistAndFlush(activeReservation)
        testEntityManager.clear()

        // when
        val activeReservations = reservationRepository.findActiveReservationsByRoomId(testRoom.id)

        // then
        assertTrue(activeReservations.isNotEmpty())
        activeReservations.forEach { reservation ->
            assertTrue(
                reservation.status == ReservationStatus.CONFIRMED ||
                reservation.status == ReservationStatus.CHECKED_IN
            )
            assertTrue(reservation.checkOutDate >= LocalDate.now())
        }
    }

    @Test
    fun `예약 상태별 통계 조회가 정상 동작한다`() {
        // given - 다양한 상태의 예약들 생성
        val cancelledReservation = createTestReservation().copy(
            confirmationNumber = "CANCELLED-RES",
            status = ReservationStatus.CANCELLED
        )
        val completedReservation = createTestReservation().copy(
            confirmationNumber = "COMPLETED-RES", 
            status = ReservationStatus.COMPLETED
        )
        
        testEntityManager.persistAndFlush(cancelledReservation)
        testEntityManager.persistAndFlush(completedReservation)
        testEntityManager.clear()

        // when
        val fromDate = LocalDate.now().minusDays(1)
        val stats = reservationRepository.countReservationsByStatusSince(fromDate)

        // then
        assertNotNull(stats)
        assertTrue(stats.isNotEmpty())
        
        // 각 상태별로 최소 1개씩은 있어야 함
        val statusCounts = stats.associate { 
            it[0] as ReservationStatus to (it[1] as Long)
        }
        
        assertTrue(statusCounts.containsKey(ReservationStatus.PENDING))
        assertTrue(statusCounts.containsKey(ReservationStatus.CANCELLED))
        assertTrue(statusCounts.containsKey(ReservationStatus.COMPLETED))
    }

    @Test
    fun `고객의 예약 이력 페이징 조회가 정상 동작한다`() {
        // given - 동일 고객의 여러 예약 생성
        val reservation2 = createTestReservation().copy(
            confirmationNumber = "SECOND-RES",
            checkInDate = LocalDate.now().plusDays(10)
        )
        val reservation3 = createTestReservation().copy(
            confirmationNumber = "THIRD-RES",
            checkInDate = LocalDate.now().plusDays(20)
        )
        
        testEntityManager.persistAndFlush(reservation2)
        testEntityManager.persistAndFlush(reservation3)
        testEntityManager.clear()

        // when
        val pageable = PageRequest.of(0, 2)
        val page = reservationRepository.findByGuestEmailOrderByCreatedAtDesc(testGuest.email, pageable)

        // then
        assertEquals(2, page.size)
        assertEquals(3, page.totalElements)
        assertTrue(page.hasNext())
        
        // 생성일 내림차순 정렬 확인
        val reservations = page.content
        assertTrue(
            reservations[0].createdAt.isAfter(reservations[1].createdAt) ||
            reservations[0].createdAt.isEqual(reservations[1].createdAt)
        )
    }

    @Test
    fun `총 매출 계산이 정상 동작한다`() {
        // given - 완료된 예약들 생성
        val completedReservation1 = createTestReservation().copy(
            confirmationNumber = "COMPLETED-1",
            status = ReservationStatus.COMPLETED,
            totalAmount = BigDecimal("150000")
        )
        val completedReservation2 = createTestReservation().copy(
            confirmationNumber = "COMPLETED-2", 
            status = ReservationStatus.CHECKED_OUT,
            totalAmount = BigDecimal("200000"),
            checkInDate = LocalDate.now().minusDays(1)
        )
        
        testEntityManager.persistAndFlush(completedReservation1)
        testEntityManager.persistAndFlush(completedReservation2)
        testEntityManager.clear()

        // when
        val startDate = LocalDate.now().minusDays(2)
        val endDate = LocalDate.now().plusDays(2)
        val totalRevenue = reservationRepository.calculateTotalRevenue(startDate, endDate)

        // then
        assertTrue(totalRevenue >= BigDecimal("350000")) // 최소 350,000원 이상
    }

    @Test
    fun `예약 겹침 확인이 정상 동작한다`() {
        // given - 기존 예약과 겹치는 날짜
        val overlappingCheckIn = testReservation.checkInDate.plusDays(1)
        val overlappingCheckOut = testReservation.checkOutDate.plusDays(1)

        // when
        val hasOverlap = reservationRepository.existsOverlappingReservation(
            testRoom.id,
            overlappingCheckIn,
            overlappingCheckOut
        )

        // then
        assertTrue(hasOverlap)
    }

    @Test
    fun `예약 겹침이 없는 경우 false를 반환한다`() {
        // given - 기존 예약과 겹치지 않는 날짜
        val nonOverlappingCheckIn = testReservation.checkOutDate.plusDays(1)
        val nonOverlappingCheckOut = testReservation.checkOutDate.plusDays(3)

        // when
        val hasOverlap = reservationRepository.existsOverlappingReservation(
            testRoom.id,
            nonOverlappingCheckIn,
            nonOverlappingCheckOut
        )

        // then
        assertFalse(hasOverlap)
    }

    // === 테스트 데이터 생성 헬퍼 메서드들 ===

    private fun createTestGuest(): Guest {
        return Guest(
            firstName = "테스트",
            lastName = "고객",
            email = "test@example.com",
            phoneNumber = "010-1234-5678",
            address = Address(
                street = "테스트로 123",
                city = "서울",
                postalCode = "12345",
                countryCode = "KR"
            )
        )
    }

    private fun createTestRoom(): Room {
        val property = Property(
            name = "테스트 호텔",
            type = PropertyType.HOTEL,
            category = PropertyCategory.BUSINESS,
            starRating = 4,
            address = Address(
                street = "호텔로 456", 
                city = "서울",
                postalCode = "54321",
                countryCode = "KR"
            )
        )

        return Room(
            property = property,
            roomNumber = "101",
            name = "스탠다드 룸",
            type = RoomType.STANDARD,
            bedType = BedType.QUEEN,
            maxOccupancy = 2,
            standardOccupancy = 2,
            baseRate = BigDecimal("120000"),
            size = 30.0,
            floor = 1
        )
    }

    private fun createTestReservation(): Reservation {
        return Reservation(
            id = 0L,
            confirmationNumber = "TEST-CONF-001",
            guest = testGuest,
            room = testRoom,
            checkInDate = LocalDate.now().plusDays(7),
            checkOutDate = LocalDate.now().plusDays(9),
            numberOfGuests = 2,
            numberOfAdults = 2,
            numberOfChildren = 0,
            totalAmount = BigDecimal("240000"),
            roomRate = BigDecimal("120000"),
            status = ReservationStatus.PENDING,
            paymentStatus = PaymentStatus.PENDING,
            guestDetails = ReservationGuestDetails(
                primaryGuestFirstName = "테스트",
                primaryGuestLastName = "고객",
                primaryGuestEmail = "test@example.com"
            ),
            source = ReservationSource.DIRECT,
            createdAt = LocalDateTime.now(),
            lastModifiedAt = LocalDateTime.now()
        )
    }
}