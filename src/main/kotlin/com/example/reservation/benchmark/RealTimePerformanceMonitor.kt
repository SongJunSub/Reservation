package com.example.reservation.benchmark

import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * 실시간 성능 모니터링 도구
 * API 엔드포인트의 실시간 성능 지표를 모니터링하고 시각화
 */
@Component
class RealTimePerformanceMonitor : CommandLineRunner {

    private val webClient = WebClient.builder()
        .baseUrl("http://localhost:8080")
        .build()

    private val memoryBean = ManagementFactory.getMemoryMXBean()
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()

    data class PerformanceMetrics(
        val timestamp: LocalDateTime,
        val endpoint: String,
        val responseTime: Long,
        val success: Boolean,
        val memoryUsed: Long,
        val cpuLoad: Double
    )

    data class EndpointStats(
        val endpoint: String,
        val totalRequests: AtomicInteger = AtomicInteger(0),
        val successfulRequests: AtomicInteger = AtomicInteger(0),
        val totalResponseTime: AtomicLong = AtomicLong(0),
        val minResponseTime: AtomicLong = AtomicLong(Long.MAX_VALUE),
        val maxResponseTime: AtomicLong = AtomicLong(0),
        val recentMetrics: MutableList<PerformanceMetrics> = mutableListOf()
    ) {
        fun addMetric(metric: PerformanceMetrics) {
            totalRequests.incrementAndGet()
            if (metric.success) {
                successfulRequests.incrementAndGet()
                totalResponseTime.addAndGet(metric.responseTime)
                minResponseTime.updateAndGet { minOf(it, metric.responseTime) }
                maxResponseTime.updateAndGet { maxOf(it, metric.responseTime) }
            }
            
            synchronized(recentMetrics) {
                recentMetrics.add(metric)
                // 최근 100개만 유지
                if (recentMetrics.size > 100) {
                    recentMetrics.removeAt(0)
                }
            }
        }
        
        fun getAverageResponseTime(): Double = 
            if (successfulRequests.get() > 0) totalResponseTime.get().toDouble() / successfulRequests.get() 
            else 0.0
            
        fun getSuccessRate(): Double = 
            if (totalRequests.get() > 0) successfulRequests.get().toDouble() / totalRequests.get() * 100 
            else 0.0
    }

    private val endpointStats = ConcurrentHashMap<String, EndpointStats>()
    private var monitoringActive = false

    override fun run(vararg args: String?) {
        if (args.contains("--real-time-monitor")) {
            println("📊 실시간 성능 모니터링 시작...")
            startRealTimeMonitoring()
        }
    }

    fun startRealTimeMonitoring() {
        val endpoints = listOf(
            "/api/reservations",
            "/api/webflux/reservations", 
            "/api/java/reservations",
            "/api/webflux-java/reservations"
        )

        endpoints.forEach { endpoint ->
            endpointStats[endpoint] = EndpointStats(endpoint)
        }

        monitoringActive = true
        
        runBlocking {
            // 모니터링 코루틴들 시작
            val monitoringJobs = listOf(
                // 실시간 요청 전송
                async { sendContinuousRequests(endpoints) },
                // 실시간 통계 출력
                async { displayRealTimeStats() },
                // 시스템 리소스 모니터링
                async { monitorSystemResources() }
            )

            println("🚀 실시간 모니터링 시작됨. Ctrl+C로 종료하세요.")
            println("=" * 80)

            try {
                monitoringJobs.awaitAll()
            } catch (e: CancellationException) {
                println("\n모니터링 중단됨.")
            } finally {
                monitoringActive = false
                printFinalSummary()
            }
        }
    }

    private suspend fun sendContinuousRequests(endpoints: List<String>) {
        val requestInterval = 1000L // 1초마다 요청
        
        while (monitoringActive) {
            endpoints.forEach { endpoint ->
                launch {
                    try {
                        val responseTime = measureTimeMillis {
                            val response = webClient.get()
                                .uri("$endpoint/health")
                                .retrieve()
                                .awaitBodyOrNull<Any>()
                        }
                        
                        val metric = PerformanceMetrics(
                            timestamp = LocalDateTime.now(),
                            endpoint = endpoint,
                            responseTime = responseTime,
                            success = true,
                            memoryUsed = getMemoryUsage(),
                            cpuLoad = getCpuLoad()
                        )
                        
                        endpointStats[endpoint]?.addMetric(metric)
                        
                    } catch (e: Exception) {
                        val metric = PerformanceMetrics(
                            timestamp = LocalDateTime.now(),
                            endpoint = endpoint,
                            responseTime = 0,
                            success = false,
                            memoryUsed = getMemoryUsage(),
                            cpuLoad = getCpuLoad()
                        )
                        
                        endpointStats[endpoint]?.addMetric(metric)
                    }
                }
            }
            
            delay(requestInterval)
        }
    }

    private suspend fun displayRealTimeStats() {
        while (monitoringActive) {
            delay(5000) // 5초마다 통계 출력
            clearConsole()
            printCurrentStats()
        }
    }

    private suspend fun monitorSystemResources() {
        while (monitoringActive) {
            delay(2000) // 2초마다 시스템 리소스 체크
            // 시스템 리소스 정보는 개별 메트릭에 포함되므로 별도 처리 없음
        }
    }

