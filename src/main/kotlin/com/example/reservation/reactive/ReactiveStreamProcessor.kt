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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * 실제 예약 시스템에서 사용할 수 있는 Reactive Stream 프로세서
 * 백프레셔 처리와 함께 실무 적용 가능한 패턴들을 구현
 */
@Component
class ReactiveStreamProcessor {

    /**
     * 예약 요청 스트림 처리기
     * 대량의 예약 요청을 효율적으로 처리하면서 백프레셔를 적절히 관리
     */
    suspend fun processReservationStream() {
        println("🏨 실시간 예약 요청 스트림 처리")
        println("=" * 50)

        val processedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val totalRevenue = AtomicLong(0)

        try {
            createReservationRequestStream()
                .buffer(capacity = 100) // 백프레셔 처리: 100개까지 버퍼링
                .map { request -> validateReservationRequest(request) }
                .filter { it.isValid }
                .flowOn(Dispatchers.IO) // I/O 작업을 별도 디스패처에서 처리
                .map { validRequest -> 
                    processReservationBusiness(validRequest.request)
                }
                .catch { e ->
                    errorCount.incrementAndGet()
                    println("    ⚠️ 스트림 처리 오류: ${e.message}")
                    emit(ProcessingResult.error(e.message ?: "Unknown error"))
                }
                .collect { result ->
                    if (result.success) {
                        val count = processedCount.incrementAndGet()
                        totalRevenue.addAndGet(result.revenue.toLong())
                        
                        if (count <= 10 || count % 50 == 0) {
                            println("    ✅ 예약 처리 완료: ${result.reservationId} (${count}번째)")
                        }
                    } else {
                        errorCount.incrementAndGet()
                        println("    ❌ 예약 처리 실패: ${result.error}")
                    }
                }
        } catch (e: Exception) {
            println("    💥 치명적 오류: ${e.message}")
        }

        // 처리 결과 요약
        println("\n📊 스트림 처리 결과:")
        println("    성공한 예약: ${processedCount.get()}개")
        println("    실패한 요청: ${errorCount.get()}개")
        println("    총 수익: ${String.format("%,d", totalRevenue.get())}원")
        println("    성공률: ${"%.1f".format(processedCount.get() * 100.0 / (processedCount.get() + errorCount.get()))}%")
    }

    /**
     * 백프레셔를 고려한 배치 처리
     * 대량의 예약 데이터를 배치로 나누어 안정적으로 처리
     */
    suspend fun processBatchReservations() {
        println("\n🔄 배치 예약 처리 (백프레셔 적용)")
        println("=" * 50)

        val totalBatches = AtomicInteger(0)
        val successfulBatches = AtomicInteger(0)
        val totalProcessed = AtomicInteger(0)

        createLargeBatchStream()
            .chunked(25) // 25개씩 배치로 묶기
            .buffer(capacity = 5) // 최대 5개 배치까지 버퍼링
            .flowOn(Dispatchers.Default)
            .collect { batch ->
                val batchNumber = totalBatches.incrementAndGet()
                println("    🔄 배치 $batchNumber 처리 시작 (${batch.size}개 항목)")
                
                try {
                    // 배치 내 병렬 처리
                    val results = batch.map { request ->
                        async {
                            delay(Random.nextLong(50, 150)) // 가변 처리 시간
                            processReservationBusiness(request)
                        }
                    }.awaitAll()
                    
                    val batchSuccessCount = results.count { it.success }
                    totalProcessed.addAndGet(batchSuccessCount)
                    
                    if (batchSuccessCount == batch.size) {
                        successfulBatches.incrementAndGet()
                        println("    ✅ 배치 $batchNumber 완료: ${batchSuccessCount}/${batch.size} 성공")
                    } else {
                        println("    ⚠️ 배치 $batchNumber 부분 성공: ${batchSuccessCount}/${batch.size} 성공")
                    }
                    
                } catch (e: Exception) {
                    println("    ❌ 배치 $batchNumber 실패: ${e.message}")
                }
            }

        println("\n📊 배치 처리 결과:")
        println("    총 배치: ${totalBatches.get()}개")
        println("    성공한 배치: ${successfulBatches.get()}개")
        println("    총 처리된 예약: ${totalProcessed.get()}개")
        println("    배치 성공률: ${"%.1f".format(successfulBatches.get() * 100.0 / totalBatches.get())}%")
    }

