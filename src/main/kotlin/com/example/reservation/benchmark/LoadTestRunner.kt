package com.example.reservation.benchmark

import com.example.reservation.controller.CreateReservationRequest
import kotlinx.coroutines.*
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * 부하 테스트 실행기
 * 실제 HTTP 요청을 통한 부하 테스트
 */
@Component
@Profile("loadtest")
class LoadTestRunner : ApplicationRunner {

    private val restTemplate = RestTemplate()
    private val webClient = WebClient.builder()
        .baseUrl("http://localhost:8080")
        .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024) }
        .build()

    data class LoadTestConfig(
        val testName: String,
        val totalRequests: Int,
        val concurrentUsers: Int,
        val rampUpTimeSeconds: Int = 10,
        val testDurationSeconds: Int = 60,
        val endpoint: String
    )

    data class LoadTestMetrics(
        val totalRequests: Int,
        val successfulRequests: AtomicInteger = AtomicInteger(0),
        val failedRequests: AtomicInteger = AtomicInteger(0),
        val totalResponseTime: AtomicLong = AtomicLong(0),
        val minResponseTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
        val maxResponseTime: AtomicLong = AtomicLong(0),
        val responseTimes: ConcurrentHashMap<String, MutableList<Long>> = ConcurrentHashMap(),
        val errorsByType: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
        val requestsPerSecond: ConcurrentHashMap<Int, AtomicInteger> = ConcurrentHashMap()
    )

    override fun run(args: org.springframework.boot.ApplicationArguments) {
        println("🚀 부하 테스트 시작...")
        
        val testConfigs = listOf(
            // MVC 엔드포인트 테스트
            LoadTestConfig(
                testName = "MVC API 부하 테스트",
                totalRequests = 1000,
                concurrentUsers = 50,
                rampUpTimeSeconds = 10,
                testDurationSeconds = 60,
                endpoint = "/api/reservations"
            ),
            
            // WebFlux 엔드포인트 테스트
            LoadTestConfig(
                testName = "WebFlux API 부하 테스트",
                totalRequests = 1000,
                concurrentUsers = 50,
                rampUpTimeSeconds = 10,
                testDurationSeconds = 60,
                endpoint = "/api/webflux/reservations"
            ),
            
            // Java MVC 엔드포인트 테스트
            LoadTestConfig(
                testName = "Java MVC API 부하 테스트",
                totalRequests = 1000,
                concurrentUsers = 50,
                rampUpTimeSeconds = 10,
                testDurationSeconds = 60,
                endpoint = "/api/java/reservations"
            ),
            
            // Java WebFlux 엔드포인트 테스트
            LoadTestConfig(
                testName = "Java WebFlux API 부하 테스트", 
                totalRequests = 1000,
                concurrentUsers = 50,
                rampUpTimeSeconds = 10,
                testDurationSeconds = 60,
                endpoint = "/api/webflux-java/reservations"
            )
        )

        testConfigs.forEach { config ->
            println("\n" + "=".repeat(60))
            println("▶️ ${config.testName} 시작")
            println("=".repeat(60))
            
            val metrics = runLoadTest(config)
            printLoadTestResults(config, metrics)
            
            // 테스트 간 간격
            Thread.sleep(5000)
        }
        
        println("\n🏁 모든 부하 테스트 완료!")
    }

    private fun runLoadTest(config: LoadTestConfig): LoadTestMetrics {
        val metrics = LoadTestMetrics(config.totalRequests)
        
        return runBlocking {
            val startTime = System.currentTimeMillis()
            
            // 동시 사용자 시뮬레이션
            val userJobs = (1..config.concurrentUsers).map { userId ->
                async(Dispatchers.IO) {
                    simulateUser(userId, config, metrics, startTime)
                }
            }
            
            // 실시간 모니터링
            val monitoringJob = async {
                monitorProgress(config, metrics, startTime)
            }
            
            // 모든 사용자 시뮬레이션 완료 대기
            userJobs.awaitAll()
            monitoringJob.cancel()
            
            metrics
        }
    }

    private suspend fun simulateUser(
        userId: Int,
        config: LoadTestConfig,
        metrics: LoadTestMetrics,
        startTime: Long
    ) {
        val requestsPerUser = config.totalRequests / config.concurrentUsers
        val rampUpDelay = (config.rampUpTimeSeconds * 1000 / config.concurrentUsers) * (userId - 1)
        
        // 램프업 지연
        delay(rampUpDelay.toLong())
        
        repeat(requestsPerUser) { requestIndex ->
            try {
                val responseTime = when {
                    config.endpoint.contains("webflux") -> executeWebFluxRequest(config.endpoint, requestIndex)
                    else -> executeMvcRequest(config.endpoint, requestIndex)
                }
                
                // 메트릭 업데이트
                metrics.successfulRequests.incrementAndGet()
                metrics.totalResponseTime.addAndGet(responseTime)
                
                // 최소/최대 응답 시간 업데이트
                metrics.minResponseTime.updateAndGet { minOf(it, responseTime) }
                metrics.maxResponseTime.updateAndGet { maxOf(it, responseTime) }
                
                // 응답 시간 기록
                val second = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                metrics.requestsPerSecond.computeIfAbsent(second) { AtomicInteger(0) }.incrementAndGet()
                
                // 요청 간 간격 (실제 사용자 시뮬레이션)
                delay(Random.nextLong(100, 1000))
                
            } catch (e: Exception) {
                metrics.failedRequests.incrementAndGet()
                val errorType = e.javaClass.simpleName
                metrics.errorsByType.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
            }
        }
    }

    private suspend fun executeMvcRequest(endpoint: String, requestIndex: Int): Long {
        return measureTimeMillis {
            val request = createTestRequest(requestIndex)
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(request, headers)
            
            restTemplate.postForObject("http://localhost:8080$endpoint", entity, Any::class.java)
        }
    }

    private suspend fun executeWebFluxRequest(endpoint: String, requestIndex: Int): Long {
        return measureTimeMillis {
            val request = createTestRequest(requestIndex)
            
            webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Any::class.java)
                .timeout(Duration.ofSeconds(10))
                .awaitSingleOrNull()
        }
    }

    private fun createTestRequest(requestIndex: Int): CreateReservationRequest {
        return CreateReservationRequest(
            guestName = "Load Test Guest $requestIndex",
            roomNumber = "Room ${Random.nextInt(1, 101)}",
            checkInDate = "2024-12-${Random.nextInt(10, 28)}",
            checkOutDate = "2024-12-${Random.nextInt(28, 31)}",
            totalAmount = Random.nextDouble(100.0, 500.0)
        )
    }

    private suspend fun monitorProgress(
        config: LoadTestConfig,
        metrics: LoadTestMetrics,
        startTime: Long
    ) {
        while (true) {
            delay(10000) // 10초마다 진행상황 출력
            
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
            val completedRequests = metrics.successfulRequests.get() + metrics.failedRequests.get()
            val progress = (completedRequests.toDouble() / config.totalRequests * 100).toInt()
            
            println("⏱️ 진행률: $progress% ($completedRequests/${config.totalRequests}) | " +
                    "경과시간: ${elapsedSeconds}초 | " +
                    "성공: ${metrics.successfulRequests.get()} | " +
                    "실패: ${metrics.failedRequests.get()}")
            
            if (completedRequests >= config.totalRequests) break
        }
    }

    private fun printLoadTestResults(config: LoadTestConfig, metrics: LoadTestMetrics) {
        val totalRequests = metrics.successfulRequests.get() + metrics.failedRequests.get()
        val successRate = if (totalRequests > 0) (metrics.successfulRequests.get().toDouble() / totalRequests * 100) else 0.0
        val averageResponseTime = if (metrics.successfulRequests.get() > 0) {
            metrics.totalResponseTime.get().toDouble() / metrics.successfulRequests.get()
        } else 0.0
        
        println("\n📊 ${config.testName} 결과")
        println("-".repeat(50))
        println("설정:")
        println("  - 총 요청 수: ${config.totalRequests}")
        println("  - 동시 사용자: ${config.concurrentUsers}")
        println("  - 램프업 시간: ${config.rampUpTimeSeconds}초")
        println("  - 엔드포인트: ${config.endpoint}")
        
        println("\n결과:")
        println("  - 완료된 요청: $totalRequests")
        println("  - 성공한 요청: ${metrics.successfulRequests.get()}")
        println("  - 실패한 요청: ${metrics.failedRequests.get()}")
        println("  - 성공률: ${"%.2f".format(successRate)}%")
        println("  - 평균 응답시간: ${"%.2f".format(averageResponseTime)}ms")
        println("  - 최소 응답시간: ${if (metrics.minResponseTime.get() == Long.MAX_VALUE) 0 else metrics.minResponseTime.get()}ms")
        println("  - 최대 응답시간: ${metrics.maxResponseTime.get()}ms")
        
        // 초당 요청 수 분석
        val peakRPS = metrics.requestsPerSecond.values.maxOfOrNull { it.get() } ?: 0
        val averageRPS = metrics.requestsPerSecond.values.map { it.get() }.average()
        
        println("  - 최대 RPS: $peakRPS")
        println("  - 평균 RPS: ${"%.2f".format(averageRPS)}")
        
        // 에러 분석
        if (metrics.errorsByType.isNotEmpty()) {
            println("\n에러 분석:")
            metrics.errorsByType.forEach { (errorType, count) ->
                println("  - $errorType: ${count.get()}회")
            }
        }
        
        // 성능 평가
        println("\n성능 평가:")
        when {
            successRate >= 99.0 && averageResponseTime < 100 -> println("  ✅ 우수: 안정적이고 빠른 성능")
            successRate >= 95.0 && averageResponseTime < 200 -> println("  ✅ 양호: 실용적인 성능")
            successRate >= 90.0 && averageResponseTime < 500 -> println("  ⚠️ 보통: 성능 최적화 권장")
            else -> println("  ❌ 미흡: 성능 개선 필요")
        }
    }
}

/**
 * WebClient 확장 함수 (Kotlin Coroutines 지원)
 */
suspend fun <T> Mono<T>.awaitSingleOrNull(): T? = this.awaitSingleOrNull()