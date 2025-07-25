package com.example.reservation.benchmark

import kotlinx.coroutines.*
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.lang.ref.WeakReference
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 메모리 누수 감지 도구
 * 잠재적인 메모리 누수를 자동으로 감지하고 분석
 */
@Component
class MemoryLeakDetector : CommandLineRunner {

    private val memoryBean = ManagementFactory.getMemoryMXBean()
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    
    data class LeakSuspect(
        val location: String,
        val objectType: String,
        val instanceCount: AtomicInteger = AtomicInteger(0),
        val totalMemory: AtomicLong = AtomicLong(0),
        val firstDetected: LocalDateTime = LocalDateTime.now(),
        val lastDetected: LocalDateTime = LocalDateTime.now()
    ) {
        fun updateDetection() {
            instanceCount.incrementAndGet()
        }
        
        fun getSeverity(): String = when {
            instanceCount.get() > 1000 -> "HIGH"
            instanceCount.get() > 100 -> "MEDIUM"
            else -> "LOW"
        }
    }

    data class MemoryLeakReport(
        val testDuration: Long,
        val totalLeakSuspects: Int,
        val highSeverityLeaks: Int,
        val mediumSeverityLeaks: Int,
        val lowSeverityLeaks: Int,
        val memoryGrowthRate: Double,
        val gcPressure: Double,
        val suspects: List<LeakSuspect>
    )

    private val leakSuspects = ConcurrentHashMap<String, LeakSuspect>()
    private val weakReferences = mutableListOf<WeakReference<Any>>()
    
    override fun run(vararg args: String?) {
        if (args.contains("--memory-leak-detection")) {
            println("🕵️ 메모리 누수 감지 시작...")
            runMemoryLeakDetection()
        }
    }

    fun runMemoryLeakDetection() {
        println("🔍 메모리 누수 감지 및 분석")
        println("=" * 80)
        
        runBlocking {
            val startTime = System.currentTimeMillis()
            val initialMemory = memoryBean.heapMemoryUsage.used
            val initialGcCount = gcBeans.sumOf { it.collectionCount }
            
            // 다양한 메모리 누수 시나리오 테스트
            val leakTests = listOf(
                "컬렉션 누수" to { testCollectionLeak() },
                "리스너 누수" to { testListenerLeak() },
                "스레드 로컬 누수" to { testThreadLocalLeak() },
                "캐시 누수" to { testCacheLeak() },
                "클로저 누수" to { testClosureLeak() }
            )
            
            leakTests.forEach { (testName, test) ->
                println("\n📊 $testName 테스트 중...")
                test()
                delay(2000)
                
                // 강제 GC 후 메모리 상태 확인
                System.gc()
                delay(1000)
                checkForLeakSuspects(testName)
            }
            
            val endTime = System.currentTimeMillis()
            val finalMemory = memoryBean.heapMemoryUsage.used
            val finalGcCount = gcBeans.sumOf { it.collectionCount }
            
            // 결과 분석 및 리포트 생성
            val report = generateLeakReport(
                testDuration = endTime - startTime,
                initialMemory = initialMemory,
                finalMemory = finalMemory,
                initialGcCount = initialGcCount,
                finalGcCount = finalGcCount
            )
            
            printLeakReport(report)
        }
    }

    private suspend fun testCollectionLeak() {
        // 컬렉션이 계속 증가하는 상황 시뮬레이션
        val suspectKey = "collection_leak_test"
        val leakingCollection = mutableListOf<String>()
        
        repeat(1000) { index ->
            val item = "LeakingItem-$index-${System.currentTimeMillis()}"
            leakingCollection.add(item)
            
            // 일부 항목만 제거 (완전한 정리 안됨)
            if (index % 10 == 0 && leakingCollection.size > 5) {
                leakingCollection.removeAt(0)
            }
            
            if (index % 100 == 0) {
                recordLeakSuspect(suspectKey, "MutableList", leakingCollection.size.toLong())
            }
            
            delay(1)
        }
        
        // 약한 참조로 추적
        weakReferences.add(WeakReference(leakingCollection))
    }

    private suspend fun testListenerLeak() {
        // 리스너가 제거되지 않는 상황 시뮬레이션
        val suspectKey = "listener_leak_test"
        val listeners = mutableListOf<EventListener>()
        
        repeat(100) { index ->
            val listener = EventListener("Listener-$index")
            listeners.add(listener)
            
            // 시뮬레이션: 리스너 등록은 하지만 해제는 안함
            registerListener(listener)
            
            recordLeakSuspect(suspectKey, "EventListener", listeners.size.toLong())
            delay(10)
        }
        
        weakReferences.add(WeakReference(listeners))
    }

