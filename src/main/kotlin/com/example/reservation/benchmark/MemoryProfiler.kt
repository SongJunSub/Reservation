package com.example.reservation.benchmark

import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.lang.management.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * 메모리 사용량 프로파일링 도구
 * JVM 메모리 사용 패턴을 분석하고 언어/프레임워크별 메모리 효율성을 비교
 */
@Component
class MemoryProfiler : CommandLineRunner {

    private val memoryBean = ManagementFactory.getMemoryMXBean()
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()

    data class MemorySnapshot(
        val timestamp: LocalDateTime,
        val heapUsed: Long,
        val heapMax: Long,
        val nonHeapUsed: Long,
        val nonHeapMax: Long,
        val youngGenUsed: Long = 0,
        val oldGenUsed: Long = 0,
        val metaspaceUsed: Long = 0,
        val threadCount: Int,
        val peakThreadCount: Int,
        val totalGcCount: Long,
        val totalGcTime: Long
    ) {
        fun getHeapUtilization(): Double = if (heapMax > 0) (heapUsed.toDouble() / heapMax) * 100 else 0.0
        fun getNonHeapUtilization(): Double = if (nonHeapMax > 0) (nonHeapUsed.toDouble() / nonHeapMax) * 100 else 0.0
        fun toMB(bytes: Long): Long = bytes / (1024 * 1024)
    }

    data class MemoryProfileResult(
        val testName: String,
        val snapshots: MutableList<MemorySnapshot> = mutableListOf(),
        val peakHeapUsage: Long = 0,
        val averageHeapUsage: Long = 0,
        val memoryLeaks: MutableList<String> = mutableListOf(),
        val gcEfficiency: Double = 0.0
    ) {
        fun addSnapshot(snapshot: MemorySnapshot) {
            snapshots.add(snapshot)
        }
        
        fun calculateStats(): MemoryProfileResult {
            if (snapshots.isEmpty()) return this
            
            val avgHeap = snapshots.map { it.heapUsed }.average().toLong()
            val peakHeap = snapshots.maxOfOrNull { it.heapUsed } ?: 0
            
            // GC 효율성 계산 (수집된 메모리 대비 소요 시간)
            val totalGcTime = snapshots.lastOrNull()?.totalGcTime ?: 0
            val totalRuntime = snapshots.size * 1000L // 대략적인 실행 시간
            val gcEfficiency = if (totalGcTime > 0) {
                ((totalRuntime - totalGcTime).toDouble() / totalRuntime) * 100
            } else 100.0
            
            return copy(
                averageHeapUsage = avgHeap,
                peakHeapUsage = peakHeap,
                gcEfficiency = gcEfficiency
            )
        }
    }

    private val profilingResults = ConcurrentHashMap<String, MemoryProfileResult>()
    private var profilingActive = false

    override fun run(vararg args: String?) {
        if (args.contains("--memory-profiling")) {
            println("🔍 메모리 프로파일링 시작...")
            runMemoryProfiling()
        }
    }

    fun runMemoryProfiling() {
        println("🚀 메모리 사용량 프로파일링 시작")
        println("=" * 80)
        
        runBlocking {
            // 다양한 시나리오에서 메모리 사용량 측정
            val scenarios = listOf(
                "기본 메모리 베이스라인" to { measureBaselineMemory() },
                "단일 요청 처리" to { measureSingleRequestMemory() },
                "동시 요청 처리" to { measureConcurrentRequestMemory() },
                "대용량 데이터 처리" to { measureBulkDataMemory() },
                "장시간 실행" to { measureLongRunningMemory() }
            )
            
            scenarios.forEach { (name, scenario) ->
                println("\n📊 $name 메모리 프로파일링...")
                scenario()
                delay(2000) // 시나리오 간 간격
            }
            
            // 결과 분석 및 출력
            analyzeAndPrintResults()
        }
    }

    private suspend fun measureBaselineMemory() {
        val result = MemoryProfileResult("기본 메모리 베이스라인")
        profilingResults["baseline"] = result
        
        // 시스템 안정화 대기
        delay(1000)
        System.gc()
        delay(1000)
        
        // 30초간 기본 상태 메모리 모니터링
        repeat(30) {
            val snapshot = captureMemorySnapshot()
            result.addSnapshot(snapshot)
            delay(1000)
        }
    }