    /**
     * 실시간 모니터링과 백프레셔 조정
     * 시스템 부하에 따라 동적으로 처리 속도를 조절
     */
    suspend fun processWithDynamicBackpressure() {
        println("\n⚡ 동적 백프레셔 제어")
        println("=" * 50)

        val systemLoad = AtomicInteger(1) // 1: 낮음, 2: 보통, 3: 높음
        val processedCount = AtomicInteger(0)
        val droppedCount = AtomicInteger(0)

        createHighVolumeStream()
            .dynamicallyThrottle { systemLoad.get() }
            .buffer(capacity = Channel.UNLIMITED) { overflow ->
                droppedCount.incrementAndGet()
                println("    🗑️ 오버플로우로 인한 드롭: ${overflow.guestName}")
            }
            .flowOn(Dispatchers.IO)
            .collect { request ->
                // 시스템 부하 시뮬레이션
                val currentLoad = (processedCount.get() / 30) + 1
                systemLoad.set(minOf(currentLoad, 3))

                val processingDelay = when (systemLoad.get()) {
                    1 -> 30L   // 낮은 부하: 빠른 처리
                    2 -> 60L   // 보통 부하: 보통 처리
                    else -> 120L // 높은 부하: 느린 처리
                }

                delay(processingDelay)
                val count = processedCount.incrementAndGet()

                if (count % 20 == 0) {
                    println("    📈 처리 현황: ${count}개 완료, 시스템 부하: ${systemLoad.get()}")
                }
            }

        println("\n📊 동적 백프레셔 결과:")
        println("    처리된 요청: ${processedCount.get()}개")
        println("    드롭된 요청: ${droppedCount.get()}개")
        println("    최종 시스템 부하: ${systemLoad.get()}")
    }

    /**
     * 우선순위 기반 스트림 처리
     * VIP 고객과 일반 고객의 예약을 차별적으로 처리
     */
    suspend fun processPriorityBasedStream() {
        println("\n👑 우선순위 기반 스트림 처리")
        println("=" * 50)

        val vipProcessed = AtomicInteger(0)
        val regularProcessed = AtomicInteger(0)
        val vipRevenue = AtomicLong(0)
        val regularRevenue = AtomicLong(0)

        createMixedPriorityStream()
            .priorityBuffer(vipCapacity = 50, regularCapacity = 20)
            .collect { priorityRequest ->
                val result = processReservationBusiness(priorityRequest.request)
                
                if (result.success) {
                    if (priorityRequest.isVip) {
                        val count = vipProcessed.incrementAndGet()
                        vipRevenue.addAndGet(result.revenue.toLong())
                        println("    👑 VIP 예약 처리: ${result.reservationId} ($count 번째)")
                    } else {
                        val count = regularProcessed.incrementAndGet()
                        regularRevenue.addAndGet(result.revenue.toLong())
                        if (count <= 5 || count % 20 == 0) {
                            println("    👤 일반 예약 처리: ${result.reservationId} ($count 번째)")
                        }
                    }
                }
            }

        println("\n📊 우선순위 처리 결과:")
        println("    VIP 예약: ${vipProcessed.get()}개, 수익: ${String.format("%,d", vipRevenue.get())}원")
        println("    일반 예약: ${regularProcessed.get()}개, 수익: ${String.format("%,d", regularRevenue.get())}원")
        println("    VIP 평균 수익: ${if (vipProcessed.get() > 0) vipRevenue.get() / vipProcessed.get() else 0}원")
        println("    일반 평균 수익: ${if (regularProcessed.get() > 0) regularRevenue.get() / regularProcessed.get() else 0}원")
    }

