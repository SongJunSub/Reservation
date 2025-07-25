package com.example.reservation.concurrency

import com.example.reservation.controller.CreateReservationRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Kotlin Coroutines 예제 및 데모
 * Coroutines의 특징과 고급 사용 패턴을 보여주는 교육용 코드
 */
@Component
class CoroutinesExample {

    /**
     * Kotlin Coroutines 기본 사용법 데모
     */
    suspend fun demonstrateBasicUsage() {
        println("🚀 Kotlin Coroutines 기본 사용법 데모")
        println("=".repeat(50))
        
        // 1. 기본 코루틴 생성
        basicCoroutineExample()
        
        // 2. 여러 코루틴 동시 실행
        multipleCoroutinesExample()
        
        // 3. async/await 패턴
        asyncAwaitExample()
        
        // 4. 구조화된 동시성
        structuredConcurrencyExample()
    }
    
    private suspend fun basicCoroutineExample() {
        println("\n1️⃣ 기본 코루틴 예제:")
        
        // 코루틴 스코프에서 실행
        coroutineScope {
            launch {
                println("코루틴 시작: ${Thread.currentThread().name}")
                delay(1000) // 논블로킹 지연
                println("코루틴 완료")
            }
        }
    }
    
    private suspend fun multipleCoroutinesExample() {
        println("\n2️⃣ 다중 코루틴 예제:")
        
        val startTime = System.currentTimeMillis()
        
        coroutineScope {
            repeat(10) { taskId ->
                launch {
                    println("Task $taskId 시작 (코루틴: ${coroutineContext[CoroutineName]})")
                    delay(500 + (taskId * 100L)) // 가변 지연 시간
                    println("Task $taskId 완료")
                }
            }
        }
        
        val endTime = System.currentTimeMillis()
        println("총 실행 시간: ${endTime - startTime} ms")
    }
    
    private suspend fun asyncAwaitExample() {
        println("\n3️⃣ async/await 패턴 예제:")
        
        coroutineScope {
            // 병렬로 실행되는 비동기 작업들
            val deferred1 = async { 
                delay(300)
                "사용자 데이터"
            }
            
            val deferred2 = async {
                delay(250)
                "예약 데이터"
            }
            
            val deferred3 = async {
                delay(400)
                "결제 데이터"
            }
            
            // 모든 결과를 기다려서 결합
            val results = awaitAll(deferred1, deferred2, deferred3)
            println("통합 결과: ${results.joinToString(" + ")}")
        }
    }
    
    private suspend fun structuredConcurrencyExample() {
        println("\n4️⃣ 구조화된 동시성 예제:")
        
        try {
            coroutineScope {
                val job1 = launch {
                    delay(2000)
                    println("장시간 작업 완료")
                }
                
                val job2 = launch {
                    delay(500)
                    throw Exception("작업 중 오류 발생!")
                }
                
                // 하나라도 실패하면 모든 작업이 취소됨
            }
        } catch (e: Exception) {
            println("구조화된 동시성으로 인한 전체 취소: ${e.message}")
        }
    }
    
    /**
     * Coroutines vs Threads 성능 비교
     */
    suspend fun compareCoroutinesVsThreads() {
        println("\n🔥 Coroutines vs Threads 성능 비교")
        println("=".repeat(60))
        
        val taskCount = 10000
        
        // Thread 기반 테스트
        val threadsTime = measureTimeMillis {
            testWithThreads(taskCount)
        }
        
        // 메모리 정리
        System.gc()
        delay(1000)
        
        // Coroutines 테스트
        val coroutinesTime = measureTimeMillis {
            testWithCoroutines(taskCount)
        }
        
        // 결과 비교
        printPerformanceComparison(taskCount, threadsTime, coroutinesTime)
    }
    
    private fun testWithThreads(taskCount: Int) {
        println("🧵 Thread 기반 테스트 중...")
        
        val completedTasks = AtomicInteger(0)
        val threads = mutableListOf<Thread>()
        
        // 스레드 생성 제한 (메모리 제한으로 인해)
        val batchSize = 100
        for (batch in 0 until taskCount step batchSize) {
            val currentBatchSize = minOf(batchSize, taskCount - batch)
            
            repeat(currentBatchSize) { i ->
                val thread = Thread {
                    Thread.sleep(10 + (i % 50).toLong()) // 가변 지연
                    completedTasks.incrementAndGet()
                }
                threads.add(thread)
                thread.start()
            }
            
            // 배치별로 완료 대기
            threads.forEach { it.join() }
            threads.clear()
        }
        
        println("  완료된 작업: ${completedTasks.get()}/$taskCount")
    }
    
