package com.example.reservation.benchmark

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.service.ReservationService
import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * 성능 벤치마크 도구
 * MVC vs WebFlux, Java vs Kotlin 성능 비교
 */
@Component
class PerformanceBenchmark(
    private val reservationService: ReservationService
) : CommandLineRunner {

    private val restTemplate = RestTemplate()
    private val webClient = WebClient.builder()
        .baseUrl("http://localhost:8080")
        .build()

    data class BenchmarkResult(
        val testName: String,
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val totalTimeMs: Long,
        val averageTimeMs: Double,
        val requestsPerSecond: Double,
        val minTimeMs: Long,
        val maxTimeMs: Long,
        val p95TimeMs: Long,
        val p99TimeMs: Long
    )

    override fun run(vararg args: String?) {
        if (args.contains("--benchmark")) {
            println("🚀 성능 벤치마크 시작...")
            runAllBenchmarks()
        }
    }

    private fun runAllBenchmarks() {
        val results = mutableListOf<BenchmarkResult>()

        // 1. 단일 요청 성능 테스트
        results.add(benchmarkSingleRequests())

        // 2. 동시 요청 성능 테스트
        results.add(benchmarkConcurrentRequests())

        // 3. MVC vs WebFlux 비교
        results.add(benchmarkMvcVsWebFlux())

        // 4. Java vs Kotlin 비교
        results.add(benchmarkJavaVsKotlin())

        // 결과 출력
        printBenchmarkResults(results)
    }

    /**
     * 단일 요청 성능 테스트
     */
    fun benchmarkSingleRequests(): BenchmarkResult {
        val requestCount = 1000
        val times = mutableListOf<Long>()
        var successCount = 0
        var failCount = 0

        val totalTime = measureTimeMillis {
            repeat(requestCount) { i ->
                try {
                    val time = measureTimeMillis {
                        val request = CreateReservationRequest(
                            guestName = "Test Guest $i",
                            roomNumber = "Room ${i % 100 + 1}",
                            checkInDate = "2024-12-25",
                            checkOutDate = "2024-12-27",
                            totalAmount = 200.0 + (i % 100)
                        )
                        reservationService.create(request)
                    }
                    times.add(time)
                    successCount++
                } catch (e: Exception) {
                    failCount++
                }
            }
        }

        return calculateBenchmarkResult(
            "단일 요청 성능",
            requestCount,
            successCount,
            failCount,
            totalTime,
            times
        )
    }

    /**
     * 동시 요청 성능 테스트
     */
    fun benchmarkConcurrentRequests(): BenchmarkResult {
        val requestCount = 1000
        val concurrency = 50
        val times = mutableListOf<Long>()
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        val totalTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..requestCount).chunked(requestCount / concurrency).map { chunk ->
                    async(Dispatchers.IO) {
                        chunk.forEach { i ->
                            try {
                                val time = measureTimeMillis {
                                    val request = CreateReservationRequest(
                                        guestName = "Concurrent Guest $i",
                                        roomNumber = "Room ${i % 100 + 1}",
                                        checkInDate = "2024-12-25",
                                        checkOutDate = "2024-12-27",
                                        totalAmount = 200.0 + (i % 100)
                                    )
                                    reservationService.create(request)
                                }
                                synchronized(times) { times.add(time) }
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        return calculateBenchmarkResult(
            "동시 요청 성능 (동시성: $concurrency)",
            requestCount,
            successCount.get(),
            failCount.get(),
            totalTime,
            times
        )
    }

    /**
     * MVC vs WebFlux 성능 비교
     */
    fun benchmarkMvcVsWebFlux(): BenchmarkResult {
        val requestCount = 500
        val mvcTimes = mutableListOf<Long>()
        val webFluxTimes = mutableListOf<Long>()
        var mvcSuccess = 0
        var webFluxSuccess = 0
        var mvcFail = 0
        var webFluxFail = 0

        // MVC 테스트
        val mvcTime = measureTimeMillis {
            repeat(requestCount) { i ->
                try {
                    val time = measureTimeMillis {
                        // MVC 엔드포인트 호출 시뮬레이션
                        val request = CreateReservationRequest(
                            guestName = "MVC Guest $i",
                            roomNumber = "Room ${i % 100 + 1}",
                            checkInDate = "2024-12-25",
                            checkOutDate = "2024-12-27",
                            totalAmount = 200.0 + (i % 100)
                        )
                        reservationService.create(request)
                    }
                    mvcTimes.add(time)
                    mvcSuccess++
                } catch (e: Exception) {
                    mvcFail++
                }
            }
        }

        // WebFlux 테스트
        val webFluxTime = measureTimeMillis {
            runBlocking {
                repeat(requestCount) { i ->
                    try {
                        val time = measureTimeMillis {
                            // WebFlux 스타일 비동기 처리 시뮬레이션
                            val request = CreateReservationRequest(
                                guestName = "WebFlux Guest $i",
                                roomNumber = "Room ${i % 100 + 1}",
                                checkInDate = "2024-12-25",
                                checkOutDate = "2024-12-27",
                                totalAmount = 200.0 + (i % 100)
                            )
                            
                            Mono.fromCallable { reservationService.create(request) }
                                .awaitSingle()
                        }
                        webFluxTimes.add(time)
                        webFluxSuccess++
                    } catch (e: Exception) {
                        webFluxFail++
                    }
                }
            }
        }

        // 결과 비교를 위해 통합된 결과 반환
        return BenchmarkResult(
            testName = "MVC vs WebFlux 비교",
            totalRequests = requestCount * 2,
            successfulRequests = mvcSuccess + webFluxSuccess,
            failedRequests = mvcFail + webFluxFail,
            totalTimeMs = mvcTime + webFluxTime,
            averageTimeMs = (mvcTimes.average() + webFluxTimes.average()) / 2,
            requestsPerSecond = (requestCount * 2.0) / ((mvcTime + webFluxTime) / 1000.0),
            minTimeMs = minOf(mvcTimes.minOrNull() ?: 0, webFluxTimes.minOrNull() ?: 0),
            maxTimeMs = maxOf(mvcTimes.maxOrNull() ?: 0, webFluxTimes.maxOrNull() ?: 0),
            p95TimeMs = calculatePercentile(mvcTimes + webFluxTimes, 95.0),
            p99TimeMs = calculatePercentile(mvcTimes + webFluxTimes, 99.0)
        )
    }

    /**
     * Java vs Kotlin 성능 비교 (시뮬레이션)
     */
    fun benchmarkJavaVsKotlin(): BenchmarkResult {
        val requestCount = 1000
        val kotlinTimes = mutableListOf<Long>()
        var kotlinSuccess = 0
        var kotlinFail = 0

        // Kotlin 구현 테스트
        val kotlinTime = measureTimeMillis {
            repeat(requestCount) { i ->
                try {
                    val time = measureTimeMillis {
                        // Kotlin 스타일 처리
                        val request = CreateReservationRequest(
                            guestName = "Kotlin Guest $i",
                            roomNumber = "Room ${i % 100 + 1}",
                            checkInDate = "2024-12-25",
                            checkOutDate = "2024-12-27",
                            totalAmount = 200.0 + (i % 100)
                        )
                        
                        // Kotlin 특화 기능 사용
                        reservationService.create(request).also { reservation ->
                            // Kotlin의 scope functions 활용
                            reservation.takeIf { it.id > 0 }
                                ?.let { validateReservation(it) }
                        }
                    }
                    kotlinTimes.add(time)
                    kotlinSuccess++
                } catch (e: Exception) {
                    kotlinFail++
                }
            }
        }

        return calculateBenchmarkResult(
            "Kotlin 구현 성능",
            requestCount,
            kotlinSuccess,
            kotlinFail,
            kotlinTime,
            kotlinTimes
        )
    }

    private fun validateReservation(reservation: Reservation): Boolean {
        return reservation.isActive() && reservation.canBeModified()
    }

    private fun calculateBenchmarkResult(
        testName: String,
        totalRequests: Int,
        successfulRequests: Int,
        failedRequests: Int,
        totalTimeMs: Long,
        times: List<Long>
    ): BenchmarkResult {
        val sortedTimes = times.sorted()
        
        return BenchmarkResult(
            testName = testName,
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            failedRequests = failedRequests,
            totalTimeMs = totalTimeMs,
            averageTimeMs = if (times.isNotEmpty()) times.average() else 0.0,
            requestsPerSecond = if (totalTimeMs > 0) (successfulRequests * 1000.0) / totalTimeMs else 0.0,
            minTimeMs = sortedTimes.firstOrNull() ?: 0,
            maxTimeMs = sortedTimes.lastOrNull() ?: 0,
            p95TimeMs = calculatePercentile(sortedTimes, 95.0),
            p99TimeMs = calculatePercentile(sortedTimes, 99.0)
        )
    }

    private fun calculatePercentile(sortedTimes: List<Long>, percentile: Double): Long {
        if (sortedTimes.isEmpty()) return 0
        val index = ((percentile / 100.0) * (sortedTimes.size - 1)).toInt()
        return sortedTimes[index.coerceIn(0, sortedTimes.size - 1)]
    }

    private fun printBenchmarkResults(results: List<BenchmarkResult>) {
        println("\n" + "=".repeat(80))
        println("🏆 성능 벤치마크 결과 보고서")
        println("=".repeat(80))
        println("실행 시간: ${LocalDateTime.now()}")
        println()

        results.forEach { result ->
            println("📊 ${result.testName}")
            println("-".repeat(50))
            println("총 요청 수: ${result.totalRequests}")
            println("성공 요청: ${result.successfulRequests}")
            println("실패 요청: ${result.failedRequests}")
            println("성공률: ${"%.2f".format((result.successfulRequests.toDouble() / result.totalRequests) * 100)}%")
            println("총 실행 시간: ${result.totalTimeMs}ms")
            println("평균 응답 시간: ${"%.2f".format(result.averageTimeMs)}ms")
            println("초당 요청 수 (RPS): ${"%.2f".format(result.requestsPerSecond)}")
            println("최소 응답 시간: ${result.minTimeMs}ms")
            println("최대 응답 시간: ${result.maxTimeMs}ms")
            println("95% 응답 시간: ${result.p95TimeMs}ms")
            println("99% 응답 시간: ${result.p99TimeMs}ms")
            println()
        }

        // 성능 분석 및 권장사항
        printPerformanceAnalysis(results)
    }

    private fun printPerformanceAnalysis(results: List<BenchmarkResult>) {
        println("📈 성능 분석 및 권장사항")
        println("-".repeat(50))
        
        val singleRequestResult = results.find { it.testName.contains("단일 요청") }
        val concurrentResult = results.find { it.testName.contains("동시 요청") }
        
        if (singleRequestResult != null && concurrentResult != null) {
            val scalabilityRatio = concurrentResult.requestsPerSecond / singleRequestResult.requestsPerSecond
            println("확장성 지수: ${"%.2f".format(scalabilityRatio)}")
            
            when {
                scalabilityRatio > 0.8 -> println("✅ 우수한 확장성: 동시 처리 성능이 양호합니다.")
                scalabilityRatio > 0.5 -> println("⚠️ 보통 확장성: 동시 처리 최적화가 필요할 수 있습니다.")
                else -> println("❌ 낮은 확장성: 동시 처리 성능 개선이 필요합니다.")
            }
        }

        println("\n💡 최적화 제안:")
        results.forEach { result ->
            when {
                result.averageTimeMs > 100 -> println("- ${result.testName}: 응답 시간 최적화 필요 (현재: ${"%.2f".format(result.averageTimeMs)}ms)")
                result.requestsPerSecond < 100 -> println("- ${result.testName}: 처리량 개선 필요 (현재: ${"%.2f".format(result.requestsPerSecond)} RPS)")
                result.p99TimeMs > result.averageTimeMs * 5 -> println("- ${result.testName}: 응답 시간 편차 개선 필요")
            }
        }
        
        println("\n" + "=".repeat(80))
    }
}