    private suspend fun measureSingleRequestMemory() {
        val result = MemoryProfileResult("단일 요청 처리")
        profilingResults["single_request"] = result
        
        System.gc()
        delay(1000)
        
        // 단일 요청 시뮬레이션
        repeat(100) { index ->
            val beforeSnapshot = captureMemorySnapshot()
            
            // 메모리 사용 시뮬레이션
            val data = simulateRequestProcessing(index)
            
            val afterSnapshot = captureMemorySnapshot()
            result.addSnapshot(afterSnapshot)
            
            // 메모리 증가 패턴 분석
            if (afterSnapshot.heapUsed > beforeSnapshot.heapUsed + 10 * 1024 * 1024) {
                result.memoryLeaks.add("요청 $index: 메모리 증가 ${(afterSnapshot.heapUsed - beforeSnapshot.heapUsed) / 1024 / 1024}MB")
            }
            
            delay(100)
        }
    }

    private suspend fun measureConcurrentRequestMemory() {
        val result = MemoryProfileResult("동시 요청 처리")
        profilingResults["concurrent_request"] = result
        
        System.gc()
        delay(1000)
        
        // 동시 요청 시뮬레이션
        val concurrentJobs = (1..50).map { requestId ->
            async(Dispatchers.IO) {
                repeat(20) {
                    simulateRequestProcessing(requestId * 100 + it)
                    delay(50)
                }
            }
        }
        
        // 메모리 모니터링
        val monitoringJob = async {
            repeat(60) {
                val snapshot = captureMemorySnapshot()
                result.addSnapshot(snapshot)
                delay(1000)
            }
        }
        
        concurrentJobs.awaitAll()
        monitoringJob.cancel()
    }

    private suspend fun measureBulkDataMemory() {
        val result = MemoryProfileResult("대용량 데이터 처리")
        profilingResults["bulk_data"] = result
        
        System.gc()
        delay(1000)
        
        // 대용량 데이터 처리 시뮬레이션
        val beforeSnapshot = captureMemorySnapshot()
        
        val bulkData = generateBulkData(10000) // 10,000개 객체
        result.addSnapshot(captureMemorySnapshot())
        
        // 데이터 처리
        val processedData = processBulkData(bulkData)
        result.addSnapshot(captureMemorySnapshot())
        
        // 메모리 정리
        @Suppress("UNUSED_VALUE")
        var unusedRef: Any? = processedData
        unusedRef = null
        System.gc()
        delay(2000)
        
        val afterSnapshot = captureMemorySnapshot()
        result.addSnapshot(afterSnapshot)
        
        // 메모리 회수 효율성 검사
        val memoryRecovered = beforeSnapshot.heapUsed - afterSnapshot.heapUsed
        if (memoryRecovered < 0) {
            result.memoryLeaks.add("대용량 데이터 처리 후 메모리 미회수: ${-memoryRecovered / 1024 / 1024}MB")
        }
    }

    private suspend fun measureLongRunningMemory() {
        val result = MemoryProfileResult("장시간 실행")
        profilingResults["long_running"] = result
        
        System.gc()
        delay(1000)
        
        // 2분간 지속적인 작업 시뮬레이션
        val startTime = System.currentTimeMillis()
        val duration = 2 * 60 * 1000L // 2분
        
        while (System.currentTimeMillis() - startTime < duration) {
            // 주기적인 작업 시뮬레이션
            val data = simulateRequestProcessing((System.currentTimeMillis() % 1000).toInt())
            
            val snapshot = captureMemorySnapshot()
            result.addSnapshot(snapshot)
            
            // 메모리 누수 감지
            if (result.snapshots.size > 10) {
                val recent = result.snapshots.takeLast(10)
                val trend = recent.last().heapUsed - recent.first().heapUsed
                if (trend > 50 * 1024 * 1024) { // 50MB 증가
                    result.memoryLeaks.add("장시간 실행 중 메모리 누수 감지: ${trend / 1024 / 1024}MB 증가")
                }
            }
            
            delay(5000) // 5초마다 측정
        }
    }

    private fun captureMemorySnapshot(): MemorySnapshot {
        val heapMemory = memoryBean.heapMemoryUsage
        val nonHeapMemory = memoryBean.nonHeapMemoryUsage
        
        // GC 통계 수집
        val totalGcCount = gcBeans.sumOf { it.collectionCount }
        val totalGcTime = gcBeans.sumOf { it.collectionTime }
        
        // 메모리 풀별 정보 수집
        val memoryPools = ManagementFactory.getMemoryPoolMXBeans()
        val youngGenUsed = memoryPools.filter { it.name.contains("Eden") || it.name.contains("Survivor") }
            .sumOf { it.usage.used }
        val oldGenUsed = memoryPools.filter { it.name.contains("Old") || it.name.contains("Tenured") }
            .sumOf { it.usage.used }
        val metaspaceUsed = memoryPools.find { it.name.contains("Metaspace") }?.usage?.used ?: 0
        
        return MemorySnapshot(
            timestamp = LocalDateTime.now(),
            heapUsed = heapMemory.used,
            heapMax = heapMemory.max,
            nonHeapUsed = nonHeapMemory.used,
            nonHeapMax = nonHeapMemory.max,
            youngGenUsed = youngGenUsed,
            oldGenUsed = oldGenUsed,
            metaspaceUsed = metaspaceUsed,
            threadCount = threadBean.threadCount,
            peakThreadCount = threadBean.peakThreadCount,
            totalGcCount = totalGcCount,
            totalGcTime = totalGcTime
        )
    }