    private suspend fun testWithCoroutines(taskCount: Int) {
        println("⚡ Coroutines 테스트 중...")
        
        val completedTasks = AtomicInteger(0)
        
        coroutineScope {
            repeat(taskCount) { i ->
                launch {
                    delay(10 + (i % 50).toLong()) // 가변 지연
                    completedTasks.incrementAndGet()
                }
            }
        }
        
        println("  완료된 작업: ${completedTasks.get()}/$taskCount")
    }
    
    private fun printPerformanceComparison(taskCount: Int, threadsTime: Long, coroutinesTime: Long) {
        println("\n📊 성능 비교 결과:")
        println("-".repeat(30))
        
        println("작업 수: $taskCount")
        println("Thread 기반: ${threadsTime} ms")
        println("Coroutines: ${coroutinesTime} ms")
        
        if (coroutinesTime < threadsTime) {
            val improvement = ((threadsTime - coroutinesTime).toDouble() / threadsTime) * 100
            println("Coroutines가 ${"%.1f".format(improvement)}% 빠름")
        } else {
            val degradation = ((coroutinesTime - threadsTime).toDouble() / coroutinesTime) * 100
            println("Thread가 ${"%.1f".format(degradation)}% 빠름")
        }
        
        // 처리량 계산
        val threadsThroughput = taskCount.toDouble() / threadsTime * 1000
        val coroutinesThroughput = taskCount.toDouble() / coroutinesTime * 1000
        
        println("Thread 처리량: ${"%.1f".format(threadsThroughput)} tasks/sec")
        println("Coroutines 처리량: ${"%.1f".format(coroutinesThroughput)} tasks/sec")
    }
    
    /**
     * 고급 Coroutines 패턴 데모
     */
    suspend fun demonstrateAdvancedPatterns() {
        println("\n🎯 고급 Coroutines 패턴")
        println("=".repeat(40))
        
        // 1. Flow를 사용한 데이터 스트림
        flowPatternExample()
        
        // 2. Channel을 사용한 통신
        channelPatternExample()
        
        // 3. Select를 사용한 다중 채널 처리
        selectPatternExample()
        
        // 4. Supervisor Job을 사용한 에러 격리
        supervisorJobExample()
    }
    
    private suspend fun flowPatternExample() {
        println("\n1️⃣ Flow 패턴 예제:")
        
        // 데이터 스트림 생성
        val reservationFlow = flow {
            repeat(10) { id ->
                delay(100) // 비동기 데이터 생성 시뮬레이션
                emit(CreateReservationRequest(
                    guestName = "Guest $id",
                    roomNumber = "Room ${id % 5 + 1}",
                    checkInDate = "2024-12-25",
                    checkOutDate = "2024-12-27",
                    totalAmount = 200.0 + id
                ))
            }
        }
        
        // Flow 연산자를 사용한 데이터 처리
        reservationFlow
            .filter { it.totalAmount > 205.0 }
            .map { "처리된 예약: ${it.guestName} (${it.totalAmount}원)" }
            .take(5)
            .collect { result ->
                println("  $result")
            }
    }
    
    private suspend fun channelPatternExample() {
        println("\n2️⃣ Channel 패턴 예제:")
        
        val channel = Channel<String>(capacity = 10)
        
        coroutineScope {
            // Producer 코루틴
            launch {
                repeat(20) { i ->
                    delay(50)
                    channel.send("메시지 $i")
                }
                channel.close()
            }
            
            // Consumer 코루틴들
            repeat(3) { consumerId ->
                launch {
                    for (message in channel) {
                        println("  Consumer $consumerId: $message")
                        delay(30) // 처리 시간 시뮬레이션
                    }
                }
            }
        }
    }
    