    /**
     * 오류 복구를 포함한 탄력적 스트림 처리
     * 일시적 오류 상황에서도 스트림 처리를 계속 유지
     */
    suspend fun processResilientStream() {
        println("\n🛡️ 탄력적 스트림 처리 (오류 복구)")
        println("=" * 50)

        val processedCount = AtomicInteger(0)
        val retryCount = AtomicInteger(0)
        val permanentFailures = AtomicInteger(0)

        createUnreliableStream()
            .retry(retries = 3) { error ->
                retryCount.incrementAndGet()
                println("    🔄 재시도 중... (${retryCount.get()}번째, 오류: ${error.message})")
                delay(1000) // 재시도 전 대기
                true // 항상 재시도
            }
            .catch { error ->
                permanentFailures.incrementAndGet()
                println("    💥 영구 실패: ${error.message}")
                // 기본값으로 빈 결과 방출하여 스트림 계속 진행
                emit(ProcessingResult.error("Permanent failure"))
            }
            .collect { result ->
                if (result.success) {
                    val count = processedCount.incrementAndGet()
                    if (count <= 10 || count % 25 == 0) {
                        println("    ✅ 복구 후 처리: ${result.reservationId} ($count 번째)")
                    }
                }
            }

        println("\n📊 탄력적 처리 결과:")
        println("    성공 처리: ${processedCount.get()}개")
        println("    재시도 횟수: ${retryCount.get()}회")
        println("    영구 실패: ${permanentFailures.get()}개")
    }

    // ===== 스트림 생성 함수들 =====

    private fun createReservationRequestStream(): Flow<CreateReservationRequest> = flow {
        repeat(200) { i ->
            emit(CreateReservationRequest(
                guestName = "Customer-$i",
                roomNumber = "Room-${i % 20 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 150.0 + Random.nextDouble(50.0, 350.0)
            ))
            delay(Random.nextLong(10, 30)) // 가변 요청 간격
        }
    }

    private fun createLargeBatchStream(): Flow<CreateReservationRequest> = flow {
        repeat(500) { i ->
            emit(CreateReservationRequest(
                guestName = "Batch-Customer-$i",
                roomNumber = "Room-${i % 50 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 200.0 + (i % 300)
            ))
            delay(5) // 빠른 생성
        }
    }

    private fun createHighVolumeStream(): Flow<CreateReservationRequest> = flow {
        repeat(300) { i ->
            emit(CreateReservationRequest(
                guestName = "HighVolume-$i",
                roomNumber = "Room-${i % 15 + 1}",
                checkInDate = "2024-12-25",
                checkOutDate = "2024-12-27",
                totalAmount = 250.0 + Random.nextDouble(100.0, 200.0)
            ))
            delay(Random.nextLong(2, 8)) // 매우 빠른 요청
        }
    }

    private fun createMixedPriorityStream(): Flow<PriorityRequest> = flow {
        repeat(150) { i ->
            val isVip = i % 4 == 0 // 25%가 VIP
            val baseAmount = if (isVip) 500.0 else 200.0
            
            emit(PriorityRequest(
                request = CreateReservationRequest(
                    guestName = if (isVip) "VIP-Guest-$i" else "Regular-Guest-$i",
                    roomNumber = if (isVip) "Suite-${i % 5 + 1}" else "Room-${i % 15 + 1}",
                    checkInDate = "2024-12-25",
                    checkOutDate = "2024-12-27",
                    totalAmount = baseAmount + Random.nextDouble(0.0, 200.0)
                ),
                isVip = isVip,
                priority = if (isVip) 1 else 2
            ))
            delay(Random.nextLong(5, 15))
        }
    }

    private fun createUnreliableStream(): Flow<ProcessingResult> = flow {
        repeat(100) { i ->
            // 30% 확률로 오류 발생
            if (Random.nextDouble() < 0.3) {
                throw RuntimeException("Simulated service failure at item $i")
            }
            
            emit(ProcessingResult.success("Reliable-$i", 300.0))
            delay(Random.nextLong(20, 50))
        }
    }