    private fun simulateRequestProcessing(requestId: Int): List<String> {
        // 실제 요청 처리와 유사한 메모리 사용 패턴 시뮬레이션
        val data = mutableListOf<String>()
        
        // 문자열 처리 (Kotlin vs Java 비교용)
        repeat(100) {
            data.add("Request $requestId - Processing item $it - ${System.currentTimeMillis()}")
        }
        
        // 컬렉션 연산 (함수형 프로그래밍)
        val processed = data
            .filter { it.contains("Processing") }
            .map { it.uppercase() }
            .take(50)
        
        // 객체 생성 및 해제
        val tempObjects = (1..50).map { 
            TempObject("Temp-$requestId-$it", System.currentTimeMillis(), data.size)
        }
        
        return processed
    }

    private fun generateBulkData(count: Int): List<BulkDataObject> {
        return (1..count).map { id ->
            BulkDataObject(
                id = id.toLong(),
                name = "BulkData-$id",
                description = "Description for bulk data item $id - ${System.currentTimeMillis()}",
                data = ByteArray(1024) { (it % 256).toByte() }, // 1KB 데이터
                metadata = mapOf(
                    "created" to System.currentTimeMillis().toString(),
                    "type" to "bulk",
                    "index" to id.toString()
                )
            )
        }
    }

    private fun processBulkData(data: List<BulkDataObject>): List<String> {
        return data
            .filter { it.id % 2 == 0L }
            .map { "${it.name}: ${it.description.substring(0, minOf(50, it.description.length))}" }
            .sorted()
    }