    private suspend fun selectPatternExample() {
        println("\n3️⃣ Select 패턴 예제:")
        
        val channel1 = Channel<String>()
        val channel2 = Channel<String>()
        
        coroutineScope {
            // 데이터 생산자들
            launch {
                repeat(5) { i ->
                    delay(Random.nextLong(100, 300))
                    channel1.send("Channel1: Message $i")
                }
                channel1.close()
            }
            
            launch {
                repeat(5) { i ->
                    delay(Random.nextLong(100, 300))
                    channel2.send("Channel2: Message $i")
                }
                channel2.close()
            }
            
            // Select를 사용한 다중 채널 처리
            launch {
                var activeChannels = 2
                while (activeChannels > 0) {
                    select<Unit> {
                        channel1.onReceiveCatching { result ->
                            result.getOrNull()?.let { message ->
                                println("  받음: $message")
                            } ?: run {
                                println("  Channel1 종료")
                                activeChannels--
                            }
                        }
                        
                        channel2.onReceiveCatching { result ->
                            result.getOrNull()?.let { message ->
                                println("  받음: $message")
                            } ?: run {
                                println("  Channel2 종료")
                                activeChannels--
                            }
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun supervisorJobExample() {
        println("\n4️⃣ Supervisor Job 패턴 예제:")
        
        supervisorScope {
            val job1 = launch {
                repeat(10) { i ->
                    delay(200)
                    println("  안정적인 작업 $i 완료")
                }
            }
            
            val job2 = launch {
                delay(500)
                throw Exception("불안정한 작업에서 오류 발생!")
            }
            
            val job3 = launch {
                repeat(8) { i ->
                    delay(300)
                    println("  다른 안정적인 작업 $i 완료")
                }
            }
            
            // job2가 실패해도 job1과 job3는 계속 실행됨
            try {
                job2.join()
            } catch (e: Exception) {
                println("  오류 발생했지만 다른 작업들은 계속 실행: ${e.message}")
            }
        }
    }
    
    /**
     * 메모리 효율성 분석
     */
    suspend fun analyzeMemoryEfficiency() {
        println("\n🧠 Coroutines 메모리 효율성 분석")
        println("=".repeat(50))
        
        val initialMemory = getMemoryUsage()
        println("초기 메모리 사용량: $initialMemory MB")
        
        val coroutineCount = 100000 // 대량의 코루틴 생성
        println("${coroutineCount}개의 코루틴 생성 중...")
        
        val startTime = System.currentTimeMillis()
        val completedTasks = AtomicInteger(0)
        
        coroutineScope {
            repeat(coroutineCount) { i ->
                launch {
                    delay(5000) // 5초 대기 (메모리 사용량 측정용)
                    completedTasks.incrementAndGet()
                }
            }
            
            // 코루틴 생성 완료 후 메모리 측정
            delay(1000) // 안정화 대기
            val memoryAfterCreation = getMemoryUsage()
            println("코루틴 생성 후 메모리: $memoryAfterCreation MB (+${memoryAfterCreation - initialMemory} MB)")
            
            // 메모리 효율성 계산
            val memoryPerCoroutine = (memoryAfterCreation - initialMemory).toDouble() / coroutineCount * 1024
            println("코루틴당 메모리 사용량: ${"%.2f".format(memoryPerCoroutine)} KB")
            
            // 참고: Thread는 일반적으로 1-8MB 스택 메모리 사용
            val threadMemoryKB = 2 * 1024 // 2MB 기준
            val efficiencyRatio = threadMemoryKB / memoryPerCoroutine
            println("Thread 대비 메모리 효율성: ${"%.0f".format(efficiencyRatio)}배")
        }
        
        val endTime = System.currentTimeMillis()
        println("총 실행 시간: ${endTime - startTime} ms")
        println("완료된 코루틴: ${completedTasks.get()}/$coroutineCount")
        
        // 최종 메모리 사용량
        System.gc()
        delay(1000)
        val finalMemory = getMemoryUsage()
        println("정리 후 메모리: $finalMemory MB")
    }
    
    /**
     * 실제 사용 사례 패턴
     */
    suspend fun demonstrateRealWorldPatterns() {
        println("\n🌍 실제 사용 사례 패턴")
        println("=".repeat(40))
        
        // 1. 병렬 API 호출
        parallelAPICallsPattern()
        
        // 2. 데이터베이스 배치 처리
        batchDatabaseProcessingPattern()
        
        // 3. 이벤트 스트림 처리
        eventStreamProcessingPattern()
    }
    
    private suspend fun parallelAPICallsPattern() {
        println("\n1️⃣ 병렬 API 호출 패턴:")
        
        val apiEndpoints = listOf(
            "user-service", "reservation-service", "payment-service",
            "notification-service", "audit-service"
        )
        
        val results = coroutineScope {
            apiEndpoints.map { endpoint ->
                async {
                    // API 호출 시뮬레이션
                    delay(Random.nextLong(100, 500))
                    "$endpoint 응답 데이터 (코루틴: ${coroutineContext[CoroutineName]})"
                }
            }.awaitAll()
        }
        
        results.forEach { result ->
            println("  $result")
        }
    }
    
    private suspend fun batchDatabaseProcessingPattern() {
        println("\n2️⃣ 데이터베이스 배치 처리 패턴:")
        
        val batchSize = 100
        val totalRecords = 1000
        val processedRecords = AtomicInteger(0)
        
        // 배치별 병렬 처리
        coroutineScope {
            (0 until totalRecords step batchSize).map { batchStart ->
                async {
                    val batchEnd = minOf(batchStart + batchSize, totalRecords)
                    val batchRecords = (batchStart until batchEnd).toList()
                    
                    // 배치 처리 시뮬레이션
                    delay(200)
                    
                    batchRecords.forEach { recordId ->
                        // 개별 레코드 처리
                        processedRecords.incrementAndGet()
                    }
                    
                    "배치 $batchStart-${batchEnd-1} 처리 완료 (${batchRecords.size}개 레코드)"
                }
            }.awaitAll().forEach { result ->
                println("  $result")
            }
        }
        
        println("총 처리된 레코드: ${processedRecords.get()}/$totalRecords")
    }
    
    private suspend fun eventStreamProcessingPattern() {
        println("\n3️⃣ 이벤트 스트림 처리 패턴:")
        
        // 이벤트 스트림 생성
        val eventStream = flow {
            repeat(50) { i ->
                delay(50)
                emit("Event-$i")
            }
        }
        
        // 백프레셔와 버퍼링을 활용한 처리
        eventStream
            .buffer(capacity = 10) // 버퍼링으로 백프레셔 처리
            .chunked(5) // 5개씩 배치 처리
            .map { eventBatch ->
                // 배치 처리
                delay(100) // 처리 시간 시뮬레이션
                "배치 처리: ${eventBatch.joinToString(", ")}"
            }
            .take(8) // 처음 8개 배치만 처리
            .collect { result ->
                println("  $result")
            }
    }
    
    /**
     * 에러 처리 패턴
     */
    suspend fun demonstrateErrorHandling() {
        println("\n⚠️ Coroutines 에러 처리 패턴")
        println("=".repeat(40))
        
        // 1. try-catch를 사용한 기본 에러 처리
        basicErrorHandlingExample()
        
        // 2. CoroutineExceptionHandler 사용
        exceptionHandlerExample()
        
        // 3. 부분 실패 처리 (supervisorScope)
        partialFailureHandlingExample()
    }
    
    private suspend fun basicErrorHandlingExample() {
        println("\n1️⃣ 기본 에러 처리:")
        
        try {
            coroutineScope {
                launch {
                    delay(100)
                    throw Exception("계획된 오류")
                }
                
                launch {
                    delay(200)
                    println("  이 작업은 실행되지 않음 (부모 취소)")
                }
            }
        } catch (e: Exception) {
            println("  에러 캐치: ${e.message}")
        }
    }
    
    private suspend fun exceptionHandlerExample() {
        println("\n2️⃣ CoroutineExceptionHandler 사용:")
        
        val handler = CoroutineExceptionHandler { _, exception ->
            println("  전역 에러 핸들러: ${exception.message}")
        }
        
        val scope = CoroutineScope(SupervisorJob() + handler)
        
        scope.launch {
            delay(100)
            throw Exception("처리되지 않은 예외")
        }
        
        scope.launch {
            delay(200)
            println("  이 작업은 계속 실행됨 (SupervisorJob 사용)")
        }
        
        delay(300) // 모든 작업 완료 대기
        scope.cancel()
    }
    
    private suspend fun partialFailureHandlingExample() {
        println("\n3️⃣ 부분 실패 처리:")
        
        supervisorScope {
            val results = (1..5).map { taskId ->
                async {
                    try {
                        delay(100)
                        if (taskId == 3) {
                            throw Exception("Task $taskId 실패")
                        }
                        "Task $taskId 성공"
                    } catch (e: Exception) {
                        "Task $taskId 오류: ${e.message}"
                    }
                }
            }
            
            results.awaitAll().forEach { result ->
                println("  $result")
            }
        }
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    // Flow chunked 확장 함수 (Kotlin 표준 라이브러리에 없는 경우)
    private fun <T> Flow<T>.chunked(size: Int): Flow<List<T>> = flow {
        val chunk = mutableListOf<T>()
        collect { item ->
            chunk.add(item)
            if (chunk.size >= size) {
                emit(chunk.toList())
                chunk.clear()
            }
        }
        if (chunk.isNotEmpty()) {
            emit(chunk.toList())
        }
    }
}