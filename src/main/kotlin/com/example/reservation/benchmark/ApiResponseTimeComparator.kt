package com.example.reservation.benchmark

import com.example.reservation.controller.CreateReservationRequest
import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * API 응답 시간 비교 도구
 * MVC vs WebFlux 성능을 실제 HTTP 요청으로 비교 측정
 */
@Component 
class ApiResponseTimeComparator(
) : CommandLineRunner {

    private val restTemplate = RestTemplate().apply {
        // 연결 타임아웃 설정
        requestFactory = org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(5000)
            setReadTimeout(10000)
        }
    }
    
    private val webClient = WebClient.builder()
        .baseUrl("http://localhost:8080")
        .codecs { it.defaultCodecs().maxInMemorySize(1024 * 1024) }
        .build()

    data class ApiEndpoint(
        val name: String,
        val path: String,
        val type: ApiType,
        val language: Language
    )

    enum class ApiType { MVC, WEBFLUX }
    enum class Language { KOTLIN, JAVA }

    data class ResponseTimeResult(
        val endpoint: ApiEndpoint,
        val totalRequests: Int,
        val successfulRequests: AtomicInteger = AtomicInteger(0),
        val failedRequests: AtomicInteger = AtomicInteger(0),
        val totalResponseTime: AtomicLong = AtomicLong(0),
        val minResponseTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
        val maxResponseTime: AtomicLong = AtomicLong(0),
        val responseTimes: ConcurrentHashMap<Long, Int> = ConcurrentHashMap(), // 응답시간 분포
        val errorMessages: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap()
    ) {
        fun getAverageResponseTime(): Double = 
            if (successfulRequests.get() > 0) totalResponseTime.get().toDouble() / successfulRequests.get() 
            else 0.0
        
        fun getSuccessRate(): Double = 
            if (totalRequests > 0) successfulRequests.get().toDouble() / totalRequests * 100 
            else 0.0
        
        fun getThroughput(): Double = 
            if (totalResponseTime.get() > 0) successfulRequests.get().toDouble() / (totalResponseTime.get() / 1000.0) 
            else 0.0
    }

    override fun run(vararg args: String?) {
        if (args.contains("--response-time-comparison")) {
            println("📊 API 응답 시간 비교 분석 시작...")
            runResponseTimeComparison()
        }
    }

    fun runResponseTimeComparison() {
        val endpoints = listOf(
            ApiEndpoint("Kotlin MVC", "/api/reservations", ApiType.MVC, Language.KOTLIN),
            ApiEndpoint("Kotlin WebFlux", "/api/webflux/reservations", ApiType.WEBFLUX, Language.KOTLIN),
            ApiEndpoint("Java MVC", "/api/java/reservations", ApiType.MVC, Language.JAVA),
            ApiEndpoint("Java WebFlux", "/api/webflux-java/reservations", ApiType.WEBFLUX, Language.JAVA)
        )

        println("🚀 응답 시간 비교 테스트 시작 (${LocalDateTime.now()})")
        println("=" * 80)

        val results = runBlocking {
            endpoints.map { endpoint ->
                async(Dispatchers.IO) {
                    println("🔄 ${endpoint.name} 테스트 시작...")
                    measureApiResponseTime(endpoint)
                }
            }.awaitAll()
        }

        // 결과 분석 및 출력
        analyzeAndPrintResults(results)
    }

    private suspend fun measureApiResponseTime(endpoint: ApiEndpoint): ResponseTimeResult {
        val testConfigurations = listOf(
            TestConfig("단일 요청", 100, 1, 0),
            TestConfig("저부하", 200, 10, 100),
            TestConfig("중부하", 500, 25, 50),
            TestConfig("고부하", 1000, 50, 20)
        )

        val overallResult = ResponseTimeResult(endpoint, 0)

        testConfigurations.forEach { config ->
            println("  📈 ${endpoint.name} - ${config.name} 테스트...")
            val result = executeLoadTest(endpoint, config)
            
            // 결과 집계
            overallResult.successfulRequests.addAndGet(result.successfulRequests.get())
            overallResult.failedRequests.addAndGet(result.failedRequests.get())
            overallResult.totalResponseTime.addAndGet(result.totalResponseTime.get())
            overallResult.minResponseTime.updateAndGet { minOf(it, result.minResponseTime.get()) }
            overallResult.maxResponseTime.updateAndGet { maxOf(it, result.maxResponseTime.get()) }
            
            // 응답시간 분포 병합
            result.responseTimes.forEach { (time, count) ->
                overallResult.responseTimes.merge(time, count) { old, new -> old + new }
            }
            
            // 에러 메시지 병합
            result.errorMessages.forEach { (error, count) ->
                overallResult.errorMessages.merge(error, count) { old, new -> 
                    AtomicInteger(old.get() + new.get()) 
                }
            }
        }

        return overallResult.copy(totalRequests = testConfigurations.sumOf { it.requests })
    }

    private data class TestConfig(
        val name: String,
        val requests: Int,
        val concurrency: Int,
        val delayMs: Long
    )

    private suspend fun executeLoadTest(endpoint: ApiEndpoint, config: TestConfig): ResponseTimeResult {
        val result = ResponseTimeResult(endpoint, config.requests)

        // 동시 요청 실행
        val jobs = (1..config.requests).chunked(config.requests / config.concurrency).map { chunk ->
            async(Dispatchers.IO) {
                chunk.forEach { requestIndex ->
                    try {
                        val responseTime = when (endpoint.type) {
                            ApiType.MVC -> executeMvcRequest(endpoint.path, requestIndex)
                            ApiType.WEBFLUX -> executeWebFluxRequest(endpoint.path, requestIndex)
                        }
                        
                        // 성공 처리
                        result.successfulRequests.incrementAndGet()
                        result.totalResponseTime.addAndGet(responseTime)
                        result.minResponseTime.updateAndGet { minOf(it, responseTime) }
                        result.maxResponseTime.updateAndGet { maxOf(it, responseTime) }
                        
                        // 응답시간 분포 기록 (10ms 단위로 그룹화)
                        val timeGroup = (responseTime / 10) * 10
                        result.responseTimes.merge(timeGroup, 1) { old, new -> old + new }
                        
                    } catch (e: Exception) {
                        result.failedRequests.incrementAndGet()
                        val errorType = e.javaClass.simpleName
                        result.errorMessages.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
                    }
                    
                    // 요청 간 지연
                    if (config.delayMs > 0) {
                        delay(Random.nextLong(config.delayMs / 2, config.delayMs))
                    }
                }
            }
        }

        jobs.awaitAll()
        return result
    }

    private suspend fun executeMvcRequest(path: String, requestIndex: Int): Long {
        return measureTimeMillis {
            val request = createTestRequest(requestIndex)
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
            }
            val entity = HttpEntity(request, headers)
            
            restTemplate.postForObject("http://localhost:8080$path", entity, Any::class.java)
        }
    }

    private suspend fun executeWebFluxRequest(path: String, requestIndex: Int): Long {
        return measureTimeMillis {
            val request = createTestRequest(requestIndex)
            
            webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .awaitBody<Any>()
        }
    }

    private fun createTestRequest(requestIndex: Int): CreateReservationRequest {
        return CreateReservationRequest(
            guestName = "API Test Guest $requestIndex",
            roomNumber = "Room ${Random.nextInt(1, 101)}",
            checkInDate = "2024-12-${String.format("%02d", Random.nextInt(10, 28))}",
            checkOutDate = "2024-12-${String.format("%02d", Random.nextInt(28, 31))}",
            totalAmount = Random.nextDouble(100.0, 500.0)
        )
    }

    private fun analyzeAndPrintResults(results: List<ResponseTimeResult>) {
        println("\n" + "=" * 80)
        println("📊 API 응답 시간 비교 분석 결과")
        println("=" * 80)
        println("실행 시간: ${LocalDateTime.now()}")
        println()

        // 개별 결과 출력
        results.forEach { result ->
            printDetailedResult(result)
        }

        // 비교 분석
        printComparativeAnalysis(results)
        
        // 성능 권장사항
        printRecommendations(results)
    }

    private fun printDetailedResult(result: ResponseTimeResult) {
        val endpoint = result.endpoint
        println("🎯 ${endpoint.name} (${endpoint.type}, ${endpoint.language})")
        println("-" * 60)
        
        println("기본 통계:")
        println("  총 요청 수: ${result.totalRequests}")
        println("  성공 요청: ${result.successfulRequests.get()}")
        println("  실패 요청: ${result.failedRequests.get()}")
        println("  성공률: ${"%.2f".format(result.getSuccessRate())}%")
        
        println("\n응답 시간:")
        println("  평균: ${"%.2f".format(result.getAverageResponseTime())}ms")
        println("  최소: ${if (result.minResponseTime.get() == Long.MAX_VALUE) 0 else result.minResponseTime.get()}ms")
        println("  최대: ${result.maxResponseTime.get()}ms")
        
        // 백분위수 계산
        val percentiles = calculatePercentiles(result.responseTimes)
        println("  P50 (중간값): ${"%.0f".format(percentiles[50] ?: 0.0)}ms")
        println("  P95: ${"%.0f".format(percentiles[95] ?: 0.0)}ms")
        println("  P99: ${"%.0f".format(percentiles[99] ?: 0.0)}ms")
        
        // 응답시간 분포
        println("\n응답시간 분포:")
        val sortedDistribution = result.responseTimes.toSortedMap()
        sortedDistribution.forEach { (timeGroup, count) ->
            val percentage = (count.toDouble() / result.successfulRequests.get() * 100)
            if (percentage >= 5.0) { // 5% 이상인 구간만 표시
                println("  ${timeGroup}ms ~ ${timeGroup + 9}ms: ${count}회 (${"%.1f".format(percentage)}%)")
            }
        }
        
        // 에러 분석
        if (result.errorMessages.isNotEmpty()) {
            println("\n에러 분석:")
            result.errorMessages.forEach { (error, count) ->
                println("  $error: ${count.get()}회")
            }
        }
        
        println()
    }

    private fun calculatePercentiles(responseTimes: ConcurrentHashMap<Long, Int>): Map<Int, Double> {
        val sortedTimes = mutableListOf<Long>()
        responseTimes.forEach { (time, count) ->
            repeat(count) { sortedTimes.add(time) }
        }
        sortedTimes.sort()
        
        val percentiles = mutableMapOf<Int, Double>()
        listOf(50, 95, 99).forEach { percentile ->
            if (sortedTimes.isNotEmpty()) {
                val index = ((percentile / 100.0) * (sortedTimes.size - 1)).toInt()
                percentiles[percentile] = sortedTimes[index.coerceIn(0, sortedTimes.size - 1)].toDouble()
            }
        }
        
        return percentiles
    }

    private fun printComparativeAnalysis(results: List<ResponseTimeResult>) {
        println("🔍 비교 분석")
        println("-" * 60)
        
        // MVC vs WebFlux 비교
        val mvcResults = results.filter { it.endpoint.type == ApiType.MVC }
        val webFluxResults = results.filter { it.endpoint.type == ApiType.WEBFLUX }
        
        if (mvcResults.isNotEmpty() && webFluxResults.isNotEmpty()) {
            val mvcAvgTime = mvcResults.map { it.getAverageResponseTime() }.average()
            val webFluxAvgTime = webFluxResults.map { it.getAverageResponseTime() }.average()
            val mvcSuccessRate = mvcResults.map { it.getSuccessRate() }.average()
            val webFluxSuccessRate = webFluxResults.map { it.getSuccessRate() }.average()
            
            println("📈 MVC vs WebFlux:")
            println("  평균 응답시간 - MVC: ${"%.2f".format(mvcAvgTime)}ms, WebFlux: ${"%.2f".format(webFluxAvgTime)}ms")
            println("  성능 차이: ${if (mvcAvgTime > webFluxAvgTime) "WebFlux가 ${"%.1f".format((mvcAvgTime - webFluxAvgTime) / mvcAvgTime * 100)}% 빠름" else "MVC가 ${"%.1f".format((webFluxAvgTime - mvcAvgTime) / webFluxAvgTime * 100)}% 빠름"}")
            println("  성공률 - MVC: ${"%.2f".format(mvcSuccessRate)}%, WebFlux: ${"%.2f".format(webFluxSuccessRate)}%")
        }
        
        // Java vs Kotlin 비교
        val kotlinResults = results.filter { it.endpoint.language == Language.KOTLIN }
        val javaResults = results.filter { it.endpoint.language == Language.JAVA }
        
        if (kotlinResults.isNotEmpty() && javaResults.isNotEmpty()) {
            val kotlinAvgTime = kotlinResults.map { it.getAverageResponseTime() }.average()
            val javaAvgTime = javaResults.map { it.getAverageResponseTime() }.average()
            
            println("\n🔤 Kotlin vs Java:")
            println("  평균 응답시간 - Kotlin: ${"%.2f".format(kotlinAvgTime)}ms, Java: ${"%.2f".format(javaAvgTime)}ms")
            println("  성능 차이: ${if (kotlinAvgTime > javaAvgTime) "Java가 ${"%.1f".format((kotlinAvgTime - javaAvgTime) / kotlinAvgTime * 100)}% 빠름" else "Kotlin이 ${"%.1f".format((javaAvgTime - kotlinAvgTime) / javaAvgTime * 100)}% 빠름"}")
        }
        
        // 전체 순위
        println("\n🏆 전체 성능 순위 (평균 응답시간 기준):")
        results.sortedBy { it.getAverageResponseTime() }.forEachIndexed { index, result ->
            println("  ${index + 1}위: ${result.endpoint.name} - ${"%.2f".format(result.getAverageResponseTime())}ms")
        }
        
        println()
    }

    private fun printRecommendations(results: List<ResponseTimeResult>) {
        println("💡 성능 최적화 권장사항")
        println("-" * 60)
        
        results.forEach { result ->
            val avgTime = result.getAverageResponseTime()
            val successRate = result.getSuccessRate()
            
            println("${result.endpoint.name}:")
            
            when {
                avgTime > 200 -> println("  ⚠️ 높은 응답시간: 데이터베이스 쿼리 최적화 또는 캐싱 적용 권장")
                avgTime > 100 -> println("  📊 보통 응답시간: 인덱스 최적화 검토 권장")
                else -> println("  ✅ 우수한 응답시간")
            }
            
            when {
                successRate < 95 -> println("  ❌ 낮은 성공률: 에러 처리 로직 및 타임아웃 설정 검토 필요")
                successRate < 99 -> println("  ⚠️ 보통 성공률: 예외 처리 개선 권장")
                else -> println("  ✅ 우수한 안정성")
            }
            
            // 특정 기술별 권장사항
            when (result.endpoint.type) {
                ApiType.MVC -> {
                    if (avgTime > 150) {
                        println("  💡 MVC 최적화: 스레드 풀 크기 조정, 연결 풀 최적화 고려")
                    }
                }
                ApiType.WEBFLUX -> {
                    if (avgTime > 150) {
                        println("  💡 WebFlux 최적화: 백프레셔 처리, 논블로킹 I/O 활용 검토")
                    }
                }
            }
            
            println()
        }
        
        println("🎯 전반적 권장사항:")
        println("- 지속적인 모니터링을 통한 성능 추이 관찰")
        println("- 프로덕션 환경에서의 실제 부하 테스트 수행")
        println("- APM 도구 연동으로 상세한 성능 분석")
        println("- 캐시 전략 및 데이터베이스 최적화 적용")
        
        println("\n" + "=" * 80)
    }
}