    private suspend fun testThreadLocalLeak() {
        // ThreadLocal 변수가 정리되지 않는 상황 시뮬레이션
        val suspectKey = "threadlocal_leak_test"
        val threadLocal = ThreadLocal<MutableList<String>>()
        
        val jobs = (1..20).map { threadId ->
            async(Dispatchers.IO) {
                val localData = mutableListOf<String>()
                threadLocal.set(localData)
                
                repeat(50) { index ->
                    localData.add("ThreadData-$threadId-$index")
                    delay(5)
                }
                
                recordLeakSuspect(suspectKey, "ThreadLocal", localData.size.toLong())
                
                // ThreadLocal 정리를 안함 (누수 시뮬레이션)
                // threadLocal.remove() // 이 줄이 주석처리되어 누수 발생
            }
        }
        
        jobs.awaitAll()
        weakReferences.add(WeakReference(threadLocal))
    }

    private suspend fun testCacheLeak() {
        // 캐시가 무한정 증가하는 상황 시뮬레이션
        val suspectKey = "cache_leak_test"
        val cache = mutableMapOf<String, CachedObject>()
        
        repeat(500) { index ->
            val key = "cache-key-$index"
            val value = CachedObject("data-$index", System.currentTimeMillis())
            cache[key] = value
            
            // 오래된 캐시 항목을 일부만 제거 (완전한 정리 안됨)
            if (index % 50 == 0 && cache.size > 10) {
                val oldestKey = cache.keys.first()
                cache.remove(oldestKey)
            }
            
            if (index % 50 == 0) {
                recordLeakSuspect(suspectKey, "Cache", cache.size.toLong())
            }
            
            delay(2)
        }
        
        weakReferences.add(WeakReference(cache))
    }

    private suspend fun testClosureLeak() {
        // 클로저가 외부 변수를 계속 참조하는 상황 시뮬레이션
        val suspectKey = "closure_leak_test"
        val closures = mutableListOf<() -> String>()
        
        repeat(200) { index ->
            val largeData = "Large data string ".repeat(100) + index
            
            // 클로저가 largeData를 캡처
            val closure = { "Processing: $largeData" }
            closures.add(closure)
            
            if (index % 20 == 0) {
                recordLeakSuspect(suspectKey, "Closure", closures.size.toLong())
            }
            
            delay(5)
        }
        
        weakReferences.add(WeakReference(closures))
    }

    private fun recordLeakSuspect(location: String, objectType: String, memorySize: Long) {
        val suspect = leakSuspects.computeIfAbsent(location) { 
            LeakSuspect(location, objectType) 
        }
        
        suspect.updateDetection()
        suspect.totalMemory.addAndGet(memorySize)
    }

    private fun checkForLeakSuspects(testName: String) {
        val currentMemory = memoryBean.heapMemoryUsage.used
        val heapUtilization = (currentMemory.toDouble() / memoryBean.heapMemoryUsage.max) * 100
        
        println("  메모리 상태: ${currentMemory / 1024 / 1024}MB (사용률: ${"%.1f".format(heapUtilization)}%)")
        
        // 약한 참조 확인
        val aliveReferences = weakReferences.count { it.get() != null }
        val totalReferences = weakReferences.size
        
        if (aliveReferences > 0) {
            println("  ⚠️ $aliveReferences/$totalReferences 객체가 여전히 메모리에 존재")
        } else {
            println("  ✅ 모든 테스트 객체가 GC되었음")
        }
    }

    private fun generateLeakReport(
        testDuration: Long,
        initialMemory: Long,
        finalMemory: Long,
        initialGcCount: Long,
        finalGcCount: Long
    ): MemoryLeakReport {
        val memoryGrowth = finalMemory - initialMemory
        val memoryGrowthRate = (memoryGrowth.toDouble() / testDuration) * 1000 // bytes per second
        
        val gcCount = finalGcCount - initialGcCount
        val gcPressure = gcCount.toDouble() / (testDuration / 1000.0) // GC per second
        
        val suspects = leakSuspects.values.toList()
        val highSeverity = suspects.count { it.getSeverity() == "HIGH" }
        val mediumSeverity = suspects.count { it.getSeverity() == "MEDIUM" }
        val lowSeverity = suspects.count { it.getSeverity() == "LOW" }
        
        return MemoryLeakReport(
            testDuration = testDuration,
            totalLeakSuspects = suspects.size,
            highSeverityLeaks = highSeverity,
            mediumSeverityLeaks = mediumSeverity,
            lowSeverityLeaks = lowSeverity,
            memoryGrowthRate = memoryGrowthRate,
            gcPressure = gcPressure,
            suspects = suspects.sortedByDescending { it.instanceCount.get() }
        )
    }

