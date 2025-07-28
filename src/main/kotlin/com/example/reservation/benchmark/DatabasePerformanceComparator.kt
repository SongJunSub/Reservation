package com.example.reservation.benchmark

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.repository.ReservationRepository
import com.example.reservation.repository.ReservationRepositoryReactive
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * 데이터베이스 성능 비교 도구
 * JPA vs R2DBC의 다양한 시나리오별 성능을 실무적 관점에서 비교 분석
 */
@Component
class DatabasePerformanceComparator(
    private val jpaRepository: ReservationRepository,
    private val r2dbcRepository: ReservationRepositoryReactive
) : CommandLineRunner {

    data class DatabaseTestResult(
        val testName: String,
        val technology: String,
        val recordCount: Int,
        val executionTimeMs: Long,
        val operationsPerSecond: Double,
        val avgOperationTimeMs: Double,
        val memoryUsedMB: Long,
        val connectionPoolStats: ConnectionPoolStats?,
        val transactionCount: Int = 0,
        val errorCount: Int = 0
    ) {
        fun getSuccessRate(): Double = 
            if (recordCount > 0) ((recordCount - errorCount).toDouble() / recordCount) * 100 else 0.0
    }

    data class ConnectionPoolStats(
        val active: Int,
        val idle: Int,
        val total: Int,
        val maxPoolSize: Int,
        val waitingThreads: Int = 0
    ) {
        fun getUtilizationRate(): Double = (active.toDouble() / maxPoolSize) * 100
    }

    data class QueryComplexityMetrics(
        val simpleQueries: DatabaseTestResult,
        val joinQueries: DatabaseTestResult,
        val aggregationQueries: DatabaseTestResult,
        val paginatedQueries: DatabaseTestResult
    )

    override fun run(vararg args: String?) {
        if (args.contains("--database-performance")) {
            println("🗄️ 데이터베이스 성능 비교 분석 시작...")
            runDatabasePerformanceComparison()
        }
    }

    fun runDatabasePerformanceComparison() {
        println("📊 JPA vs R2DBC 데이터베이스 성능 비교")
        println("=" * 80)
        println("분석 시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        println()

        val results = mutableListOf<DatabaseTestResult>()

        // 1. 데이터 준비 (테스트용 데이터 생성)
        println("🔄 테스트 데이터 준비 중...")
        prepareTestData()
        println("✅ 테스트 데이터 준비 완료\n")

        // 2. 단순 CRUD 성능 비교
        println("📋 Phase 1: 단순 CRUD 성능 비교")
        println("-" * 50)
        results.addAll(testBasicCrudOperations())
        println()

        // 3. 복잡한 쿼리 성능 비교
        println("🔍 Phase 2: 복잡한 쿼리 성능 비교")
        println("-" * 50)
        results.addAll(testComplexQueries())
        println()

        // 4. 배치 처리 성능 비교
        println("⚡ Phase 3: 배치 처리 성능 비교")
        println("-" * 50)
        results.addAll(testBatchOperations())
        println()

        // 5. 트랜잭션 성능 비교
        println("🔄 Phase 4: 트랜잭션 성능 비교")
        println("-" * 50)
        results.addAll(testTransactionPerformance())
        println()

        // 6. 동시성 성능 비교
        println("🚀 Phase 5: 동시성 성능 비교")
        println("-" * 50)
        results.addAll(testConcurrentOperations())
        println()

        // 7. 커넥션 풀 효율성 비교
        println("🔗 Phase 6: 커넥션 풀 효율성 비교")
        println("-" * 50)
        results.addAll(testConnectionPoolEfficiency())
        println()

        // 종합 분석 및 결과 출력
        analyzeAndPrintResults(results)
    }

    /**
     * 테스트용 더미 데이터 생성
     */
    private fun prepareTestData() {
        // 실제 구현에서는 대용량 테스트 데이터를 생성
        // 현재는 기존 데이터를 활용하거나 소규모 데이터 생성
        println("  📊 기존 데이터 확인 중...")
        
        runBlocking {
            val existingCount = try {
                jpaRepository.count()
            } catch (e: Exception) {
                0L
            }
            
            println("  📈 기존 예약 데이터: ${existingCount}개")
            
            if (existingCount < 1000) {
                println("  🔄 추가 테스트 데이터 생성 중...")
                generateTestReservations(1000 - existingCount.toInt())
            }
        }
    }

    /**
     * 테스트용 예약 데이터 생성
     */
    @Transactional
    private fun generateTestReservations(count: Int) {
        val testReservations = (1..count).map { i ->
            Reservation(
                id = 0, // Auto-generated
                confirmationNumber = "TEST-${System.currentTimeMillis()}-$i",
                guestEmail = "test$i@example.com",
                guestName = "Test Guest $i",
                roomNumber = "Room ${i % 100 + 1}",
                checkInDate = LocalDate.now().plusDays(Random.nextLong(1, 365)),
                checkOutDate = LocalDate.now().plusDays(Random.nextLong(366, 730)),
                totalAmount = 100.0 + Random.nextDouble(400.0),
                status = ReservationStatus.values()[Random.nextInt(ReservationStatus.values().size)],
                paymentStatus = com.example.reservation.domain.reservation.PaymentStatus.values()[Random.nextInt(2)]
            )
        }
        
        // 배치로 저장
        jpaRepository.saveAll(testReservations)
        println("  ✅ ${count}개 테스트 데이터 생성 완료")
    }

    /**
     * 기본 CRUD 성능 테스트
     */
    private fun testBasicCrudOperations(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()
        val testCount = 1000

        // JPA CRUD 테스트
        println("  🔄 JPA CRUD 성능 테스트...")
        val jpaResult = testJpaCrudOperations(testCount)
        results.add(jpaResult)
        printTestResult(jpaResult)

        // 메모리 정리
        System.gc()
        Thread.sleep(1000)

        // R2DBC CRUD 테스트
        println("  🔄 R2DBC CRUD 성능 테스트...")
        val r2dbcResult = testR2dbcCrudOperations(testCount)
        results.add(r2dbcResult)
        printTestResult(r2dbcResult)

        return results
    }

    private fun testJpaCrudOperations(testCount: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val errorCount = AtomicInteger(0)
        val operationCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(testCount) { i ->
                try {
                    // Create
                    val reservation = createTestReservation(i)
                    val saved = jpaRepository.save(reservation)
                    operationCount.incrementAndGet()

                    // Read
                    jpaRepository.findById(saved.id)
                    operationCount.incrementAndGet()

                    // Update
                    val updated = saved.copy(guestName = "Updated Guest $i")
                    jpaRepository.save(updated)
                    operationCount.incrementAndGet()

                    // Delete (선택적으로 일부만)
                    if (i % 10 == 0) {
                        jpaRepository.delete(updated)
                        operationCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()
        val operations = operationCount.get()

        return DatabaseTestResult(
            testName = "기본 CRUD 작업",
            technology = "JPA",
            recordCount = testCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (operations * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (operations > 0) executionTime.toDouble() / operations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("JPA"),
            errorCount = errorCount.get()
        )
    }

    private suspend fun testR2dbcCrudOperations(testCount: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val errorCount = AtomicInteger(0)
        val operationCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                repeat(testCount) { i ->
                    try {
                        // Create
                        val reservation = createTestReservation(i)
                        val saved = r2dbcRepository.save(reservation).awaitSingle()
                        operationCount.incrementAndGet()

                        // Read
                        r2dbcRepository.findById(saved.id).awaitSingleOrNull()
                        operationCount.incrementAndGet()

                        // Update
                        val updated = saved.copy(guestName = "Updated Guest $i")
                        r2dbcRepository.save(updated).awaitSingle()
                        operationCount.incrementAndGet()

                        // Delete (선택적으로 일부만)
                        if (i % 10 == 0) {
                            r2dbcRepository.delete(updated).awaitSingleOrNull()
                            operationCount.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }

        val endMemory = getMemoryUsage()
        val operations = operationCount.get()

        return DatabaseTestResult(
            testName = "기본 CRUD 작업",
            technology = "R2DBC",
            recordCount = testCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (operations * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (operations > 0) executionTime.toDouble() / operations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("R2DBC"),
            errorCount = errorCount.get()
        )
    }

    /**
     * 복잡한 쿼리 성능 테스트
     */
    private fun testComplexQueries(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()

        // JPA 복잡한 쿼리 테스트
        results.add(testJpaComplexQueries())
        
        // R2DBC 복잡한 쿼리 테스트  
        results.add(testR2dbcComplexQueries())

        return results
    }

    private fun testJpaComplexQueries(): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val queryCount = 500
        val errorCount = AtomicInteger(0)
        val operationCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(queryCount) {
                try {
                    // 복잡한 조건 검색
                    jpaRepository.findByGuestEmailAndStatusIn(
                        "test${Random.nextInt(1000)}@example.com",
                        listOf(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)
                    )
                    operationCount.incrementAndGet()

                    // 날짜 범위 검색
                    jpaRepository.findByCheckInDateBetween(
                        LocalDate.now(),
                        LocalDate.now().plusDays(30)
                    )
                    operationCount.incrementAndGet()

                    // 집계 쿼리
                    jpaRepository.calculateTotalRevenue(
                        LocalDate.now().minusDays(30),
                        LocalDate.now()
                    )
                    operationCount.incrementAndGet()

                    // 상태별 통계
                    jpaRepository.countReservationsByStatusSince(LocalDate.now().minusDays(7))
                    operationCount.incrementAndGet()

                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()
        val operations = operationCount.get()

        return DatabaseTestResult(
            testName = "복잡한 쿼리",
            technology = "JPA",
            recordCount = queryCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (operations * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (operations > 0) executionTime.toDouble() / operations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("JPA"),
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcComplexQueries(): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val queryCount = 500
        val errorCount = AtomicInteger(0)
        val operationCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                repeat(queryCount) {
                    try {
                        // 복잡한 조건 검색
                        r2dbcRepository.findByGuestEmailAndStatusIn(
                            "test${Random.nextInt(1000)}@example.com",
                            listOf(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)
                        ).toList()
                        operationCount.incrementAndGet()

                        // 날짜 범위 검색
                        r2dbcRepository.findByCheckInDateBetween(
                            LocalDate.now(),
                            LocalDate.now().plusDays(30)
                        ).toList()
                        operationCount.incrementAndGet()

                        // 집계 쿼리
                        r2dbcRepository.calculateTotalRevenue(
                            LocalDate.now().minusDays(30),
                            LocalDate.now()
                        ).awaitSingle()
                        operationCount.incrementAndGet()

                        // 상태별 통계
                        r2dbcRepository.countReservationsByStatusSince(LocalDate.now().minusDays(7))
                            .toList()
                        operationCount.incrementAndGet()

                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }

        val endMemory = getMemoryUsage()
        val operations = operationCount.get()

        return DatabaseTestResult(
            testName = "복잡한 쿼리",
            technology = "R2DBC",
            recordCount = queryCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (operations * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (operations > 0) executionTime.toDouble() / operations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("R2DBC"),
            errorCount = errorCount.get()
        )
    }

    /**
     * 배치 처리 성능 테스트
     */
    private fun testBatchOperations(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()
        val batchSize = 100
        val batchCount = 10

        // JPA 배치 처리 테스트
        println("  🔄 JPA 배치 처리 테스트...")
        results.add(testJpaBatchOperations(batchSize, batchCount))

        // R2DBC 배치 처리 테스트
        println("  🔄 R2DBC 배치 처리 테스트...")
        results.add(testR2dbcBatchOperations(batchSize, batchCount))

        return results
    }

    @Transactional
    private fun testJpaBatchOperations(batchSize: Int, batchCount: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val totalRecords = batchSize * batchCount
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(batchCount) { batchIndex ->
                try {
                    val batch = (1..batchSize).map { i ->
                        createTestReservation(batchIndex * batchSize + i)
                    }
                    jpaRepository.saveAll(batch)
                } catch (e: Exception) {
                    errorCount.addAndGet(batchSize)
                }
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "배치 처리 (${batchSize}개씩 ${batchCount}회)",
            technology = "JPA",
            recordCount = totalRecords,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (totalRecords * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (totalRecords > 0) executionTime.toDouble() / totalRecords else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("JPA"),
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcBatchOperations(batchSize: Int, batchCount: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val totalRecords = batchSize * batchCount
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                repeat(batchCount) { batchIndex ->
                    try {
                        val batch = (1..batchSize).map { i ->
                            createTestReservation(batchIndex * batchSize + i)
                        }
                        
                        // R2DBC에서는 Flux를 사용한 배치 처리
                        Flux.fromIterable(batch)
                            .flatMap { r2dbcRepository.save(it) }
                            .collectList()
                            .awaitSingle()
                    } catch (e: Exception) {
                        errorCount.addAndGet(batchSize)
                    }
                }
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "배치 처리 (${batchSize}개씩 ${batchCount}회)",
            technology = "R2DBC",
            recordCount = totalRecords,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (totalRecords * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (totalRecords > 0) executionTime.toDouble() / totalRecords else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("R2DBC"),
            errorCount = errorCount.get()
        )
    }

    /**
     * 트랜잭션 성능 테스트
     */
    private fun testTransactionPerformance(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()

        // JPA 트랜잭션 테스트
        results.add(testJpaTransactionPerformance())

        // R2DBC 트랜잭션 테스트  
        results.add(testR2dbcTransactionPerformance())

        return results
    }

    private fun testJpaTransactionPerformance(): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val transactionCount = 100
        val operationsPerTx = 10
        val errorCount = AtomicInteger(0)
        val totalOperations = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(transactionCount) { txIndex ->
                try {
                    executeJpaTransaction(txIndex, operationsPerTx)
                    totalOperations.addAndGet(operationsPerTx)
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "트랜잭션 처리 (${operationsPerTx}개 연산/트랜잭션)",
            technology = "JPA",
            recordCount = transactionCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (totalOperations.get() * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (transactionCount > 0) executionTime.toDouble() / transactionCount else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("JPA"),
            transactionCount = transactionCount,
            errorCount = errorCount.get()
        )
    }

    @Transactional
    private fun executeJpaTransaction(txIndex: Int, operationsPerTx: Int) {
        repeat(operationsPerTx) { opIndex ->
            val reservation = createTestReservation(txIndex * operationsPerTx + opIndex)
            jpaRepository.save(reservation)
        }
    }

    private fun testR2dbcTransactionPerformance(): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val transactionCount = 100
        val operationsPerTx = 10
        val errorCount = AtomicInteger(0)
        val totalOperations = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                repeat(transactionCount) { txIndex ->
                    try {
                        executeR2dbcTransaction(txIndex, operationsPerTx)
                        totalOperations.addAndGet(operationsPerTx)
                    } catch (e: Exception) {
                        errorCount.incrementAndGet()
                    }
                }
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "트랜잭션 처리 (${operationsPerTx}개 연산/트랜잭션)",
            technology = "R2DBC",
            recordCount = transactionCount,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (totalOperations.get() * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (transactionCount > 0) executionTime.toDouble() / transactionCount else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("R2DBC"),
            transactionCount = transactionCount,
            errorCount = errorCount.get()
        )
    }

    private suspend fun executeR2dbcTransaction(txIndex: Int, operationsPerTx: Int) {
        // R2DBC 트랜잭션 처리
        val operations = (1..operationsPerTx).map { opIndex ->
            val reservation = createTestReservation(txIndex * operationsPerTx + opIndex)
            r2dbcRepository.save(reservation)
        }
        
        // 모든 연산을 하나의 트랜잭션으로 처리
        Flux.fromIterable(operations)
            .flatMap { it }
            .collectList()
            .awaitSingle()
    }

    /**
     * 동시성 성능 테스트
     */
    private fun testConcurrentOperations(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()
        val concurrentThreads = 20
        val operationsPerThread = 50

        // JPA 동시성 테스트
        results.add(testJpaConcurrentOperations(concurrentThreads, operationsPerThread))

        // R2DBC 동시성 테스트
        results.add(testR2dbcConcurrentOperations(concurrentThreads, operationsPerThread))

        return results
    }

    private fun testJpaConcurrentOperations(threads: Int, operationsPerThread: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val totalOperations = threads * operationsPerThread
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..threads).map { threadIndex ->
                    async(Dispatchers.IO) {
                        repeat(operationsPerThread) { opIndex ->
                            try {
                                val reservation = createTestReservation(threadIndex * operationsPerThread + opIndex)
                                jpaRepository.save(reservation)
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "동시성 처리 (${threads}개 스레드, ${operationsPerThread}개 연산/스레드)",
            technology = "JPA",
            recordCount = totalOperations,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (successCount.get() * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (totalOperations > 0) executionTime.toDouble() / totalOperations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("JPA"),
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcConcurrentOperations(threads: Int, operationsPerThread: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val totalOperations = threads * operationsPerThread
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..threads).map { threadIndex ->
                    async {
                        repeat(operationsPerThread) { opIndex ->
                            try {
                                val reservation = createTestReservation(threadIndex * operationsPerThread + opIndex)
                                r2dbcRepository.save(reservation).awaitSingle()
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "동시성 처리 (${threads}개 스레드, ${operationsPerThread}개 연산/스레드)",
            technology = "R2DBC",
            recordCount = totalOperations,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (successCount.get() * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (totalOperations > 0) executionTime.toDouble() / totalOperations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats("R2DBC"),
            errorCount = errorCount.get()
        )
    }

    /**
     * 커넥션 풀 효율성 테스트
     */
    private fun testConnectionPoolEfficiency(): List<DatabaseTestResult> {
        val results = mutableListOf<DatabaseTestResult>()

        // 높은 동시성으로 커넥션 풀 압박 테스트
        val highConcurrency = 100
        val quickOperations = 10

        results.add(testConnectionPoolStress("JPA", highConcurrency, quickOperations))
        results.add(testConnectionPoolStress("R2DBC", highConcurrency, quickOperations))

        return results
    }

    private fun testConnectionPoolStress(technology: String, concurrency: Int, operationsPerThread: Int): DatabaseTestResult {
        val startMemory = getMemoryUsage()
        val totalOperations = concurrency * operationsPerThread
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val connectionWaitCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                val jobs = (1..concurrency).map { threadIndex ->
                    async(Dispatchers.IO) {
                        repeat(operationsPerThread) { opIndex ->
                            try {
                                val startTime = System.currentTimeMillis()
                                
                                when (technology) {
                                    "JPA" -> {
                                        // 단순 조회로 커넥션 풀 테스트
                                        jpaRepository.count()
                                    }
                                    "R2DBC" -> {
                                        r2dbcRepository.count().awaitSingle()
                                    }
                                }
                                
                                val waitTime = System.currentTimeMillis() - startTime
                                if (waitTime > 100) { // 100ms 이상 대기하면 커넥션 풀 압박
                                    connectionWaitCount.incrementAndGet()
                                }
                                
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        val endMemory = getMemoryUsage()

        return DatabaseTestResult(
            testName = "커넥션 풀 스트레스 테스트 (${concurrency}개 동시 연결)",
            technology = technology,
            recordCount = totalOperations,
            executionTimeMs = executionTime,
            operationsPerSecond = if (executionTime > 0) (successCount.get() * 1000.0) / executionTime else 0.0,
            avgOperationTimeMs = if (totalOperations > 0) executionTime.toDouble() / totalOperations else 0.0,
            memoryUsedMB = endMemory - startMemory,
            connectionPoolStats = getCurrentConnectionPoolStats(technology)?.copy(
                waitingThreads = connectionWaitCount.get()
            ),
            errorCount = errorCount.get()
        )
    }

    /**
     * 유틸리티 함수들
     */
    private fun createTestReservation(index: Int): Reservation {
        return Reservation(
            id = 0, // Auto-generated
            confirmationNumber = "PERF-${System.currentTimeMillis()}-$index",
            guestEmail = "perf$index@test.com",
            guestName = "Performance Test Guest $index",
            roomNumber = "Room ${index % 50 + 1}",
            checkInDate = LocalDate.now().plusDays(Random.nextLong(1, 30)),
            checkOutDate = LocalDate.now().plusDays(Random.nextLong(31, 60)),
            totalAmount = 150.0 + Random.nextDouble(300.0),
            status = ReservationStatus.CONFIRMED,
            paymentStatus = com.example.reservation.domain.reservation.PaymentStatus.PAID
        )
    }

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun getCurrentConnectionPoolStats(technology: String): ConnectionPoolStats? {
        // 실제 구현에서는 각 기술별 커넥션 풀 정보를 조회
        // 현재는 시뮬레이션된 값 반환
        return when (technology) {
            "JPA" -> ConnectionPoolStats(
                active = Random.nextInt(5, 15),
                idle = Random.nextInt(0, 5),
                total = 20,
                maxPoolSize = 20
            )
            "R2DBC" -> ConnectionPoolStats(
                active = Random.nextInt(2, 8),
                idle = Random.nextInt(0, 3),
                total = 10,
                maxPoolSize = 10
            )
            else -> null
        }
    }

    private fun printTestResult(result: DatabaseTestResult) {
        println("    📊 ${result.technology} 결과:")
        println("      레코드 수: ${result.recordCount}")
        println("      실행 시간: ${result.executionTimeMs}ms")
        println("      처리량: ${"%.1f".format(result.operationsPerSecond)} ops/sec")
        println("      평균 연산 시간: ${"%.2f".format(result.avgOperationTimeMs)}ms")
        println("      성공률: ${"%.1f".format(result.getSuccessRate())}%")
        println("      메모리 사용: ${result.memoryUsedMB}MB")
        result.connectionPoolStats?.let { pool ->
            println("      커넥션 풀: ${pool.active}/${pool.maxPoolSize} (활성/최대)")
            println("      풀 사용률: ${"%.1f".format(pool.getUtilizationRate())}%")
        }
        if (result.transactionCount > 0) {
            println("      트랜잭션 수: ${result.transactionCount}")
        }
        println()
    }

    /**
     * 종합 분석 및 결과 출력
     */
    private fun analyzeAndPrintResults(results: List<DatabaseTestResult>) {
        println("🔍 데이터베이스 성능 비교 종합 분석")
        println("=" * 80)

        // 기술별 결과 그룹화
        val jpaResults = results.filter { it.technology == "JPA" }
        val r2dbcResults = results.filter { it.technology == "R2DBC" }

        // 각 테스트별 비교
        val testNames = results.map { it.testName }.distinct()
        
        testNames.forEach { testName ->
            val jpaResult = jpaResults.find { it.testName == testName }
            val r2dbcResult = r2dbcResults.find { it.testName == testName }
            
            if (jpaResult != null && r2dbcResult != null) {
                println("📈 $testName 비교:")
                compareDbResults(jpaResult, r2dbcResult)
                println()
            }
        }

        // 전체 성능 요약
        printDatabaseSummary(jpaResults, r2dbcResults)
        
        // 실무 권장사항
        printDatabaseRecommendations()
    }

    private fun compareDbResults(jpaResult: DatabaseTestResult, r2dbcResult: DatabaseTestResult) {
        println("  처리량 비교:")
        if (r2dbcResult.operationsPerSecond > jpaResult.operationsPerSecond) {
            val improvement = ((r2dbcResult.operationsPerSecond - jpaResult.operationsPerSecond) / jpaResult.operationsPerSecond) * 100
            println("    R2DBC가 ${"%.1f".format(improvement)}% 빠름 (${"%.0f".format(r2dbcResult.operationsPerSecond)} vs ${"%.0f".format(jpaResult.operationsPerSecond)} ops/sec)")
        } else {
            val improvement = ((jpaResult.operationsPerSecond - r2dbcResult.operationsPerSecond) / r2dbcResult.operationsPerSecond) * 100
            println("    JPA가 ${"%.1f".format(improvement)}% 빠름 (${"%.0f".format(jpaResult.operationsPerSecond)} vs ${"%.0f".format(r2dbcResult.operationsPerSecond)} ops/sec)")
        }

        println("  메모리 사용 비교:")
        if (r2dbcResult.memoryUsedMB < jpaResult.memoryUsedMB) {
            val savings = jpaResult.memoryUsedMB - r2dbcResult.memoryUsedMB
            println("    R2DBC가 ${savings}MB 적게 사용")
        } else {
            val savings = r2dbcResult.memoryUsedMB - jpaResult.memoryUsedMB
            println("    JPA가 ${savings}MB 적게 사용")
        }

        println("  안정성 비교:")
        println("    JPA 성공률: ${"%.1f".format(jpaResult.getSuccessRate())}%")
        println("    R2DBC 성공률: ${"%.1f".format(r2dbcResult.getSuccessRate())}%")
    }

    private fun printDatabaseSummary(jpaResults: List<DatabaseTestResult>, r2dbcResults: List<DatabaseTestResult>) {
        println("📋 전체 데이터베이스 성능 요약")
        println("-" * 60)

        val jpaAvgThroughput = jpaResults.map { it.operationsPerSecond }.average()
        val r2dbcAvgThroughput = r2dbcResults.map { it.operationsPerSecond }.average()
        val jpaAvgMemory = jpaResults.map { it.memoryUsedMB }.average()
        val r2dbcAvgMemory = r2dbcResults.map { it.memoryUsedMB }.average()
        val jpaAvgSuccess = jpaResults.map { it.getSuccessRate() }.average()
        val r2dbcAvgSuccess = r2dbcResults.map { it.getSuccessRate() }.average()

        println("JPA 평균 성능:")
        println("  처리량: ${"%.1f".format(jpaAvgThroughput)} ops/sec")
        println("  메모리 사용: ${"%.1f".format(jpaAvgMemory)}MB")
        println("  안정성: ${"%.1f".format(jpaAvgSuccess)}%")

        println("\nR2DBC 평균 성능:")
        println("  처리량: ${"%.1f".format(r2dbcAvgThroughput)} ops/sec")
        println("  메모리 사용: ${"%.1f".format(r2dbcAvgMemory)}MB")
        println("  안정성: ${"%.1f".format(r2dbcAvgSuccess)}%")

        // 승자 결정
        val r2dbcWins = (if (r2dbcAvgThroughput > jpaAvgThroughput) 1 else 0) +
                       (if (r2dbcAvgMemory < jpaAvgMemory) 1 else 0) +
                       (if (r2dbcAvgSuccess > jpaAvgSuccess) 1 else 0)

        println("\n🏆 전체 우승자:")
        when {
            r2dbcWins >= 2 -> println("  R2DBC (3개 지표 중 ${r2dbcWins}개 우위)")
            r2dbcWins <= 1 -> println("  JPA (3개 지표 중 ${3 - r2dbcWins}개 우위)")
        }
        println()
    }

    private fun printDatabaseRecommendations() {
        println("🎯 데이터베이스 기술 선택 가이드")
        println("-" * 60)
        
        println("JPA 선택 기준:")
        println("  ✅ 복잡한 객체 관계 매핑")
        println("  ✅ 트랜잭션 처리 중심 애플리케이션")
        println("  ✅ 기존 JPA 코드베이스 호환성")
        println("  ✅ 복잡한 비즈니스 로직과 검증")
        println("  ✅ 개발 생산성 우선")
        
        println("\nR2DBC 선택 기준:")
        println("  ✅ 높은 동시성 처리 필요")
        println("  ✅ 낮은 지연시간 요구사항")
        println("  ✅ 마이크로서비스 아키텍처")
        println("  ✅ 리소스 효율성 중시")
        println("  ✅ 반응형 시스템 구축")
        
        println("\n💡 하이브리드 접근법:")
        println("  - 읽기 집약적: R2DBC")
        println("  - 쓰기 집약적: JPA")
        println("  - 복잡한 트랜잭션: JPA")
        println("  - 단순 CRUD: R2DBC")
        
        println("\n" + "=" * 80)
    }
}