    // ===== 비즈니스 로직 함수들 =====

    private suspend fun validateReservationRequest(request: CreateReservationRequest): ValidationResult {
        delay(10) // 검증 시간 시뮬레이션
        
        val isValid = request.guestName.isNotBlank() && 
                     request.roomNumber.isNotBlank() && 
                     request.totalAmount > 0
        
        return ValidationResult(request, isValid)
    }

    private suspend fun processReservationBusiness(request: CreateReservationRequest): ProcessingResult {
        delay(Random.nextLong(30, 80)) // 비즈니스 처리 시간
        
        // 5% 확률로 비즈니스 로직 실패
        if (Random.nextDouble() < 0.05) {
            return ProcessingResult.error("Business validation failed")
        }
        
        val reservationId = "RES-${System.currentTimeMillis()}-${Random.nextInt(1000)}"
        return ProcessingResult.success(reservationId, request.totalAmount)
    }

    // ===== 확장 함수들 =====

    private fun <T> Flow<T>.dynamicallyThrottle(loadProvider: () -> Int): Flow<T> = flow {
        collect { item ->
            val currentLoad = loadProvider()
            val shouldProcess = when (currentLoad) {
                1 -> true // 낮은 부하: 모든 항목 처리
                2 -> Random.nextDouble() < 0.7 // 보통 부하: 70% 처리
                else -> Random.nextDouble() < 0.4 // 높은 부하: 40% 처리
            }
            
            if (shouldProcess) {
                emit(item)
            }
        }
    }

    private fun <T> Flow<T>.buffer(capacity: Int, onOverflow: (T) -> Unit): Flow<T> = flow {
        val buffer = mutableListOf<T>()
        
        collect { item ->
            if (buffer.size < capacity) {
                buffer.add(item)
                if (buffer.isNotEmpty()) {
                    emit(buffer.removeFirst())
                }
            } else {
                onOverflow(item)
            }
        }
        
        // 남은 버퍼 항목들 처리
        buffer.forEach { emit(it) }
    }

    private fun Flow<PriorityRequest>.priorityBuffer(
        vipCapacity: Int, 
        regularCapacity: Int
    ): Flow<PriorityRequest> = flow {
        val vipBuffer = mutableListOf<PriorityRequest>()
        val regularBuffer = mutableListOf<PriorityRequest>()
        
        collect { request ->
            if (request.isVip) {
                if (vipBuffer.size < vipCapacity) {
                    vipBuffer.add(request)
                }
            } else {
                if (regularBuffer.size < regularCapacity) {
                    regularBuffer.add(request)
                }
            }
            
            // VIP 우선 처리
            while (vipBuffer.isNotEmpty()) {
                emit(vipBuffer.removeFirst())
            }
            
            // 일반 고객 처리 (VIP 버퍼가 비어있을 때만)
            if (vipBuffer.isEmpty() && regularBuffer.isNotEmpty()) {
                emit(regularBuffer.removeFirst())
            }
        }
        
        // 남은 버퍼 항목들 처리 (VIP 우선)
        vipBuffer.forEach { emit(it) }
        regularBuffer.forEach { emit(it) }
    }

    // ===== 데이터 클래스들 =====

    private data class ValidationResult(
        val request: CreateReservationRequest,
        val isValid: Boolean
    )

    private data class ProcessingResult(
        val success: Boolean,
        val reservationId: String = "",
        val revenue: Double = 0.0,
        val error: String = ""
    ) {
        companion object {
            fun success(id: String, revenue: Double) = ProcessingResult(true, id, revenue)
            fun error(message: String) = ProcessingResult(false, error = message)
        }
    }

    private data class PriorityRequest(
        val request: CreateReservationRequest,
        val isVip: Boolean,
        val priority: Int
    )
}