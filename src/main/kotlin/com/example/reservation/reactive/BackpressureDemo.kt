package com.example.reservation.reactive

import com.example.reservation.controller.CreateReservationRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Reactive Streams 백프레셔 처리 예제 및 데모
 * Kotlin Flow와 Project Reactor의 백프레셔 전략을 비교 분석
 */
@Component
class BackpressureDemo {

    /**
     * Kotlin Flow 백프레셔 처리 데모
     */
    suspend fun demonstrateFlowBackpressure() {
        println("🌊 Kotlin Flow 백프레셔 처리 데모")
        println("=" * 60)
        println("시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
        println()

        // 1. Buffer 전략
        bufferBackpressureExample()
        
        // 2. Conflate 전략
        conflateBackpressureExample()
        
        // 3. CollectLatest 전략
        collectLatestBackpressureExample()
        
        // 4. Custom 백프레셔 전략
        customBackpressureExample()
        
        // 5. 동적 백프레셔 제어
        dynamicBackpressureExample()
    }

    /**
     * Project Reactor 백프레셔 처리 데모
     */
    fun demonstrateReactorBackpressure() {
        println("\n⚛️ Project Reactor 백프레셔 처리 데모")
        println("=" * 60)
        println("시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}")
        println()

        // 1. Buffer 전략
        reactorBufferExample()
        
        // 2. Drop 전략
        reactorDropExample()
        
        // 3. Latest 전략
        reactorLatestExample()
        
        // 4. Error 전략
        reactorErrorExample()
        
        // 5. 요청 기반 백프레셔
        reactorRequestBasedExample()
    }

    // ===== Kotlin Flow 백프레셔 예제들 =====

    private suspend fun bufferBackpressureExample() {
        println("1️⃣ Buffer 백프레셔 전략:")
        println("   생산자가 빠르고 소비자가 느린 경우 버퍼에 저장")
        
        val processedCount = AtomicInteger(0)
        val droppedCount = AtomicInteger(0)
        
        val executionTime = measureTimeMillis {
            fastProducerFlow()
                .buffer(capacity = 50) // 50개까지 버퍼링
                .collect { reservation ->
                    // 느린 소비자 (처리 시간 100ms)
                    delay(100)
                    processedCount.incrementAndGet()
                    if (processedCount.get() <= 5) {
                        println("    처리됨: ${reservation.guestName}")
                    }
                }
        }
        
        println("    실행 시간: ${executionTime}ms")
        println("    처리된 항목: ${processedCount.get()}개")
        println("    버퍼 전략 특징: 모든 데이터 보존, 메모리 사용량 증가")
        println()
    }

    private suspend fun conflateBackpressureExample() {
        println("2️⃣ Conflate 백프레셔 전략:")
        println("   최신 값만 유지하고 중간 값들은 건너뛰기")
        
        val processedCount = AtomicInteger(0)
        val executionTime = measureTimeMillis {
            fastProducerFlow()
                .conflate() // 최신 값만 유지
                .collect { reservation ->
                    delay(200) // 매우 느린 소비자
                    processedCount.incrementAndGet()
                    println("    처리됨: ${reservation.guestName} (${processedCount.get()}번째)")
                }
        }
        
        println("    실행 시간: ${executionTime}ms")
        println("    처리된 항목: ${processedCount.get()}개")
        println("    Conflate 특징: 최신 데이터만 처리, 데이터 손실 가능")
        println()
    }

    private suspend fun collectLatestBackpressureExample() {
        println("3️⃣ CollectLatest 백프레셔 전략:")
        println("   새로운 값이 오면 현재 처리 중인 작업을 취소")
        
        val processedCount = AtomicInteger(0)
        val cancelledCount = AtomicInteger(0)
        
        val executionTime = measureTimeMillis {
            fastProducerFlow()
                .take(10) // 10개만 처리
                .collectLatest { reservation ->
                    try {
                        delay(150) // 처리 시간
                        processedCount.incrementAndGet()
                        println("    완료 처리: ${reservation.guestName}")
                    } catch (e: CancellationException) {
                        cancelledCount.incrementAndGet()
                        println("    취소됨: ${reservation.guestName}")
                        throw e
                    }
                }
        }
        
        println("    실행 시간: ${executionTime}ms")
        println("    완료 처리: ${processedCount.get()}개")
        println("    취소된 작업: ${cancelledCount.get()}개")
        println("    CollectLatest 특징: 최신 데이터 우선, 중간 작업 취소")
        println()
    }

    private suspend fun customBackpressureExample() {
        println("4️⃣ 커스텀 백프레셔 전략:")
        println("   조건부 드롭과 우선순위 기반 처리")
        
        val highPriorityCount = AtomicInteger(0)
        val normalPriorityCount = AtomicInteger(0)
        val droppedCount = AtomicInteger(0)
        
        val executionTime = measureTimeMillis {
            prioritizedReservationFlow()
                .customBackpressure(maxBuffer = 20)
                .collect { reservation ->
                    delay(80) // 처리 시간
                    
                    if (reservation.totalAmount > 500.0) {
                        highPriorityCount.incrementAndGet()
                        println("    🟢 고우선순위 처리: ${reservation.guestName} (${reservation.totalAmount}원)")
                    } else {
                        normalPriorityCount.incrementAndGet()
                        if (normalPriorityCount.get() <= 3) {
                            println("    🔵 일반 처리: ${reservation.guestName} (${reservation.totalAmount}원)")
                        }
                    }
                }
        }
        
        println("    실행 시간: ${executionTime}ms")
        println("    고우선순위 처리: ${highPriorityCount.get()}개")
        println("    일반 처리: ${normalPriorityCount.get()}개")
        println("    커스텀 전략 특징: 우선순위 기반 처리, 지능적 드롭")
        println()
    }

    private suspend fun dynamicBackpressureExample() {
        println("5️⃣ 동적 백프레셔 제어:")
        println("   시스템 부하에 따른 동적 백프레셔 조정")
        
        val processedCount = AtomicInteger(0)
        val systemLoad = AtomicInteger(1) // 1: 낮음, 2: 보통, 3: 높음
        
        val executionTime = measureTimeMillis {
            adaptiveReservationFlow()
                .dynamicBackpressure { systemLoad.get() }
                .collect { reservation ->
                    // 시스템 부하 시뮬레이션
                    val currentLoad = processedCount.get() / 20 + 1
                    systemLoad.set(minOf(currentLoad, 3))
                    
                    val processingTime = when (systemLoad.get()) {
                        1 -> 50L  // 낮은 부하
                        2 -> 100L // 보통 부하
                        else -> 200L // 높은 부하
                    }
                    
                    delay(processingTime)
                    processedCount.incrementAndGet()
                    
                    if (processedCount.get() % 10 == 0) {
                        println("    처리됨: ${processedCount.get()}개 (부하 레벨: ${systemLoad.get()})")
                    }
                }
        }
        
        println("    실행 시간: ${executionTime}ms")
        println("    총 처리: ${processedCount.get()}개")
        println("    동적 전략 특징: 시스템 상태 기반 자동 조정")
        println()
    }

    // ===== Project Reactor 백프레셔 예제들 =====

    private fun reactorBufferExample() {
        println("1️⃣ Reactor Buffer 전략:")
        
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.interval(Duration.ofMillis(10)) // 빠른 생산자
            .take(100)
            .map { createReactorReservation(it.toInt()) }
            .onBackpressureBuffer(50) // 50개까지 버퍼링
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { reservation ->
                Thread.sleep(100) // 느린 소비자
                val count = processedCount.incrementAndGet()
                if (count <= 5) {
                    println("    처리됨: ${reservation.guestName}")
                }
            }
            .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        println("    실행 시간: ${executionTime}ms")
        println("    처리된 항목: ${processedCount.get()}개")
        println("    Reactor Buffer 특징: Flux 내장 버퍼링")
        println()
    }

    private fun reactorDropExample() {
        println("2️⃣ Reactor Drop 전략:")
        
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.interval(Duration.ofMillis(10))
            .take(100)
            .map { createReactorReservation(it.toInt()) }
            .onBackpressureDrop { dropped ->
                // 드롭된 항목 로깅 (처음 5개만)
                if (processedCount.get() < 5) {
                    println("    드롭됨: ${dropped.guestName}")
                }
            }
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { reservation ->
                Thread.sleep(150)
                processedCount.incrementAndGet()
                println("    처리됨: ${reservation.guestName}")
            }
            .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        println("    실행 시간: ${executionTime}ms")
        println("    처리된 항목: ${processedCount.get()}개")
        println("    Drop 전략 특징: 처리 불가능한 항목 즉시 드롭")
        println()
    }

    private fun reactorLatestExample() {
        println("3️⃣ Reactor Latest 전략:")
        
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.interval(Duration.ofMillis(10))
            .take(50)
            .map { createReactorReservation(it.toInt()) }
            .onBackpressureLatest() // 최신 값만 유지
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { reservation ->
                Thread.sleep(200)
                processedCount.incrementAndGet()
                println("    처리됨: ${reservation.guestName} (${processedCount.get()}번째)")
            }
            .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        println("    실행 시간: ${executionTime}ms")
        println("    처리된 항목: ${processedCount.get()}개")
        println("    Latest 전략 특징: 최신 데이터만 유지")
        println()
    }

    private fun reactorErrorExample() {
        println("4️⃣ Reactor Error 전략:")
        
        try {
            Flux.interval(Duration.ofMillis(5)) // 매우 빠른 생산자
                .take(1000)
                .map { createReactorReservation(it.toInt()) }
                .onBackpressureError() // 백프레셔 발생 시 에러
                .publishOn(Schedulers.boundedElastic())
                .doOnNext { 
                    Thread.sleep(100) // 느린 소비자
                }
                .blockLast()
        } catch (e: Exception) {
            println("    백프레셔 에러 발생: ${e.javaClass.simpleName}")
            println("    Error 전략 특징: 백프레셔 상황에서 명시적 에러 발생")
        }
        println()
    }

    private fun reactorRequestBasedExample() {
        println("5️⃣ Reactor 요청 기반 백프레셔:")
        
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.generate<CreateReservationRequest> { sink ->
            sink.next(createReactorReservation(processedCount.get()))
        }
        .take(30)
        .publishOn(Schedulers.boundedElastic())
        .doOnRequest { requested ->
            println("    요청됨: ${requested}개 항목")
        }
        .doOnNext { reservation ->
            Thread.sleep(100)
            val count = processedCount.incrementAndGet()
            println("    처리됨: ${reservation.guestName} ($count/30)")
        }
        .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        println("    실행 시간: ${executionTime}ms")
        println("    요청 기반 특징: 소비자 요청에 따른 생산 제어")
        println()
    }

    // ===== 성능 비교 및 분석 =====

    suspend fun compareBackpressureStrategies() {
        println("📊 백프레셔 전략 성능 비교")
        println("=" * 60)

        val strategies = listOf(
            BackpressureStrategy("Flow Buffer", ::testFlowBuffer),
            BackpressureStrategy("Flow Conflate", ::testFlowConflate),
            BackpressureStrategy("Reactor Buffer", ::testReactorBuffer),
            BackpressureStrategy("Reactor Drop", ::testReactorDrop)
        )

        val results = mutableListOf<BackpressureResult>()

        strategies.forEach { strategy ->
            println("🔄 ${strategy.name} 테스트 중...")
            
            val result = if (strategy.name.startsWith("Flow")) {
                runBlocking { strategy.testFunction() }
            } else {
                strategy.testFunction()
            }
            
            results.add(result)
            println("   완료: ${result.processedItems}개 처리, ${result.executionTimeMs}ms")
        }

        // 결과 분석
        printBackpressureComparison(results)
    }

    private suspend fun testFlowBuffer(): BackpressureResult {
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        fastProducerFlow()
            .take(100)
            .buffer(50)
            .collect { 
                delay(50)
                processedCount.incrementAndGet()
            }
        
        val executionTime = System.currentTimeMillis() - startTime
        return BackpressureResult("Flow Buffer", processedCount.get(), executionTime, 0)
    }

    private suspend fun testFlowConflate(): BackpressureResult {
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        fastProducerFlow()
            .take(100)
            .conflate()
            .collect { 
                delay(50)
                processedCount.incrementAndGet()
            }
        
        val executionTime = System.currentTimeMillis() - startTime
        return BackpressureResult("Flow Conflate", processedCount.get(), executionTime, 100 - processedCount.get())
    }

    private fun testReactorBuffer(): BackpressureResult {
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.interval(Duration.ofMillis(10))
            .take(100)
            .onBackpressureBuffer(50)
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { 
                Thread.sleep(50)
                processedCount.incrementAndGet()
            }
            .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        return BackpressureResult("Reactor Buffer", processedCount.get(), executionTime, 0)
    }

    private fun testReactorDrop(): BackpressureResult {
        val processedCount = AtomicInteger(0)
        val droppedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        Flux.interval(Duration.ofMillis(10))
            .take(100)
            .onBackpressureDrop { droppedCount.incrementAndGet() }
            .publishOn(Schedulers.boundedElastic())
            .doOnNext { 
                Thread.sleep(50)
                processedCount.incrementAndGet()
            }
            .blockLast()
        
        val executionTime = System.currentTimeMillis() - startTime
        return BackpressureResult("Reactor Drop", processedCount.get(), executionTime, droppedCount.get())
    }

    private fun printBackpressureComparison(results: List<BackpressureResult>) {
        println("\n📈 백프레셔 전략 비교 결과:")
        println("-" * 80)
        
        results.forEach { result ->
            println("${result.strategy}:")
            println("  처리된 항목: ${result.processedItems}개")
            println("  실행 시간: ${result.executionTimeMs}ms")
            println("  처리량: ${"%.1f".format(result.processedItems * 1000.0 / result.executionTimeMs)} items/sec")
            println("  손실된 항목: ${result.lostItems}개")
            
            val efficiency = if (result.lostItems == 0) "100%" else 
                "${"%.1f".format((result.processedItems.toDouble() / (result.processedItems + result.lostItems)) * 100)}%"
            println("  처리 효율성: $efficiency")
            println()
        }

        // 권장사항
        println("🎯 백프레셔 전략 선택 가이드:")
        println("  Buffer: 모든 데이터 보존이 중요한 경우")
        println("  Conflate/Latest: 최신 데이터만 중요한 경우")
        println("  Drop: 시스템 안정성이 우선인 경우")
        println("  Error: 백프레셔 상황을 명시적으로 처리해야 하는 경우")
    }

    // ===== 헬퍼 함수들 =====

    private fun fastProducerFlow(): Flow<CreateReservationRequest> = flow {
        repeat(1000) { i ->
            emit(CreateReservationRequest(
                guestName = "Guest-$i",
                roomNumber = "Room-${i % 10 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 200.0 + (i % 100)
            ))
            delay(5) // 빠른 생산 (5ms 간격)
        }
    }

    private fun prioritizedReservationFlow(): Flow<CreateReservationRequest> = flow {
        repeat(100) { i ->
            val isHighPriority = i % 3 == 0
            emit(CreateReservationRequest(
                guestName = "Guest-$i",
                roomNumber = "Room-${i % 10 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = if (isHighPriority) 600.0 + (i % 100) else 200.0 + (i % 100)
            ))
            delay(10)
        }
    }

    private fun adaptiveReservationFlow(): Flow<CreateReservationRequest> = flow {
        repeat(100) { i ->
            emit(CreateReservationRequest(
                guestName = "Adaptive-Guest-$i",
                roomNumber = "Room-${i % 5 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 300.0 + Random.nextDouble(0.0, 200.0)
            ))
            delay(Random.nextLong(5, 15))
        }
    }

    private fun createReactorReservation(id: Int): CreateReservationRequest {
        return CreateReservationRequest(
            guestName = "Reactor-Guest-$id",
            roomNumber = "Room-${id % 10 + 1}",
            checkInDate = "2024-12-25",
            checkOutDate = "2024-12-27",
            totalAmount = 250.0 + (id % 100)
        )
    }

    // ===== 확장 함수들 =====

    private fun <T> Flow<T>.customBackpressure(maxBuffer: Int): Flow<T> = flow {
        val buffer = mutableListOf<T>()
        
        collect { item ->
            if (buffer.size < maxBuffer) {
                buffer.add(item)
            } else {
                // 우선순위 기반 드롭 (예: 낮은 금액의 예약 드롭)
                if (item is CreateReservationRequest && item.totalAmount > 500.0) {
                    // 고우선순위 항목은 기존 저우선순위 항목을 대체
                    val lowPriorityIndex = buffer.indexOfFirst { 
                        it is CreateReservationRequest && it.totalAmount < 400.0 
                    }
                    if (lowPriorityIndex != -1) {
                        buffer[lowPriorityIndex] = item
                    }
                }
                // 그렇지 않으면 드롭
            }
            
            // 버퍼에서 항목 방출
            if (buffer.isNotEmpty()) {
                val itemToEmit = buffer.removeFirst()
                emit(itemToEmit)
            }
        }
        
        // 남은 버퍼 항목들 처리
        buffer.forEach { emit(it) }
    }

    private fun <T> Flow<T>.dynamicBackpressure(loadProvider: () -> Int): Flow<T> = flow {
        collect { item ->
            val currentLoad = loadProvider()
            
            when (currentLoad) {
                1 -> {
                    // 낮은 부하: 모든 항목 처리
                    emit(item)
                }
                2 -> {
                    // 보통 부하: 50% 항목만 처리
                    if (Random.nextBoolean()) {
                        emit(item)
                    }
                }
                else -> {
                    // 높은 부하: 25% 항목만 처리
                    if (Random.nextInt(4) == 0) {
                        emit(item)
                    }
                }
            }
        }
    }

    // ===== 데이터 클래스들 =====

    private data class BackpressureStrategy(
        val name: String,
        val testFunction: suspend () -> BackpressureResult
    )

    private data class BackpressureResult(
        val strategy: String,
        val processedItems: Int,
        val executionTimeMs: Long,
        val lostItems: Int
    )
}

// 문자열 반복을 위한 확장 함수
private operator fun String.times(n: Int): String = this.repeat(n)