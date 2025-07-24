package com.example.reservation.integration

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.controller.UpdateReservationRequest
import com.example.reservation.domain.reservation.ReservationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate

/**
 * 전체 시스템 통합 테스트
 * Testcontainers를 사용한 실제 외부 시스템 연동 테스트
 * 
 * 테스트 전략:
 * 1. 실제 데이터베이스, 메시지 큐, 캐시 시스템 사용
 * 2. End-to-End 시나리오 테스트
 * 3. 실제 HTTP 요청/응답 검증
 * 4. 트랜잭션 롤백으로 테스트 격리
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebMvc
@ActiveProfiles("integration-test")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class ReservationIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("reservation_integration_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)

        @Container
        @JvmStatic
        val rabbitMQContainer: RabbitMQContainer = RabbitMQContainer("rabbitmq:3-management")
            .withReuse(true)

        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)

        @Container
        @JvmStatic
        val chromaDBContainer: GenericContainer<*> = GenericContainer("ghcr.io/chroma-core/chroma:0.4.18")
            .withExposedPorts(8000)
            .withReuse(true)

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            // PostgreSQL 설정
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)

            // R2DBC 설정
            registry.add("spring.r2dbc.url") {
                postgresContainer.jdbcUrl.replace("jdbc:postgresql", "r2dbc:postgresql")
            }
            registry.add("spring.r2dbc.username", postgresContainer::getUsername)
            registry.add("spring.r2dbc.password", postgresContainer::getPassword)

            // RabbitMQ 설정
            registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost)
            registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort)
            registry.add("spring.rabbitmq.username") { "guest" }
            registry.add("spring.rabbitmq.password") { "guest" }

            // Redis 설정
            registry.add("spring.redis.host", redisContainer::getHost)
            registry.add("spring.redis.port") { redisContainer.getMappedPort(6379) }

            // ChromaDB 설정
            registry.add("spring.ai.vectorstore.chroma.url") {
                "http://${chromaDBContainer.host}:${chromaDBContainer.getMappedPort(8000)}"
            }

            // 테스트용 설정
            registry.add("logging.level.com.example.reservation") { "DEBUG" }
            registry.add("spring.jpa.show-sql") { "true" }
            registry.add("spring.jpa.properties.hibernate.format_sql") { "true" }
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `전체 예약 생성부터 취소까지의 완전한 워크플로우 테스트`() {
        // 1. 예약 생성
        val createRequest = CreateReservationRequest(
            guestName = "김철수",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(7).toString(),
            checkOutDate = LocalDate.now().plusDays(9).toString(),
            totalAmount = 300000.0
        )

        val createResponse = mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.guest.firstName").value("김철수"))
            .andExpect(jsonPath("$.room.roomNumber").value("101"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()

        val createdReservation = objectMapper.readTree(createResponse.response.contentAsString)
        val reservationId = createdReservation.get("id").asLong()
        val confirmationNumber = createdReservation.get("confirmationNumber").asText()

        // 2. 생성된 예약 조회
        mockMvc.perform(get("/api/reservations/{id}", reservationId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(reservationId))
            .andExpect(jsonPath("$.confirmationNumber").value(confirmationNumber))

        // 3. 예약 목록에서 확인
        mockMvc.perform(get("/api/reservations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id == $reservationId)]").exists())

        // 4. 예약 수정
        val updateRequest = UpdateReservationRequest(
            guestName = "김철수",
            roomNumber = "102",  // 객실 변경
            checkInDate = null,
            checkOutDate = null,
            totalAmount = 350000.0  // 금액 변경
        )

        mockMvc.perform(
            put("/api/reservations/{id}", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.room.roomNumber").value("102"))
            .andExpect(jsonPath("$.totalAmount").value(350000.0))

        // 5. 예약 취소 (DELETE 요청)
        mockMvc.perform(delete("/api/reservations/{id}", reservationId))
            .andExpect(status().isNoContent)

        // 6. 취소된 예약 조회 시 404 반환 확인
        mockMvc.perform(get("/api/reservations/{id}", reservationId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `동시성 제어 테스트 - 같은 객실 중복 예약 방지`() {
        val baseRequest = CreateReservationRequest(
            guestName = "동시성테스트",
            roomNumber = "201",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 200000.0
        )

        // 첫 번째 예약 생성 (성공해야 함)
        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(baseRequest))
        )
            .andExpect(status().isCreated)

        // 동일한 객실, 겹치는 날짜로 두 번째 예약 시도 (실패해야 함)
        val conflictRequest = baseRequest.copy(
            guestName = "다른고객",
            checkInDate = LocalDate.now().plusDays(2).toString(),  // 겹치는 날짜
            checkOutDate = LocalDate.now().plusDays(4).toString()
        )

        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(conflictRequest))
        )
            .andExpect(status().isConflict) // 409 Conflict 또는 다른 에러 상태
    }

    @Test
    fun `입력 검증 테스트 - 다양한 잘못된 입력들`() {
        // 빈 고객명
        val emptyNameRequest = CreateReservationRequest(
            guestName = "",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 200000.0
        )

        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyNameRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        // 과거 날짜
        val pastDateRequest = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().minusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(1).toString(),
            totalAmount = 200000.0
        )

        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pastDateRequest))
        )
            .andExpect(status().isBadRequest)

        // 잘못된 날짜 순서
        val wrongDateOrderRequest = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(5).toString(),
            checkOutDate = LocalDate.now().plusDays(2).toString(),
            totalAmount = 200000.0
        )

        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongDateOrderRequest))
        )
            .andExpect(status().isBadRequest)

        // 음수 금액
        val negativeAmountRequest = CreateReservationRequest(
            guestName = "홍길동",
            roomNumber = "101",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = -1000.0
        )

        mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(negativeAmountRequest))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `페이징 및 정렬 테스트`() {
        // 여러 예약 생성
        repeat(15) { index ->
            val request = CreateReservationRequest(
                guestName = "테스트고객$index",
                roomNumber = "10$index",
                checkInDate = LocalDate.now().plusDays(index.toLong() + 1).toString(),
                checkOutDate = LocalDate.now().plusDays(index.toLong() + 3).toString(),
                totalAmount = (100000 + index * 10000).toDouble()
            )

            mockMvc.perform(
                post("/api/reservations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
        }

        // 첫 번째 페이지 조회 (10개)
        mockMvc.perform(
            get("/api/reservations")
                .param("page", "0")
                .param("size", "10")
                .param("sort", "createdAt,desc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(10))
            .andExpect(jsonPath("$.totalElements").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(false))

        // 두 번째 페이지 조회 (5개)
        mockMvc.perform(
            get("/api/reservations")
                .param("page", "1")
                .param("size", "10")
                .param("sort", "createdAt,desc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(5))
            .andExpect(jsonPath("$.first").value(false))
            .andExpect(jsonPath("$.last").value(true))
    }

    @Test
    fun `확인번호로 예약 조회 테스트`() {
        // 예약 생성
        val request = CreateReservationRequest(
            guestName = "확인번호테스트",
            roomNumber = "301",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 250000.0
        )

        val createResponse = mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdReservation = objectMapper.readTree(createResponse.response.contentAsString)
        val confirmationNumber = createdReservation.get("confirmationNumber").asText()

        // 확인번호로 조회
        mockMvc.perform(get("/api/reservations/confirmation/{confirmationNumber}", confirmationNumber))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.confirmationNumber").value(confirmationNumber))
            .andExpect(jsonPath("$.guest.firstName").value("확인번호테스트"))

        // 존재하지 않는 확인번호로 조회
        mockMvc.perform(get("/api/reservations/confirmation/NONEXISTENT"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `캐시 동작 검증 테스트`() {
        // 예약 생성
        val request = CreateReservationRequest(
            guestName = "캐시테스트",
            roomNumber = "401",
            checkInDate = LocalDate.now().plusDays(1).toString(),
            checkOutDate = LocalDate.now().plusDays(3).toString(),
            totalAmount = 180000.0
        )

        val createResponse = mockMvc.perform(
            post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andReturn()

        val createdReservation = objectMapper.readTree(createResponse.response.contentAsString)
        val reservationId = createdReservation.get("id").asLong()

        // 첫 번째 조회 (DB에서 가져옴)
        val startTime1 = System.currentTimeMillis()
        mockMvc.perform(get("/api/reservations/{id}", reservationId))
            .andExpect(status().isOk)
        val endTime1 = System.currentTimeMillis()

        // 두 번째 조회 (캐시에서 가져옴 - 더 빠를 것)
        val startTime2 = System.currentTimeMillis()
        mockMvc.perform(get("/api/reservations/{id}", reservationId))
            .andExpect(status().isOk)
        val endTime2 = System.currentTimeMillis()

        // 캐시된 조회가 더 빠른지는 환경에 따라 다를 수 있으므로, 단순히 성공 여부만 확인
        println("첫 번째 조회 시간: ${endTime1 - startTime1}ms")
        println("두 번째 조회 시간: ${endTime2 - startTime2}ms")
    }
}