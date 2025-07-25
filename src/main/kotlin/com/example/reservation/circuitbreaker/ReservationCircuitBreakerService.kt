package com.example.reservation.circuitbreaker

import com.example.reservation.controller.CreateReservationRequest
import kotlinx.coroutines.*
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * 예약 시스템에 Circuit Breaker 패턴을 적용한 서비스
 * 외부 서비스 호출 시 장애 전파 방지 및 시스템 안정성 보장
 */
@Service
class ReservationCircuitBreakerService {

    // 다양한 외부 서비스별 Circuit Breaker 설정
    private val paymentServiceCB = CircuitBreakerRegistry.getOrCreate(
        "payment-service",
        CircuitBreakerConfig(
            failureRateThreshold = 0.6,           // 60% 실패율에서 차단
            minimumNumberOfCalls = 5,             // 최소 5회 호출 후 판단
            openTimeout = Duration.ofSeconds(30), // 30초 후 재시도
            halfOpenMaxCalls = 3,                 // Half-Open에서 3회 테스트
            callTimeout = Duration.ofSeconds(10)  // 10초 타임아웃
        )
    )
    
    private val inventoryServiceCB = CircuitBreakerRegistry.getOrCreate(
        "inventory-service", 
        CircuitBreakerConfig(
            failureRateThreshold = 0.4,           // 40% 실패율에서 차단 (재고는 더 민감)
            minimumNumberOfCalls = 8,
            openTimeout = Duration.ofSeconds(45),
            halfOpenMaxCalls = 5,
            callTimeout = Duration.ofSeconds(3)   // 빠른 응답 요구
        )
    )
    
    private val notificationServiceCB = CircuitBreakerRegistry.getOrCreate(
        "notification-service",
        CircuitBreakerConfig(
            failureRateThreshold = 0.8,           // 80% 실패율에서 차단 (알림은 덜 중요)
            minimumNumberOfCalls = 10,
            openTimeout = Duration.ofMinutes(2),  // 2분 후 재시도
            halfOpenMaxCalls = 2,
            callTimeout = Duration.ofSeconds(15)
        )
    )