    private fun analyzeAndPrintResults() {
        println("\n" + "=" * 80)
        println("📊 메모리 프로파일링 분석 결과")
        println("=" * 80)
        println("분석 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        println()

        // 각 시나리오별 결과 출력
        profilingResults.values.forEach { result ->
            val stats = result.calculateStats()
            printScenarioResult(stats)
        }

        // 종합 분석
        printComprehensiveAnalysis()
        
        // 메모리 최적화 권장사항
        printOptimizationRecommendations()
    }

    private fun printScenarioResult(result: MemoryProfileResult) {
        println("🎯 ${result.testName}")
        println("-" * 60)
        
        if (result.snapshots.isNotEmpty()) {
            val firstSnapshot = result.snapshots.first()
            val lastSnapshot = result.snapshots.last()
            
            println("메모리 사용량:")
            println("  시작 힙 메모리: ${firstSnapshot.toMB(firstSnapshot.heapUsed)}MB")
            println("  종료 힙 메모리: ${lastSnapshot.toMB(lastSnapshot.heapUsed)}MB")
            println("  최대 힙 메모리: ${result.snapshots.maxOf { it.toMB(it.heapUsed) }}MB")
            println("  평균 힙 메모리: ${result.toMB(result.averageHeapUsage)}MB")
            println("  힙 사용률: ${"%.1f".format(lastSnapshot.getHeapUtilization())}%")
            
            println("\nGC 통계:")
            val gcCount = lastSnapshot.totalGcCount - firstSnapshot.totalGcCount
            val gcTime = lastSnapshot.totalGcTime - firstSnapshot.totalGcTime
            println("  GC 실행 횟수: $gcCount")
            println("  GC 소요 시간: ${gcTime}ms")
            println("  GC 효율성: ${"%.1f".format(result.gcEfficiency)}%")
            
            println("\n스레드 정보:")
            println("  현재 스레드 수: ${lastSnapshot.threadCount}")
            println("  최대 스레드 수: ${lastSnapshot.peakThreadCount}")
        }
        
        // 메모리 누수 경고
        if (result.memoryLeaks.isNotEmpty()) {
            println("\n⚠️ 메모리 누수 감지:")
            result.memoryLeaks.forEach { leak ->
                println("  - $leak")
            }
        }
        
        // 메모리 사용 패턴 분석
        analyzeMemoryPattern(result)
        
        println()
    }

    private fun analyzeMemoryPattern(result: MemoryProfileResult) {
        if (result.snapshots.size < 5) return
        
        val heapUsages = result.snapshots.map { it.heapUsed }
        val variance = calculateVariance(heapUsages)
        val trend = calculateTrend(heapUsages)
        
        println("\n메모리 패턴 분석:")
        
        when {
            variance < 10 * 1024 * 1024 -> println("  ✅ 안정적인 메모리 사용 패턴")
            variance < 50 * 1024 * 1024 -> println("  ⚠️ 보통 수준의 메모리 변동")
            else -> println("  ❌ 높은 메모리 변동성 - 최적화 필요")
        }
        
        when {
            trend > 1024 * 1024 -> println("  📈 메모리 사용량 증가 추세 (${trend / 1024 / 1024}MB)")
            trend < -1024 * 1024 -> println("  📉 메모리 사용량 감소 추세 (${-trend / 1024 / 1024}MB)")
            else -> println("  ➡️ 안정적인 메모리 사용 추세")
        }
    }

    private fun calculateVariance(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average().toLong()
    }

    private fun calculateTrend(values: List<Long>): Long {
        if (values.size < 2) return 0
        return values.last() - values.first()
    }

    private fun printComprehensiveAnalysis() {
        println("🔍 종합 메모리 분석")
        println("-" * 60)
        
        val results = profilingResults.values.map { it.calculateStats() }
        
        // 메모리 효율성 순위
        println("메모리 효율성 순위:")
        results.sortedBy { it.averageHeapUsage }.forEachIndexed { index, result ->
            println("  ${index + 1}위: ${result.testName} (평균 ${result.toMB(result.averageHeapUsage)}MB)")
        }
        
        // GC 효율성 분석
        println("\nGC 효율성:")
        results.sortedByDescending { it.gcEfficiency }.forEach { result ->
            val grade = when {
                result.gcEfficiency > 95 -> "A+"
                result.gcEfficiency > 90 -> "A"
                result.gcEfficiency > 85 -> "B"
                result.gcEfficiency > 80 -> "C"
                else -> "D"
            }
            println("  ${result.testName}: ${"%.1f".format(result.gcEfficiency)}% (등급: $grade)")
        }
        
        // 메모리 누수 요약
        val totalLeaks = results.sumOf { it.memoryLeaks.size }
        if (totalLeaks > 0) {
            println("\n⚠️ 총 $totalLeaks 개의 메모리 누수 경고가 감지되었습니다.")
        } else {
            println("\n✅ 메모리 누수가 감지되지 않았습니다.")
        }
        
        println()
    }

    private fun printOptimizationRecommendations() {
        println("💡 메모리 최적화 권장사항")
        println("-" * 60)
        
        val results = profilingResults.values.map { it.calculateStats() }
        
        // 힙 메모리 최적화
        val highMemoryUsage = results.any { it.averageHeapUsage > 512 * 1024 * 1024 }
        if (highMemoryUsage) {
            println("힙 메모리 최적화:")
            println("  - JVM 힙 크기 조정: -Xmx, -Xms 옵션 튜닝")
            println("  - 객체 풀링 패턴 적용")
            println("  - 불필요한 객체 생성 최소화")
        }
        
        // GC 최적화
        val lowGcEfficiency = results.any { it.gcEfficiency < 90 }
        if (lowGcEfficiency) {
            println("\nGC 최적화:")
            println("  - G1GC 또는 ZGC 사용 고려")
            println("  - Young Generation 크기 조정")
            println("  - GC 튜닝 매개변수 최적화")
        }
        
        // 메모리 누수 방지
        val hasMemoryLeaks = results.any { it.memoryLeaks.isNotEmpty() }
        if (hasMemoryLeaks) {
            println("\n메모리 누수 방지:")
            println("  - WeakReference 사용 고려")
            println("  - 리스너 및 콜백 해제")
            println("  - ThreadLocal 변수 정리")
            println("  - 대용량 컬렉션 사용 시 주의")
        }
        
        // 코틀린 특화 최적화
        println("\nKotlin 특화 최적화:")
        println("  - inline 함수 활용")
        println("  - data class copy() 남용 주의")
        println("  - 시퀀스(Sequence) 활용으로 지연 연산")
        println("  - 불변 컬렉션 사용")
        
        // 일반적인 최적화
        println("\n일반적인 최적화:")
        println("  - 적절한 컬렉션 초기 크기 설정")
        println("  - StringBuilder 사용 (문자열 연산)")
        println("  - 캐시 전략 적용")
        println("  - 지연 로딩 패턴 활용")
        
        println("\n" + "=" * 80)
    }

    // 테스트용 데이터 클래스들
    private data class TempObject(
        val name: String,
        val timestamp: Long,
        val size: Int
    )

    private data class BulkDataObject(
        val id: Long,
        val name: String,
        val description: String,
        val data: ByteArray,
        val metadata: Map<String, String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as BulkDataObject
            return id == other.id
        }

        override fun hashCode(): Int = id.hashCode()
    }

    private fun MemoryProfileResult.toMB(bytes: Long): Long = bytes / (1024 * 1024)
}