    private fun clearConsole() {
        // ANSI escape sequence로 콘솔 클리어
        print("\u001B[2J\u001B[H")
    }

    private fun printCurrentStats() {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        
        println("🔄 실시간 성능 모니터링 - $now")
        println("=" * 100)
        
        // 헤더
        println("%-25s %-8s %-8s %-10s %-10s %-10s %-12s %-10s".format(
            "엔드포인트", "요청수", "성공률", "평균응답", "최소응답", "최대응답", "메모리(MB)", "CPU%"
        ))
        println("-" * 100)
        
        endpointStats.values.sortedBy { it.endpoint }.forEach { stats ->
            val recentMemory = stats.recentMetrics.lastOrNull()?.memoryUsed?.div(1024 * 1024) ?: 0
            val recentCpu = stats.recentMetrics.lastOrNull()?.cpuLoad ?: 0.0
            
            println("%-25s %-8d %-8s %-10s %-10s %-10s %-12d %-10s".format(
                stats.endpoint.substringAfterLast("/"),
                stats.totalRequests.get(),
                "${"%.1f".format(stats.getSuccessRate())}%",
                "${"%.0f".format(stats.getAverageResponseTime())}ms",
                "${if (stats.minResponseTime.get() == Long.MAX_VALUE) 0 else stats.minResponseTime.get()}ms",
                "${stats.maxResponseTime.get()}ms",
                recentMemory,
                "${"%.1f".format(recentCpu * 100)}%"
            ))
        }
        
        println("-" * 100)
        
        // 실시간 차트 (간단한 ASCII 차트)
        printResponseTimeChart()
        
        // 시스템 정보
        printSystemInfo()
        
        println("\n💡 Ctrl+C를 눌러 모니터링을 중단하세요.")
    }

    private fun printResponseTimeChart() {
        println("\n📈 응답시간 추이 (최근 20회)")
        
        endpointStats.values.forEach { stats ->
            val recentTimes = synchronized(stats.recentMetrics) {
                stats.recentMetrics.takeLast(20)
                    .filter { it.success }
                    .map { it.responseTime }
            }
            
            if (recentTimes.isNotEmpty()) {
                val maxTime = recentTimes.maxOrNull() ?: 0
                val scale = if (maxTime > 0) 50.0 / maxTime else 1.0
                
                println("${stats.endpoint.substringAfterLast("/")}:")
                print("  ")
                recentTimes.forEach { time ->
                    val barLength = (time * scale).toInt().coerceIn(1, 50)
                    val bar = when {
                        time < 50 -> "▁"
                        time < 100 -> "▃"
                        time < 200 -> "▅"
                        else -> "▇"
                    }
                    print(bar)
                }
                println(" (${recentTimes.last()}ms)")
            }
        }
    }

    private fun printSystemInfo() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory() / 1024 / 1024
        val freeMemory = runtime.freeMemory() / 1024 / 1024
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory() / 1024 / 1024
        
        println("\n🖥️ 시스템 리소스:")
        println("메모리: ${usedMemory}MB / ${maxMemory}MB (사용률: ${"%.1f".format(usedMemory.toDouble() / maxMemory * 100)}%)")
        println("JVM 가동 시간: ${runtimeBean.uptime / 1000}초")
        println("활성 스레드: ${Thread.activeCount()}")
    }

    private fun printFinalSummary() {
        println("\n" + "=" * 80)
        println("📊 최종 성능 모니터링 결과")
        println("=" * 80)
        
        endpointStats.values.sortedBy { it.getAverageResponseTime() }.forEach { stats ->
            println("\n🎯 ${stats.endpoint}")
            println("  총 요청 수: ${stats.totalRequests.get()}")
            println("  성공 요청: ${stats.successfulRequests.get()}")
            println("  성공률: ${"%.2f".format(stats.getSuccessRate())}%")
            println("  평균 응답시간: ${"%.2f".format(stats.getAverageResponseTime())}ms")
            println("  최소 응답시간: ${if (stats.minResponseTime.get() == Long.MAX_VALUE) 0 else stats.minResponseTime.get()}ms")
            println("  최대 응답시간: ${stats.maxResponseTime.get()}ms")
            
            // 성능 등급 평가
            val avgTime = stats.getAverageResponseTime()
            val grade = when {
                avgTime < 50 -> "A+ (우수)"
                avgTime < 100 -> "A (양호)"
                avgTime < 200 -> "B (보통)"
                avgTime < 500 -> "C (개선 필요)"
                else -> "D (매우 개선 필요)"
            }
            println("  성능 등급: $grade")
        }
        
        // 전체 성능 비교
        println("\n🏆 성능 순위:")
        endpointStats.values.sortedBy { it.getAverageResponseTime() }.forEachIndexed { index, stats ->
            println("  ${index + 1}위: ${stats.endpoint} (${"%.2f".format(stats.getAverageResponseTime())}ms)")
        }
        
        println("\n💡 모니터링 완료!")
    }

    private fun getMemoryUsage(): Long {
        return memoryBean.heapMemoryUsage.used
    }

    private fun getCpuLoad(): Double {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        return if (osBean is com.sun.management.OperatingSystemMXBean) {
            osBean.cpuLoad
        } else {
            0.0
        }
    }
}