    /**
     * Circuit Breaker를 통한 예약 처리 데모
     */
    suspend fun demonstrateCircuitBreakerPatterns() {
        println("🔌 Circuit Breaker 패턴 데모")
        println("=" * 50)
        println("시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
        println()

        // 1. 정상 상태에서의 처리
        normalOperationDemo()
        
        // 2. 장애 상황 시뮬레이션
        failureScenarioDemo()
        
        // 3. 복구 과정 시뮬레이션
        recoveryDemo()
        
        // 4. 다중 서비스 장애 처리
        multiServiceFailureDemo()
        
        // 5. 실시간 모니터링
        realTimeMonitoringDemo()
    }

    /**
     * 정상 상태 동작 데모
     */
    private suspend fun normalOperationDemo() {
        println("1️⃣ 정상 상태 동작:")
        println("   모든 외부 서비스가 정상적으로 응답하는 상황")
        
        val successCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        repeat(15) { i ->
            try {
                val reservation = processReservationWithCircuitBreaker(
                    CreateReservationRequest(
                        guestName = "Normal-Guest-$i",
                        roomNumber = "Room-${i % 5 + 1}",
                        checkInDate = "2024-12-25",
                        checkOutDate = "2024-12-27",
                        totalAmount = 300.0 + (i * 50)
                    )
                )
                
                successCount.incrementAndGet()
                if (i < 5) {
                    println("    ✅ 예약 성공: ${reservation.reservationId}")
                }
                
            } catch (e: Exception) {
                println("    ❌ 예약 실패: ${e.message}")
            }
            
            delay(100) // 요청 간격
        }
        
        val endTime = System.currentTimeMillis()
        println("    결과: ${successCount.get()}/15 성공, 실행시간: ${endTime - startTime}ms")
        printCircuitBreakerStatus()
        println()
    }

    /**
     * 장애 상황 시뮬레이션
     */
    private suspend fun failureScenarioDemo() {
        println("2️⃣ 장애 상황 시뮬레이션:")
        println("   결제 서비스에 장애가 발생한 상황")
        
        // 결제 서비스 장애 시뮬레이션 활성화
        simulatePaymentServiceFailure = true
        
        val results = mutableMapOf<String, Int>()
        results["success"] = 0
        results["circuit_breaker_open"] = 0
        results["other_failure"] = 0
        
        repeat(20) { i ->
            try {
                val reservation = processReservationWithCircuitBreaker(
                    CreateReservationRequest(
                        guestName = "Failure-Test-$i",
                        roomNumber = "Room-${i % 3 + 1}",
                        checkInDate = "2024-12-25",
                        checkOutDate = "2024-12-27",
                        totalAmount = 250.0 + Random.nextDouble(100.0)
                    )
                )
                
                results["success"] = results["success"]!! + 1
                if (results["success"]!! <= 3) {
                    println("    ✅ 예약 성공: ${reservation.reservationId}")
                }
                
            } catch (e: CircuitBreakerOpenException) {
                results["circuit_breaker_open"] = results["circuit_breaker_open"]!! + 1
                if (results["circuit_breaker_open"]!! <= 3) {
                    println("    🚫 Circuit Breaker OPEN: ${e.message}")
                }
                
            } catch (e: Exception) {
                results["other_failure"] = results["other_failure"]!! + 1
                if (results["other_failure"]!! <= 3) {
                    println("    ❌ 기타 실패: ${e.message}")
                }
            }
            
            delay(50) // 빠른 요청으로 장애 상황 가속화
        }
        
        println("    장애 상황 결과:")
        println("      성공: ${results["success"]} 건")
        println("      Circuit Breaker 차단: ${results["circuit_breaker_open"]} 건")  
        println("      기타 실패: ${results["other_failure"]} 건")
        
        printCircuitBreakerStatus()
        println()
    }

    /**
     * 복구 과정 시뮬레이션
     */
    private suspend fun recoveryDemo() {
        println("3️⃣ 서비스 복구 과정:")
        println("   장애 서비스가 복구되는 과정 시뮬레이션")
        
        // 30초 대기 (Circuit Breaker Open Timeout)
        println("    ⏳ Circuit Breaker 복구 대기 중... (30초)")
        var waitTime = 0
        while (waitTime < 30) {
            delay(2000)
            waitTime += 2
            if (waitTime % 10 == 0) {
                println("      대기 중... ${waitTime}/30초")
            }
        }
        
        // 서비스 복구 시뮬레이션
        simulatePaymentServiceFailure = false
        println("    🔧 결제 서비스 복구 완료")
        
        // Half-Open 상태에서의 테스트 호출
        println("    🧪 Half-Open 상태 테스트 호출:")
        
        repeat(8) { i ->
            try {
                val reservation = processReservationWithCircuitBreaker(
                    CreateReservationRequest(
                        guestName = "Recovery-Test-$i",
                        roomNumber = "Room-${i % 2 + 1}",
                        checkInDate = "2024-12-25", 
                        checkOutDate = "2024-12-27",
                        totalAmount = 400.0 + (i * 25)
                    )
                )
                
                println("      ✅ 테스트 성공: ${reservation.reservationId}")
                
            } catch (e: Exception) {
                println("      ❌ 테스트 실패: ${e.message}")
            }
            
            delay(200)
        }
        
        printCircuitBreakerStatus()
        println()
    }

    /**
     * 다중 서비스 장애 처리
     */
    private suspend fun multiServiceFailureDemo() {
        println("4️⃣ 다중 서비스 장애 처리:")
        println("   여러 외부 서비스에 동시에 장애가 발생한 상황")
        
        // 여러 서비스 장애 시뮬레이션
        simulateInventoryServiceFailure = true
        simulateNotificationServiceFailure = true
        
        val results = mutableMapOf<String, AtomicInteger>()
        results["total_attempts"] = AtomicInteger(0)
        results["complete_success"] = AtomicInteger(0)
        results["partial_success"] = AtomicInteger(0)
        results["complete_failure"] = AtomicInteger(0)
        
        // 동시성 테스트
        coroutineScope {
            repeat(5) { batchId ->
                launch {
                    repeat(4) { i ->
                        results["total_attempts"]!!.incrementAndGet()
                        
                        try {
                            val reservation = processMultiServiceReservation(
                                CreateReservationRequest(
                                    guestName = "Multi-Service-$batchId-$i",
                                    roomNumber = "Room-${(batchId * 4 + i) % 10 + 1}",
                                    checkInDate = "2024-12-25",
                                    checkOutDate = "2024-12-27",
                                    totalAmount = 350.0 + Random.nextDouble(150.0)
                                )
                            )
                            
                            when {
                                reservation.paymentStatus == "SUCCESS" && 
                                reservation.inventoryStatus == "RESERVED" && 
                                reservation.notificationStatus == "SENT" -> {
                                    results["complete_success"]!!.incrementAndGet()
                                }
                                
                                reservation.paymentStatus == "SUCCESS" -> {
                                    results["partial_success"]!!.incrementAndGet()
                                }
                                
                                else -> {
                                    results["complete_failure"]!!.incrementAndGet()
                                }
                            }
                            
                        } catch (e: Exception) {
                            results["complete_failure"]!!.incrementAndGet()
                        }
                        
                        delay(Random.nextLong(50, 150))
                    }
                }
            }
        }
        
        println("    다중 서비스 장애 결과:")
        println("      총 시도: ${results["total_attempts"]!!.get()} 건")
        println("      완전 성공: ${results["complete_success"]!!.get()} 건")
        println("      부분 성공: ${results["partial_success"]!!.get()} 건")
        println("      완전 실패: ${results["complete_failure"]!!.get()} 건")
        
        // 장애 해제
        simulateInventoryServiceFailure = false
        simulateNotificationServiceFailure = false
        
        printCircuitBreakerStatus()
        println()
    }

    /**
     * 실시간 모니터링 데모
     */
    private suspend fun realTimeMonitoringDemo() {
        println("5️⃣ 실시간 Circuit Breaker 모니터링:")
        println("   15초간 실시간 상태 변화 추적")
        println()
        
        // 백그라운드에서 지속적인 요청 생성
        val monitoringJob = launch {
            repeat(50) { i ->
                launch {
                    try {
                        processReservationWithCircuitBreaker(
                            CreateReservationRequest(
                                guestName = "Monitor-$i",
                                roomNumber = "Room-${i % 8 + 1}",
                                checkInDate = "2024-12-25",
                                checkOutDate = "2024-12-27", 
                                totalAmount = 280.0 + Random.nextDouble(120.0)
                            )
                        )
                    } catch (e: Exception) {
                        // 모니터링 중에는 에러 무시
                    }
                }
                delay(Random.nextLong(200, 500))
            }
        }
        
        // 실시간 상태 출력
        repeat(5) { i ->
            delay(3000) // 3초마다 상태 출력
            println("    📊 모니터링 ${(i + 1) * 3}초:")
            printDetailedCircuitBreakerStatus()
            println()
        }
        
        monitoringJob.cancel()
        println("    ✅ 실시간 모니터링 완료")
    }

    /**
     * Circuit Breaker를 통한 예약 처리
     */
    private suspend fun processReservationWithCircuitBreaker(
        request: CreateReservationRequest
    ): ReservationResult {
        // 1. 결제 서비스 호출
        val paymentResult = paymentServiceCB.execute {
            callPaymentService(request)
        }
        
        // 2. 재고 서비스 호출  
        val inventoryResult = inventoryServiceCB.execute {
            callInventoryService(request)
        }
        
        // 3. 알림 서비스 호출 (논블로킹)
        val notificationResult = try {
            notificationServiceCB.execute {
                callNotificationService(request)
            }
        } catch (e: CircuitBreakerException) {
            // 알림 서비스 실패는 전체 프로세스를 중단시키지 않음
            "FALLBACK"
        }
        
        return ReservationResult(
            reservationId = "RES-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            totalAmount = request.totalAmount,
            paymentStatus = paymentResult,
            inventoryStatus = inventoryResult,
            notificationStatus = notificationResult
        )
    }

    /**
     * 다중 서비스 예약 처리 (부분 실패 허용)
     */
    private suspend fun processMultiServiceReservation(
        request: CreateReservationRequest
    ): ReservationResult {
        // 각 서비스를 독립적으로 호출하여 부분 실패 허용
        val paymentResult = try {
            paymentServiceCB.execute { callPaymentService(request) }
        } catch (e: CircuitBreakerException) {
            "CIRCUIT_BREAKER_OPEN"
        } catch (e: Exception) {
            "FAILED"
        }
        
        val inventoryResult = try {
            inventoryServiceCB.execute { callInventoryService(request) }
        } catch (e: CircuitBreakerException) {
            "CIRCUIT_BREAKER_OPEN"
        } catch (e: Exception) {
            "FAILED"
        }
        
        val notificationResult = try {
            notificationServiceCB.execute { callNotificationService(request) }
        } catch (e: CircuitBreakerException) {
            "CIRCUIT_BREAKER_OPEN"
        } catch (e: Exception) {
            "FAILED"
        }
        
        return ReservationResult(
            reservationId = "MULTI-${System.currentTimeMillis()}-${Random.nextInt(1000)}",
            guestName = request.guestName,
            roomNumber = request.roomNumber,
            totalAmount = request.totalAmount,
            paymentStatus = paymentResult,
            inventoryStatus = inventoryResult,
            notificationStatus = notificationResult
        )
    }

    // ===== 외부 서비스 호출 시뮬레이션 =====

    // 장애 시뮬레이션 플래그들
    private var simulatePaymentServiceFailure = false
    private var simulateInventoryServiceFailure = false
    private var simulateNotificationServiceFailure = false

    private suspend fun callPaymentService(request: CreateReservationRequest): String {
        delay(Random.nextLong(100, 300)) // 네트워크 지연 시뮬레이션
        
        if (simulatePaymentServiceFailure && Random.nextDouble() < 0.7) {
            throw RuntimeException("Payment service temporarily unavailable")
        }
        
        return "SUCCESS"
    }
    
    private suspend fun callInventoryService(request: CreateReservationRequest): String {
        delay(Random.nextLong(50, 150))
        
        if (simulateInventoryServiceFailure && Random.nextDouble() < 0.6) {
            throw RuntimeException("Inventory service timeout")
        }
        
        return "RESERVED"
    }
    
    private suspend fun callNotificationService(request: CreateReservationRequest): String {
        delay(Random.nextLong(200, 500))
        
        if (simulateNotificationServiceFailure && Random.nextDouble() < 0.8) {
            throw RuntimeException("Notification service down")
        }
        
        return "SENT"
    }

    // ===== 유틸리티 함수들 =====

    private fun printCircuitBreakerStatus() {
        println("    📊 Circuit Breaker 상태:")
        CircuitBreakerRegistry.getAllStatus().forEach { status ->
            val metrics = status.metrics
            println("      ${status.name}: ${status.state} " +
                    "(성공: ${metrics.successCalls}, 실패: ${metrics.failureCalls + metrics.timeoutCalls}, " +
                    "거부: ${metrics.rejectedCalls}, 실패율: ${"%.1f".format(metrics.failureRate * 100)}%)")
        }
    }
    
    private fun printDetailedCircuitBreakerStatus() {
        CircuitBreakerRegistry.getAllStatus().forEach { status ->
            val metrics = status.metrics
            println("      🔌 ${status.name}:")
            println("         상태: ${status.state}")
            println("         총 호출: ${metrics.totalCalls}, 성공: ${metrics.successCalls}")
            println("         실패: ${metrics.failureCalls}, 타임아웃: ${metrics.timeoutCalls}")
            println("         거부: ${metrics.rejectedCalls}, 실패율: ${"%.1f".format(metrics.failureRate * 100)}%")
            println("         연속 성공: ${metrics.consecutiveSuccesses}, 연속 실패: ${metrics.consecutiveFailures}")
        }
    }

    /**
     * 모든 Circuit Breaker 상태 조회 (관리 API용)
     */
    fun getAllCircuitBreakerStatus(): List<CircuitBreakerStatus> {
        return CircuitBreakerRegistry.getAllStatus()
    }

    /**
     * 특정 Circuit Breaker 리셋 (관리 API용)
     */
    fun resetCircuitBreaker(name: String): Boolean {
        val circuitBreaker = CircuitBreakerRegistry.get(name)
        return if (circuitBreaker != null) {
            circuitBreaker.reset()
            true
        } else {
            false
        }
    }

    /**
     * 특정 Circuit Breaker 상태 강제 변경 (테스트용)
     */
    fun forceCircuitBreakerState(name: String, state: CircuitBreakerState): Boolean {
        val circuitBreaker = CircuitBreakerRegistry.get(name)
        return if (circuitBreaker != null) {
            circuitBreaker.forceState(state)
            true
        } else {
            false
        }
    }
}

/**
 * 예약 처리 결과 데이터 클래스
 */
data class ReservationResult(
    val reservationId: String,
    val guestName: String,
    val roomNumber: String,
    val totalAmount: Double,
    val paymentStatus: String,
    val inventoryStatus: String,
    val notificationStatus: String
)

// 문자열 반복을 위한 확장 함수
private operator fun String.times(n: Int): String = this.repeat(n)