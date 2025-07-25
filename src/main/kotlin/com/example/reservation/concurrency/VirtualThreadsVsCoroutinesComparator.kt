package com.example.reservation.concurrency

import com.example.reservation.controller.CreateReservationRequest
import com.example.reservation.service.ReservationService
import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Virtual Threads vs Kotlin Coroutines 성능 비교 도구
 * Java 21의 Virtual Threads와 Kotlin Coroutines의 성능과 특성을 비교 분석
 */
@Component
class VirtualThreadsVsCoroutinesComparator(
    private val reservationService: ReservationService
) : CommandLineRunner {

    data class ConcurrencyTestResult(
        val testName: String,
        val technology: String,
        val taskCount: Int,
        val executionTimeMs: Long,
        val successfulTasks: Int,
        val failedTasks: Int,
        val averageTaskTimeMs: Double,
        val tasksPerSecond: Double,
        val memoryUsedMB: Long,
        val threadCount: Int,
        val cpuUsagePercent: Double
    ) {
        fun getSuccessRate(): Double = (successfulTasks.toDouble() / taskCount) * 100
    }

    data class ResourceUsage(
        val memoryUsedMB: Long,
        val activeThreads: Int,
        val cpuUsage: Double
    )

    override fun run(vararg args: String?) {
        if (args.contains("--concurrency-comparison")) {
            println("🚀 Virtual Threads vs Kotlin Coroutines 비교 분석 시작...")
            runConcurrencyComparison()
        }
    }

    fun runConcurrencyComparison() {
        println("⚡ Virtual Threads vs Kotlin Coroutines 성능 비교")
        println("=" * 80)
        println("분석 시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        println()

        val testScenarios = listOf(
            TestScenario("간단한 I/O 작업", 1000, TaskType.IO_BOUND),
            TestScenario("CPU 집약적 작업", 500, TaskType.CPU_BOUND),
            TestScenario("혼합 작업", 750, TaskType.MIXED),
            TestScenario("대용량 동시 작업", 5000, TaskType.IO_BOUND),
            TestScenario("장시간 실행 작업", 100, TaskType.LONG_RUNNING)
        )

        val results = mutableListOf<ConcurrencyTestResult>()

        testScenarios.forEach { scenario ->
            println("🔄 ${scenario.name} 테스트 시작...")
            println("-" * 50)

            // Virtual Threads 테스트
            val virtualThreadsResult = testVirtualThreads(scenario)
            results.add(virtualThreadsResult)
            printTestResult(virtualThreadsResult)

            // 메모리 정리 및 대기
            System.gc()
            Thread.sleep(2000)

            // Kotlin Coroutines 테스트
            val coroutinesResult = testKotlinCoroutines(scenario)
            results.add(coroutinesResult)
            printTestResult(coroutinesResult)

            println()
            
            // 테스트 간 간격
            Thread.sleep(3000)
        }

        // 종합 분석 및 비교
        analyzeAndPrintComparison(results)
    }

    private fun testVirtualThreads(scenario: TestScenario): ConcurrencyTestResult {
        val startMemory = getMemoryUsage()
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val totalTaskTime = AtomicLong(0)

        val executionTime = measureTimeMillis {
            // Virtual Thread Executor 생성
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val futures = mutableListOf<CompletableFuture<Void>>()

                repeat(scenario.taskCount) { taskId ->
                    val future = CompletableFuture.runAsync({
                        try {
                            val taskTime = measureTimeMillis {
                                executeTask(taskId, scenario.taskType)
                            }
                            totalTaskTime.addAndGet(taskTime)
                            successCount.incrementAndGet()
                        } catch (e: Exception) {
                            failCount.incrementAndGet()
                        }
                    }, executor)
                    futures.add(future)
                }

                // 모든 작업 완료 대기
                CompletableFuture.allOf(*futures.toTypedArray()).join()
            }
        }

        val endMemory = getMemoryUsage()
        val resourceUsage = getCurrentResourceUsage()

        return ConcurrencyTestResult(
            testName = scenario.name,
            technology = "Virtual Threads",
            taskCount = scenario.taskCount,
            executionTimeMs = executionTime,
            successfulTasks = successCount.get(),
            failedTasks = failCount.get(),
            averageTaskTimeMs = if (successCount.get() > 0) totalTaskTime.get().toDouble() / successCount.get() else 0.0,
            tasksPerSecond = if (executionTime > 0) (successCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            threadCount = resourceUsage.activeThreads,
            cpuUsagePercent = resourceUsage.cpuUsage
        )
    }

    private fun testKotlinCoroutines(scenario: TestScenario): ConcurrencyTestResult {
        val startMemory = getMemoryUsage()
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val totalTaskTime = AtomicLong(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                // 동시성 제한을 위한 세마포어 (Virtual Threads와 공정한 비교)
                val semaphore = Semaphore(200)
                
                val jobs = (1..scenario.taskCount).map { taskId ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val taskTime = measureTimeMillis {
                                    executeTaskSuspend(taskId, scenario.taskType)
                                }
                                totalTaskTime.addAndGet(taskTime)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }

                // 모든 코루틴 완료 대기
                jobs.awaitAll()
            }
        }

        val endMemory = getMemoryUsage()
        val resourceUsage = getCurrentResourceUsage()

        return ConcurrencyTestResult(
            testName = scenario.name,
            technology = "Kotlin Coroutines",
            taskCount = scenario.taskCount,
            executionTimeMs = executionTime,
            successfulTasks = successCount.get(),
            failedTasks = failCount.get(),
            averageTaskTimeMs = if (successCount.get() > 0) totalTaskTime.get().toDouble() / successCount.get() else 0.0,
            tasksPerSecond = if (executionTime > 0) (successCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            threadCount = resourceUsage.activeThreads,
            cpuUsagePercent = resourceUsage.cpuUsage
        )
    }

    private fun executeTask(taskId: Int, taskType: TaskType) {
        when (taskType) {
            TaskType.IO_BOUND -> simulateIOTask(taskId)
            TaskType.CPU_BOUND -> simulateCPUTask(taskId)
            TaskType.MIXED -> {
                simulateIOTask(taskId)
                simulateCPUTask(taskId)
            }
            TaskType.LONG_RUNNING -> simulateLongRunningTask(taskId)
        }
    }

    private suspend fun executeTaskSuspend(taskId: Int, taskType: TaskType) {
        when (taskType) {
            TaskType.IO_BOUND -> simulateIOTaskSuspend(taskId)
            TaskType.CPU_BOUND -> simulateCPUTask(taskId) // CPU 작업은 동일
            TaskType.MIXED -> {
                simulateIOTaskSuspend(taskId)
                simulateCPUTask(taskId)
            }
            TaskType.LONG_RUNNING -> simulateLongRunningTaskSuspend(taskId)
        }
    }

    private fun simulateIOTask(taskId: Int) {
        // I/O 작업 시뮬레이션 (블로킹)
        Thread.sleep(Random.nextLong(10, 100))
        
        // 실제 서비스 호출 시뮬레이션
        try {
            val request = CreateReservationRequest(
                guestName = "VirtualThread Guest $taskId",
                roomNumber = "Room ${taskId % 100 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 200.0 + (taskId % 100)
            )
            // 실제 서비스 호출은 주석 처리 (테스트 목적)
            // reservationService.create(request)
        } catch (e: Exception) {
            // 예외 무시 (테스트 목적)
        }
    }

    private suspend fun simulateIOTaskSuspend(taskId: Int) {
        // I/O 작업 시뮬레이션 (논블로킹)
        delay(Random.nextLong(10, 100))
        
        // 실제 서비스 호출 시뮬레이션
        try {
            val request = CreateReservationRequest(
                guestName = "Coroutine Guest $taskId",
                roomNumber = "Room ${taskId % 100 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 200.0 + (taskId % 100)
            )
            // 실제 서비스 호출은 주석 처리 (테스트 목적)
            // reservationService.create(request)
        } catch (e: Exception) {
            // 예외 무시 (테스트 목적)
        }
    }

    private fun simulateCPUTask(taskId: Int) {
        // CPU 집약적 작업 시뮬레이션
        var result = 0
        repeat(Random.nextInt(10000, 50000)) {
            result += (it * taskId) % 1000
        }
        
        // 결과 사용 (최적화 방지)
        if (result < 0) println("Unexpected result: $result")
    }

    private fun simulateLongRunningTask(taskId: Int) {
        // 장시간 실행 작업 시뮬레이션
        Thread.sleep(Random.nextLong(500, 2000))
        simulateCPUTask(taskId)
    }

    private suspend fun simulateLongRunningTaskSuspend(taskId: Int) {
        // 장시간 실행 작업 시뮬레이션
        delay(Random.nextLong(500, 2000))
        simulateCPUTask(taskId)
    }

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun getCurrentResourceUsage(): ResourceUsage {
        val runtime = Runtime.getRuntime()
        val memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val activeThreads = Thread.activeCount()
        
        // CPU 사용률 근사치 (실제로는 더 정교한 측정 필요)
        val cpuUsage = try {
            val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                osBean.cpuLoad * 100
            } else 0.0
        } catch (e: Exception) {
            0.0
        }

        return ResourceUsage(memoryUsed, activeThreads, cpuUsage)
    }

    private fun printTestResult(result: ConcurrencyTestResult) {
        println("📊 ${result.technology} 결과:")
        println("  작업 수: ${result.taskCount}")
        println("  실행 시간: ${result.executionTimeMs}ms")
        println("  성공률: ${"%.1f".format(result.getSuccessRate())}% (${result.successfulTasks}/${result.taskCount})")
        println("  처리량: ${"%.1f".format(result.tasksPerSecond)} tasks/sec")
        println("  평균 작업 시간: ${"%.2f".format(result.averageTaskTimeMs)}ms")
        println("  메모리 사용: ${result.memoryUsedMB}MB")
        println("  활성 스레드: ${result.threadCount}")
        println("  CPU 사용률: ${"%.1f".format(result.cpuUsagePercent)}%")
        
        // 성능 등급 평가
        val performanceGrade = when {
            result.tasksPerSecond > 1000 -> "A+"
            result.tasksPerSecond > 500 -> "A"
            result.tasksPerSecond > 200 -> "B"
            result.tasksPerSecond > 100 -> "C"
            else -> "D"
        }
        println("  성능 등급: $performanceGrade")
        println()
    }

    private fun analyzeAndPrintComparison(results: List<ConcurrencyTestResult>) {
        println("🔍 종합 비교 분석")
        println("=" * 80)

        // 기술별 결과 그룹화
        val virtualThreadsResults = results.filter { it.technology == "Virtual Threads" }
        val coroutinesResults = results.filter { it.technology == "Kotlin Coroutines" }

        // 각 테스트별 비교
        val testNames = results.map { it.testName }.distinct()
        
        testNames.forEach { testName ->
            val vtResult = virtualThreadsResults.find { it.testName == testName }
            val corResult = coroutinesResults.find { it.testName == testName }
            
            if (vtResult != null && corResult != null) {
                println("📈 $testName 비교:")
                compareResults(vtResult, corResult)
                println()
            }
        }

        // 전체 성능 요약
        printOverallSummary(virtualThreadsResults, coroutinesResults)
        
        // 기술 선택 가이드
        printTechnologySelectionGuide()
    }

    private fun compareResults(vtResult: ConcurrencyTestResult, corResult: ConcurrencyTestResult) {
        println("  처리량 비교:")
        if (vtResult.tasksPerSecond > corResult.tasksPerSecond) {
            val improvement = ((vtResult.tasksPerSecond - corResult.tasksPerSecond) / corResult.tasksPerSecond) * 100
            println("    Virtual Threads가 ${"%.1f".format(improvement)}% 빠름 (${vtResult.tasksPerSecond.toInt()} vs ${corResult.tasksPerSecond.toInt()} tasks/sec)")
        } else {
            val improvement = ((corResult.tasksPerSecond - vtResult.tasksPerSecond) / vtResult.tasksPerSecond) * 100
            println("    Kotlin Coroutines가 ${"%.1f".format(improvement)}% 빠름 (${corResult.tasksPerSecond.toInt()} vs ${vtResult.tasksPerSecond.toInt()} tasks/sec)")
        }

        println("  메모리 사용 비교:")
        if (vtResult.memoryUsedMB < corResult.memoryUsedMB) {
            val savings = corResult.memoryUsedMB - vtResult.memoryUsedMB
            println("    Virtual Threads가 ${savings}MB 적게 사용")
        } else {
            val savings = vtResult.memoryUsedMB - corResult.memoryUsedMB
            println("    Kotlin Coroutines가 ${savings}MB 적게 사용")
        }

        println("  실행 시간 비교:")
        if (vtResult.executionTimeMs < corResult.executionTimeMs) {
            val improvement = ((corResult.executionTimeMs - vtResult.executionTimeMs).toDouble() / corResult.executionTimeMs) * 100
            println("    Virtual Threads가 ${"%.1f".format(improvement)}% 빠름 (${vtResult.executionTimeMs}ms vs ${corResult.executionTimeMs}ms)")
        } else {
            val improvement = ((vtResult.executionTimeMs - corResult.executionTimeMs).toDouble() / vtResult.executionTimeMs) * 100
            println("    Kotlin Coroutines가 ${"%.1f".format(improvement)}% 빠름 (${corResult.executionTimeMs}ms vs ${vtResult.executionTimeMs}ms)")
        }
    }

    private fun printOverallSummary(vtResults: List<ConcurrencyTestResult>, corResults: List<ConcurrencyTestResult>) {
        println("📋 전체 성능 요약")
        println("-" * 40)

        val vtAvgThroughput = vtResults.map { it.tasksPerSecond }.average()
        val corAvgThroughput = corResults.map { it.tasksPerSecond }.average()
        val vtAvgMemory = vtResults.map { it.memoryUsedMB }.average()
        val corAvgMemory = corResults.map { it.memoryUsedMB }.average()
        val vtAvgTime = vtResults.map { it.executionTimeMs }.average()
        val corAvgTime = corResults.map { it.executionTimeMs }.average()

        println("Virtual Threads 평균:")
        println("  처리량: ${"%.1f".format(vtAvgThroughput)} tasks/sec")
        println("  메모리 사용: ${"%.1f".format(vtAvgMemory)}MB")
        println("  실행 시간: ${"%.0f".format(vtAvgTime)}ms")

        println("\nKotlin Coroutines 평균:")
        println("  처리량: ${"%.1f".format(corAvgThroughput)} tasks/sec")
        println("  메모리 사용: ${"%.1f".format(corAvgMemory)}MB")
        println("  실행 시간: ${"%.0f".format(corAvgTime)}ms")

        // 승자 결정
        val vtWins = (if (vtAvgThroughput > corAvgThroughput) 1 else 0) +
                     (if (vtAvgMemory < corAvgMemory) 1 else 0) +
                     (if (vtAvgTime < corAvgTime) 1 else 0)

        println("\n🏆 전체 우승자:")
        when {
            vtWins > 1 -> println("  Virtual Threads (3개 지표 중 ${vtWins}개 우위)")
            vtWins < 1 -> println("  Kotlin Coroutines (3개 지표 중 ${3 - vtWins}개 우위)")
            else -> println("  무승부 (각각 장단점 보유)")
        }
        println()
    }

    private fun printTechnologySelectionGuide() {
        println("🎯 기술 선택 가이드")
        println("-" * 40)
        
        println("Virtual Threads 선택 기준:")
        println("  ✅ 기존 blocking I/O 코드 마이그레이션")
        println("  ✅ 대량의 동시 I/O 작업")
        println("  ✅ 단순한 동시성 모델 선호")
        println("  ✅ 디버깅 편의성 중시")
        println("  ✅ Java 생태계와의 호환성")
        
        println("\nKotlin Coroutines 선택 기준:")
        println("  ✅ 복잡한 비동기 플로우 제어")
        println("  ✅ 함수형 프로그래밍 스타일")
        println("  ✅ 구조화된 동시성")
        println("  ✅ 백프레셔 및 플로우 제어")
        println("  ✅ Kotlin 생태계 활용")
        
        println("\n⚠️ 고려사항:")
        println("  - Virtual Threads: Java 21+ 필요")
        println("  - Coroutines: Kotlin 런타임 의존성")
        println("  - 성능은 작업 유형에 따라 다름")
        println("  - 팀의 기술 숙련도 고려 필요")
        
        println("\n" + "=" * 80)
    }

    // 테스트 시나리오 관련 클래스들
    private data class TestScenario(
        val name: String,
        val taskCount: Int,
        val taskType: TaskType
    )

    private enum class TaskType {
        IO_BOUND,       // I/O 집약적
        CPU_BOUND,      // CPU 집약적
        MIXED,          // 혼합
        LONG_RUNNING    // 장시간 실행
    }

    // 세마포어 확장 함수 (Kotlin Coroutines용)
    private suspend fun <T> Semaphore.withPermit(action: suspend () -> T): T {
        acquire()
        try {
            return action()
        } finally {
            release()
        }
    }
}