    private fun printLeakReport(report: MemoryLeakReport) {
        println("\n" + "=" * 80)
        println("🕵️ 메모리 누수 감지 결과 보고서")
        println("=" * 80)
        println("분석 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        println("테스트 지속시간: ${report.testDuration}ms")
        println()

        // 전체 요약
        println("📊 누수 의심 항목 요약:")
        println("  총 의심 항목: ${report.totalLeakSuspects}개")
        println("  높은 위험도: ${report.highSeverityLeaks}개")
        println("  중간 위험도: ${report.mediumSeverityLeaks}개")
        println("  낮은 위험도: ${report.lowSeverityLeaks}개")
        println()

        // 메모리 증가율 분석
        println("📈 메모리 사용 분석:")
        println("  메모리 증가율: ${"%.2f".format(report.memoryGrowthRate / 1024)}KB/초")
        println("  GC 압박도: ${"%.2f".format(report.gcPressure)}회/초")
        
        val overallRisk = when {
            report.highSeverityLeaks > 0 -> "높음"
            report.mediumSeverityLeaks > 2 -> "중간"
            report.totalLeakSuspects > 0 -> "낮음"
            else -> "없음"
        }
        println("  전체 위험도: $overallRisk")
        println()

        // 상세 누수 의심 항목
        if (report.suspects.isNotEmpty()) {
            println("🚨 누수 의심 항목 상세:")
            println("-" * 60)
            
            report.suspects.forEach { suspect ->
                val severity = suspect.getSeverity()
                val severityIcon = when (severity) {
                    "HIGH" -> "🔴"
                    "MEDIUM" -> "🟡"
                    else -> "🟢"
                }
                
                println("$severityIcon ${suspect.location} (${suspect.objectType})")
                println("    감지 횟수: ${suspect.instanceCount.get()}")
                println("    위험도: $severity")
                println("    첫 감지: ${suspect.firstDetected.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
                println("    최근 감지: ${suspect.lastDetected.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
                println()
            }
        }

        // 권장사항
        printLeakPreventionRecommendations(report)
    }

    private fun printLeakPreventionRecommendations(report: MemoryLeakReport) {
        println("💡 메모리 누수 방지 권장사항")
        println("-" * 60)
        
        if (report.highSeverityLeaks > 0) {
            println("🔴 높은 위험도 항목이 감지되었습니다:")
            println("  - 즉시 코드 리뷰 및 수정 필요")
            println("  - 프로덕션 배포 전 필수 해결")
            println()
        }
        
        // 각 누수 타입별 권장사항
        val leakTypes = report.suspects.groupBy { it.objectType }
        
        leakTypes.forEach { (type, suspects) ->
            when (type) {
                "MutableList", "Cache" -> {
                    println("📋 컬렉션/캐시 누수 방지:")
                    println("  - 적절한 크기 제한 설정")
                    println("  - LRU 캐시 정책 적용")
                    println("  - WeakHashMap 사용 고려")
                    println("  - 정기적인 정리 작업 스케줄링")
                }
                "EventListener" -> {
                    println("👂 리스너 누수 방지:")
                    println("  - WeakReference를 사용한 리스너 등록")
                    println("  - try-with-resources 패턴 활용")
                    println("  - 컴포넌트 소멸 시 리스너 해제")
                    println("  - 자동 해제 메커니즘 구현")
                }
                "ThreadLocal" -> {
                    println("🧵 ThreadLocal 누수 방지:")
                    println("  - finally 블록에서 ThreadLocal.remove() 호출")
                    println("  - 스레드 풀 사용 시 특히 주의")
                    println("  - ThreadLocal 사용 최소화")
                    println("  - 적절한 생명주기 관리")
                }
                "Closure" -> {
                    println("🔒 클로저 누수 방지:")
                    println("  - 불필요한 외부 변수 캡처 방지")
                    println("  - 람다식에서 final 변수만 사용")
                    println("  - 메서드 참조 사용 고려")
                    println("  - 클로저 생명주기 관리")
                }
            }
            println()
        }
        
        // 일반적인 메모리 관리 권장사항
        println("🛡️ 일반적인 메모리 누수 방지:")
        println("  - 정기적인 메모리 프로파일링")
        println("  - 단위 테스트에 메모리 누수 테스트 포함")
        println("  - 코드 리뷰 시 메모리 관리 확인")
        println("  - 모니터링 도구 활용 (JProfiler, VisualVM)")
        println("  - 적절한 JVM 설정 (-XX:+HeapDumpOnOutOfMemoryError)")
        
        println("\n" + "=" * 80)
    }

    // 리스너 시뮬레이션을 위한 더미 메서드
    private fun registerListener(listener: EventListener) {
        // 실제로는 이벤트 시스템에 등록
        // 시뮬레이션을 위해 빈 구현
    }

    // 테스트용 클래스들
    private data class EventListener(val id: String)
    
    private data class CachedObject(
        val data: String,
        val timestamp: Long
    )
}