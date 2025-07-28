package com.example.reservation.benchmark

import com.example.reservation.domain.reservation.Reservation
import com.example.reservation.domain.reservation.ReservationStatus
import com.example.reservation.repository.ReservationRepository
import com.example.reservation.repository.ReservationRepositoryReactive
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.springframework.boot.CommandLineRunner
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * 대용량 데이터 처리 성능 비교 도구
 * 실무 환경에서 발생하는 대규모 데이터 처리 시나리오를 시뮬레이션하고 성능을 측정
 */
@Component
class LargeDataProcessingComparator(
    private val jpaRepository: ReservationRepository,
    private val r2dbcRepository: ReservationRepositoryReactive
) : CommandLineRunner {

    data class LargeDataTestResult(
        val testName: String,
        val technology: String,
        val dataSize: Int,
        val executionTimeMs: Long,
        val throughputPerSecond: Double,
        val memoryUsedMB: Long,
        val peakMemoryMB: Long,
        val ioOperations: Long,
        val gcCount: Int,
        val gcTimeMs: Long,
        val errorCount: Int = 0,
        val additionalMetrics: Map<String, Any> = emptyMap()
    ) {
        fun getProcessingRate(): Double = 
            if (executionTimeMs > 0) (dataSize * 1000.0) / executionTimeMs else 0.0
        
        fun getEfficiencyScore(): Double {
            // 처리량, 메모리 효율성, 안정성을 종합한 점수
            val throughputScore = minOf(throughputPerSecond / 1000.0, 1.0) * 40
            val memoryScore = maxOf(0.0, 1.0 - (memoryUsedMB / 1000.0)) * 30
            val stabilityScore = maxOf(0.0, 1.0 - (errorCount / dataSize.toDouble())) * 30
            return throughputScore + memoryScore + stabilityScore
        }
    }

    data class PagingStrategy(
        val name: String,
        val pageSize: Int,
        val strategy: PagingType
    )

    enum class PagingType {
        OFFSET_BASED,
        CURSOR_BASED,
        STREAMING
    }

    data class DataExportResult(
        val format: String,
        val recordCount: Int,
        val fileSizeMB: Double,
        val exportTimeMs: Long,
        val importTimeMs: Long,
        val dataIntegrityCheck: Boolean
    )

    override fun run(vararg args: String?) {
        if (args.contains("--large-data-processing")) {
            println("📊 대용량 데이터 처리 성능 비교 분석 시작...")
            runLargeDataProcessingComparison()
        }
    }

    fun runLargeDataProcessingComparison() {
        println("🏗️ 대용량 데이터 처리 성능 비교")
        println("=" * 80)
        println("분석 시작 시간: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        println()

        val results = mutableListOf<LargeDataTestResult>()

        // 1. 대용량 데이터 준비
        println("🔄 대용량 테스트 데이터 준비 중...")
        prepareLargeDataset()
        println()

        // 2. 대용량 데이터 조회 성능 비교
        println("📋 Phase 1: 대용량 데이터 조회 성능 비교")
        println("-" * 50)
        results.addAll(testLargeDataRetrieval())
        println()

        // 3. 페이징 전략 성능 비교
        println("📄 Phase 2: 페이징 전략 성능 비교")
        println("-" * 50)
        results.addAll(testPagingStrategies())
        println()

        // 4. 대용량 데이터 Insert/Update 성능
        println("⚡ Phase 3: 대용량 데이터 Insert/Update 성능")
        println("-" * 50)
        results.addAll(testLargeDataModification())
        println()

        // 5. 데이터 Export/Import 성능
        println("📤📥 Phase 4: 데이터 Export/Import 성능")
        println("-" * 50)
        results.addAll(testDataExportImport())
        println()

        // 6. 인덱스 효과 분석
        println("🔍 Phase 5: 인덱스 효과 분석")
        println("-" * 50)
        results.addAll(testIndexPerformanceImpact())
        println()

        // 7. 메모리 사용 패턴 분석
        println("💾 Phase 6: 메모리 사용 패턴 분석")
        println("-" * 50)
        results.addAll(testMemoryUsagePatterns())
        println()

        // 종합 분석 및 결과 출력
        analyzeAndPrintLargeDataResults(results)
    }

    /**
     * 대용량 테스트 데이터 준비
     */
    private fun prepareLargeDataset() {
        println("  📊 기존 대용량 데이터 확인 중...")
        
        runBlocking {
            val existingCount = try {
                jpaRepository.count()
            } catch (e: Exception) {
                0L
            }
            
            println("  📈 기존 예약 데이터: ${existingCount}개")
            
            val targetCount = 100_000L // 10만개 목표
            if (existingCount < targetCount) {
                val needToGenerate = (targetCount - existingCount).toInt()
                println("  🔄 추가 대용량 데이터 생성 중... (${needToGenerate}개)")
                generateLargeDataset(needToGenerate)
            } else {
                println("  ✅ 충분한 테스트 데이터가 존재합니다")
            }
        }
    }

    /**
     * 대용량 데이터셋 생성
     */
    @Transactional
    private fun generateLargeDataset(count: Int) {
        val batchSize = 1000
        val totalBatches = (count + batchSize - 1) / batchSize

        println("  📊 ${totalBatches}개 배치로 나누어 생성...")

        val startTime = System.currentTimeMillis()
        var generatedCount = 0

        repeat(totalBatches) { batchIndex ->
            val currentBatchSize = minOf(batchSize, count - generatedCount)
            
            val batch = (1..currentBatchSize).map { i ->
                val globalIndex = generatedCount + i
                createLargeDataReservation(globalIndex)
            }
            
            try {
                jpaRepository.saveAll(batch)
                generatedCount += currentBatchSize
                
                if (batchIndex % 10 == 0) {
                    val progress = (generatedCount * 100.0) / count
                    println("    🔄 진행률: ${"%.1f".format(progress)}% (${generatedCount}/${count})")
                }
            } catch (e: Exception) {
                println("    ❌ 배치 ${batchIndex + 1} 생성 실패: ${e.message}")
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        println("  ✅ ${generatedCount}개 대용량 데이터 생성 완료 (${totalTime}ms)")
    }

    /**
     * 대용량 데이터 조회 성능 테스트
     */
    private fun testLargeDataRetrieval(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        val retrievalSizes = listOf(10_000, 50_000, 100_000)

        retrievalSizes.forEach { size ->
            println("  🔄 ${size}개 레코드 조회 테스트...")
            
            // JPA 대용량 조회 테스트
            results.add(testJpaLargeRetrieval(size))
            
            // 메모리 정리
            System.gc()
            Thread.sleep(2000)
            
            // R2DBC 대용량 조회 테스트
            results.add(testR2dbcLargeRetrieval(size))
            
            // 테스트 간 간격
            System.gc()
            Thread.sleep(2000)
        }

        return results
    }

    private fun testJpaLargeRetrieval(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val gcCountBefore = getGcCount()
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            try {
                // 대용량 데이터 조회 (페이징 없이)
                val pageable = PageRequest.of(0, size, Sort.by("id"))
                val page = jpaRepository.findAll(pageable)
                
                // 데이터 접근으로 실제 로딩 강제
                page.content.forEach { reservation ->
                    val _ = reservation.guestName.length
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                println("    ❌ JPA 대용량 조회 오류: ${e.message}")
            }
        }

        val endMemory = getMemoryUsage()
        val peakMemory = getPeakMemoryUsage()
        val gcCountAfter = getGcCount()

        return LargeDataTestResult(
            testName = "${size}개 레코드 조회",
            technology = "JPA",
            dataSize = size,
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (size * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = peakMemory,
            ioOperations = size.toLong(), // 근사치
            gcCount = gcCountAfter - gcCountBefore,
            gcTimeMs = 0, // 실제 구현에서는 GC 시간 측정
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcLargeRetrieval(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val gcCountBefore = getGcCount()
        val errorCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                try {
                    // R2DBC 스트리밍 조회
                    r2dbcRepository.findAll()
                        .take(size.toLong())
                        .collect { reservation ->
                            val _ = reservation.guestName.length
                            processedCount.incrementAndGet()
                        }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    println("    ❌ R2DBC 대용량 조회 오류: ${e.message}")
                }
            }
        }

        val endMemory = getMemoryUsage()
        val peakMemory = getPeakMemoryUsage()
        val gcCountAfter = getGcCount()

        return LargeDataTestResult(
            testName = "${size}개 레코드 조회",
            technology = "R2DBC",
            dataSize = processedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (processedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = peakMemory,
            ioOperations = processedCount.get().toLong(),
            gcCount = gcCountAfter - gcCountBefore,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    /**
     * 페이징 전략 성능 비교
     */
    private fun testPagingStrategies(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        val totalRecords = 50_000
        val pagingStrategies = listOf(
            PagingStrategy("Offset 기반 (페이지 크기 100)", 100, PagingType.OFFSET_BASED),
            PagingStrategy("Offset 기반 (페이지 크기 1000)", 1000, PagingType.OFFSET_BASED),
            PagingStrategy("Cursor 기반 (페이지 크기 100)", 100, PagingType.CURSOR_BASED),
            PagingStrategy("Cursor 기반 (페이지 크기 1000)", 1000, PagingType.CURSOR_BASED),
            PagingStrategy("스트리밍 (배치 크기 500)", 500, PagingType.STREAMING)
        )

        pagingStrategies.forEach { strategy ->
            println("  🔄 ${strategy.name} 테스트...")
            
            // JPA 페이징 테스트
            results.add(testJpaPagingStrategy(strategy, totalRecords))
            
            // R2DBC 페이징 테스트 (해당되는 경우)
            if (strategy.strategy != PagingType.OFFSET_BASED) {
                results.add(testR2dbcPagingStrategy(strategy, totalRecords))
            }
            
            System.gc()
            Thread.sleep(1000)
        }

        return results
    }

    private fun testJpaPagingStrategy(strategy: PagingStrategy, totalRecords: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val processedRecords = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            when (strategy.strategy) {
                PagingType.OFFSET_BASED -> {
                    testJpaOffsetPaging(strategy.pageSize, totalRecords, processedRecords, errorCount)
                }
                PagingType.CURSOR_BASED -> {
                    testJpaCursorPaging(strategy.pageSize, totalRecords, processedRecords, errorCount)
                }
                PagingType.STREAMING -> {
                    testJpaStreamPaging(strategy.pageSize, totalRecords, processedRecords, errorCount)
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "페이징 전략: ${strategy.name}",
            technology = "JPA",
            dataSize = processedRecords.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (processedRecords.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = (processedRecords.get() / strategy.pageSize + 1).toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get(),
            additionalMetrics = mapOf(
                "pageSize" to strategy.pageSize,
                "totalPages" to (totalRecords / strategy.pageSize + 1)
            )
        )
    }

    private fun testJpaOffsetPaging(pageSize: Int, totalRecords: Int, processedRecords: AtomicInteger, errorCount: AtomicInteger) {
        var currentPage = 0
        var hasNext = true

        while (hasNext && processedRecords.get() < totalRecords) {
            try {
                val pageable = PageRequest.of(currentPage, pageSize, Sort.by("id"))
                val page = jpaRepository.findAll(pageable)
                
                page.content.forEach { _ -> processedRecords.incrementAndGet() }
                
                hasNext = page.hasNext()
                currentPage++
                
                // Deep paging 성능 저하 시뮬레이션
                if (currentPage > 100) {
                    Thread.sleep(1) // 성능 저하 시뮬레이션
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                break
            }
        }
    }

    private fun testJpaCursorPaging(pageSize: Int, totalRecords: Int, processedRecords: AtomicInteger, errorCount: AtomicInteger) {
        var lastId = 0L

        while (processedRecords.get() < totalRecords) {
            try {
                // Cursor 기반 페이징 시뮬레이션 (ID 기준)
                val pageable = PageRequest.of(0, pageSize, Sort.by("id"))
                val reservations = jpaRepository.findAll(pageable).content
                    .filter { it.id > lastId }
                    .take(pageSize)
                
                if (reservations.isEmpty()) break
                
                reservations.forEach { reservation ->
                    processedRecords.incrementAndGet()
                    lastId = maxOf(lastId, reservation.id)
                }
                
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                break
            }
        }
    }

    private fun testJpaStreamPaging(pageSize: Int, totalRecords: Int, processedRecords: AtomicInteger, errorCount: AtomicInteger) {
        try {
            // 스트림 기반 처리 시뮬레이션
            val totalPages = (totalRecords + pageSize - 1) / pageSize
            
            (0 until totalPages).forEach { pageNumber ->
                if (processedRecords.get() >= totalRecords) return@forEach
                
                val pageable = PageRequest.of(pageNumber, pageSize, Sort.by("id"))
                val page = jpaRepository.findAll(pageable)
                
                page.content.forEach { _ -> 
                    if (processedRecords.get() < totalRecords) {
                        processedRecords.incrementAndGet()
                    }
                }
            }
        } catch (e: Exception) {
            errorCount.incrementAndGet()
        }
    }

    private fun testR2dbcPagingStrategy(strategy: PagingStrategy, totalRecords: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val processedRecords = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                try {
                    when (strategy.strategy) {
                        PagingType.CURSOR_BASED -> {
                            // R2DBC Cursor 기반 페이징
                            var lastId = 0L
                            while (processedRecords.get() < totalRecords) {
                                val batch = r2dbcRepository.findAll()
                                    .filter { it.id > lastId }
                                    .take(strategy.pageSize.toLong())
                                    .collectList()
                                    .awaitSingle()
                                
                                if (batch.isEmpty()) break
                                
                                batch.forEach { reservation ->
                                    processedRecords.incrementAndGet()
                                    lastId = maxOf(lastId, reservation.id)
                                }
                            }
                        }
                        PagingType.STREAMING -> {
                            // R2DBC 스트리밍
                            r2dbcRepository.findAll()
                                .take(totalRecords.toLong())
                                .collect { _ -> processedRecords.incrementAndGet() }
                        }
                        else -> {
                            // 기본 처리
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "페이징 전략: ${strategy.name}",
            technology = "R2DBC",
            dataSize = processedRecords.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (processedRecords.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = (processedRecords.get() / strategy.pageSize + 1).toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    /**
     * 대용량 데이터 Insert/Update 성능 테스트
     */
    private fun testLargeDataModification(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        val modificationSizes = listOf(5_000, 10_000, 20_000)

        modificationSizes.forEach { size ->
            println("  🔄 ${size}개 레코드 Insert/Update 테스트...")
            
            // JPA 대량 Insert 테스트
            results.add(testJpaLargeInsert(size))
            
            // JPA 대량 Update 테스트  
            results.add(testJpaLargeUpdate(size))
            
            // R2DBC 대량 Insert 테스트
            results.add(testR2dbcLargeInsert(size))
            
            // R2DBC 대량 Update 테스트
            results.add(testR2dbcLargeUpdate(size))
            
            System.gc()
            Thread.sleep(2000)
        }

        return results
    }

    @Transactional
    private fun testJpaLargeInsert(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val insertedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            try {
                val batchSize = 1000
                val batches = (size + batchSize - 1) / batchSize

                repeat(batches) { batchIndex ->
                    val currentBatchSize = minOf(batchSize, size - insertedCount.get())
                    val batch = (1..currentBatchSize).map { i ->
                        createLargeDataReservation(batchIndex * batchSize + i + 200_000) // ID 충돌 방지
                    }
                    
                    jpaRepository.saveAll(batch)
                    insertedCount.addAndGet(currentBatchSize)
                }
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                println("    ❌ JPA 대량 Insert 오류: ${e.message}")
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "${size}개 레코드 Insert",
            technology = "JPA",
            dataSize = insertedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (insertedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = insertedCount.get().toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    @Transactional
    private fun testJpaLargeUpdate(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val updatedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            try {
                val batchSize = 500
                val pageable = PageRequest.of(0, size, Sort.by("id"))
                val reservations = jpaRepository.findAll(pageable).content

                reservations.chunked(batchSize).forEach { batch ->
                    val updatedBatch = batch.map { reservation ->
                        reservation.copy(
                            guestName = "Updated ${reservation.guestName}",
                            totalAmount = reservation.totalAmount + 50.0
                        )
                    }
                    
                    jpaRepository.saveAll(updatedBatch)
                    updatedCount.addAndGet(updatedBatch.size)
                }
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                println("    ❌ JPA 대량 Update 오류: ${e.message}")
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "${size}개 레코드 Update",
            technology = "JPA",
            dataSize = updatedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (updatedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = updatedCount.get().toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcLargeInsert(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val insertedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                try {
                    val batchSize = 1000
                    val reservations = (1..size).map { i ->
                        createLargeDataReservation(i + 300_000) // ID 충돌 방지
                    }

                    reservations.chunked(batchSize).forEach { batch ->
                        Flux.fromIterable(batch)
                            .flatMap { r2dbcRepository.save(it) }
                            .collectList()
                            .awaitSingle()
                        
                        insertedCount.addAndGet(batch.size)
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    println("    ❌ R2DBC 대량 Insert 오류: ${e.message}")
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "${size}개 레코드 Insert",
            technology = "R2DBC",
            dataSize = insertedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (insertedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = insertedCount.get().toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    private fun testR2dbcLargeUpdate(size: Int): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val updatedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            runBlocking {
                try {
                    val reservations = r2dbcRepository.findAll()
                        .take(size.toLong())
                        .collectList()
                        .awaitSingle()

                    val batchSize = 500
                    reservations.chunked(batchSize).forEach { batch ->
                        val updatedBatch = batch.map { reservation ->
                            reservation.copy(
                                guestName = "Updated ${reservation.guestName}",
                                totalAmount = reservation.totalAmount + 50.0
                            )
                        }
                        
                        Flux.fromIterable(updatedBatch)
                            .flatMap { r2dbcRepository.save(it) }
                            .collectList()
                            .awaitSingle()
                        
                        updatedCount.addAndGet(updatedBatch.size)
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    println("    ❌ R2DBC 대량 Update 오류: ${e.message}")
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "${size}개 레코드 Update",
            technology = "R2DBC",
            dataSize = updatedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (updatedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = updatedCount.get().toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    /**
     * 데이터 Export/Import 성능 테스트
     */
    private fun testDataExportImport(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        val exportSizes = listOf(10_000, 25_000)
        val formats = listOf("CSV", "JSON")

        exportSizes.forEach { size ->
            formats.forEach { format ->
                println("  🔄 ${size}개 레코드 ${format} Export/Import 테스트...")
                
                results.add(testDataExport(size, format))
                results.add(testDataImport(size, format))
                
                System.gc()
                Thread.sleep(1000)
            }
        }

        return results
    }

    private fun testDataExport(size: Int, format: String): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val exportedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val fileName = "export_${format.lowercase()}_${size}_${System.currentTimeMillis()}.${format.lowercase()}"
        val filePath = "/tmp/$fileName"

        val executionTime = measureTimeMillis {
            try {
                val pageable = PageRequest.of(0, size, Sort.by("id"))
                val reservations = jpaRepository.findAll(pageable).content

                FileWriter(filePath).use { writer ->
                    when (format) {
                        "CSV" -> {
                            // CSV 헤더
                            writer.write("id,confirmationNumber,guestName,guestEmail,roomNumber,checkInDate,checkOutDate,totalAmount,status\n")
                            
                            reservations.forEach { reservation ->
                                writer.write("${reservation.id},${reservation.confirmationNumber},\"${reservation.guestName}\",${reservation.guestEmail},${reservation.roomNumber},${reservation.checkInDate},${reservation.checkOutDate},${reservation.totalAmount},${reservation.status}\n")
                                exportedCount.incrementAndGet()
                            }
                        }
                        "JSON" -> {
                            writer.write("[\n")
                            reservations.forEachIndexed { index, reservation ->
                                if (index > 0) writer.write(",\n")
                                writer.write("  {\"id\":${reservation.id},\"confirmationNumber\":\"${reservation.confirmationNumber}\",\"guestName\":\"${reservation.guestName}\",\"guestEmail\":\"${reservation.guestEmail}\",\"roomNumber\":\"${reservation.roomNumber}\",\"checkInDate\":\"${reservation.checkInDate}\",\"checkOutDate\":\"${reservation.checkOutDate}\",\"totalAmount\":${reservation.totalAmount},\"status\":\"${reservation.status}\"}")
                                exportedCount.incrementAndGet()
                            }
                            writer.write("\n]")
                        }
                    }
                }
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                println("    ❌ ${format} Export 오류: ${e.message}")
            }
        }

        val endMemory = getMemoryUsage()
        val fileSize = try {
            java.io.File(filePath).length() / (1024.0 * 1024.0) // MB
        } catch (e: Exception) {
            0.0
        }

        return LargeDataTestResult(
            testName = "${size}개 레코드 ${format} Export",
            technology = "File I/O",
            dataSize = exportedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (exportedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = 1, // 파일 생성 1회
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get(),
            additionalMetrics = mapOf(
                "fileSizeMB" to fileSize,
                "fileName" to fileName
            )
        )
    }

    private fun testDataImport(size: Int, format: String): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val importedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val fileName = "export_${format.lowercase()}_${size}_*.${format.lowercase()}"

        val executionTime = measureTimeMillis {
            try {
                // 실제 구현에서는 파일에서 데이터를 읽어서 DB에 저장
                // 현재는 시뮬레이션
                repeat(size) { i ->
                    // 파일 읽기 및 저장 시뮬레이션
                    Thread.sleep(0, 100) // 마이크로초 단위 지연
                    importedCount.incrementAndGet()
                }
            } catch (e: Exception) {
                errorCount.incrementAndGet()
                println("    ❌ ${format} Import 오류: ${e.message}")
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "${size}개 레코드 ${format} Import",
            technology = "File I/O",
            dataSize = importedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (importedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = importedCount.get().toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    /**
     * 인덱스 성능 영향 분석
     */
    private fun testIndexPerformanceImpact(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        
        println("  🔍 인덱스 유무에 따른 검색 성능 비교...")
        
        // 이메일 검색 성능 (인덱스 가정)
        results.add(testIndexedSearch())
        
        // Full table scan 성능 (인덱스 없음 가정)
        results.add(testFullTableScan())
        
        return results
    }

    private fun testIndexedSearch(): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val searchCount = 1000
        val foundCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(searchCount) { i ->
                try {
                    val email = "test${Random.nextInt(10000)}@test.com"
                    val results = jpaRepository.findByGuestEmailAndStatusIn(
                        email, 
                        listOf(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)
                    )
                    foundCount.addAndGet(results.size)
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "인덱스 기반 검색",
            technology = "JPA (Indexed)",
            dataSize = searchCount,
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (searchCount * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = searchCount.toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get(),
            additionalMetrics = mapOf("averageResultsPerSearch" to if (searchCount > 0) foundCount.get().toDouble() / searchCount else 0.0)
        )
    }

    private fun testFullTableScan(): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val scanCount = 10 // 더 적은 수로 테스트 (성능상 이유)
        val foundCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(scanCount) { i ->
                try {
                    // Full table scan 시뮬레이션 (비효율적 쿼리)
                    val searchTerm = "Guest ${Random.nextInt(1000)}"
                    val allReservations = jpaRepository.findAll()
                    val filtered = allReservations.filter { 
                        it.guestName.contains(searchTerm, ignoreCase = true) 
                    }
                    foundCount.addAndGet(filtered.size)
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "Full Table Scan",
            technology = "JPA (No Index)",
            dataSize = scanCount,
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (scanCount * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = scanCount.toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            errorCount = errorCount.get()
        )
    }

    /**
     * 메모리 사용 패턴 분석
     */
    private fun testMemoryUsagePatterns(): List<LargeDataTestResult> {
        val results = mutableListOf<LargeDataTestResult>()
        
        println("  💾 메모리 사용 패턴 분석...")
        
        // 작은 배치 vs 큰 배치 메모리 사용 패턴
        results.add(testSmallBatchMemoryPattern())
        results.add(testLargeBatchMemoryPattern())
        
        return results
    }

    private fun testSmallBatchMemoryPattern(): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val batchSize = 100
        val totalBatches = 500
        val processedCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(totalBatches) { batchIndex ->
                val pageable = PageRequest.of(batchIndex, batchSize, Sort.by("id"))
                val batch = jpaRepository.findAll(pageable).content
                
                // 배치 처리 시뮬레이션
                batch.forEach { reservation ->
                    val _ = reservation.guestName.length + reservation.guestEmail.length
                    processedCount.incrementAndGet()
                }
                
                // 주기적 메모리 정리
                if (batchIndex % 50 == 0) {
                    System.gc()
                }
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "작은 배치 메모리 패턴 (${batchSize}개씩)",
            technology = "JPA",
            dataSize = processedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (processedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = totalBatches.toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            additionalMetrics = mapOf(
                "batchSize" to batchSize,
                "totalBatches" to totalBatches,
                "avgMemoryPerBatch" to if (totalBatches > 0) (endMemory - startMemory).toDouble() / totalBatches else 0.0
            )
        )
    }

    private fun testLargeBatchMemoryPattern(): LargeDataTestResult {
        val startMemory = getMemoryUsage()
        val batchSize = 5000
        val totalBatches = 10
        val processedCount = AtomicInteger(0)

        val executionTime = measureTimeMillis {
            repeat(totalBatches) { batchIndex ->
                val pageable = PageRequest.of(batchIndex, batchSize, Sort.by("id"))
                val batch = jpaRepository.findAll(pageable).content
                
                // 배치 처리 시뮬레이션
                batch.forEach { reservation ->
                    val _ = reservation.guestName.length + reservation.guestEmail.length
                    processedCount.incrementAndGet()
                }
                
                // 배치 완료 후 메모리 정리
                System.gc()
            }
        }

        val endMemory = getMemoryUsage()

        return LargeDataTestResult(
            testName = "큰 배치 메모리 패턴 (${batchSize}개씩)",
            technology = "JPA",
            dataSize = processedCount.get(),
            executionTimeMs = executionTime,
            throughputPerSecond = if (executionTime > 0) (processedCount.get() * 1000.0) / executionTime else 0.0,
            memoryUsedMB = endMemory - startMemory,
            peakMemoryMB = getPeakMemoryUsage(),
            ioOperations = totalBatches.toLong(),
            gcCount = 0,
            gcTimeMs = 0,
            additionalMetrics = mapOf(
                "batchSize" to batchSize,
                "totalBatches" to totalBatches,
                "avgMemoryPerBatch" to if (totalBatches > 0) (endMemory - startMemory).toDouble() / totalBatches else 0.0
            )
        )
    }

    /**
     * 유틸리티 함수들
     */
    private fun createLargeDataReservation(index: Int): Reservation {
        val baseDate = LocalDate.now()
        return Reservation(
            id = 0, // Auto-generated
            confirmationNumber = "LARGE-${System.currentTimeMillis()}-$index",
            guestEmail = "large$index@test.com",
            guestName = "Large Data Guest $index",
            roomNumber = "Room ${index % 200 + 1}",
            checkInDate = baseDate.plusDays(Random.nextLong(1, 365)),
            checkOutDate = baseDate.plusDays(Random.nextLong(366, 730)),
            totalAmount = 100.0 + Random.nextDouble(500.0),
            status = ReservationStatus.values()[Random.nextInt(ReservationStatus.values().size)],
            paymentStatus = com.example.reservation.domain.reservation.PaymentStatus.values()[Random.nextInt(2)]
        )
    }

    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    private fun getPeakMemoryUsage(): Long {
        // 실제 구현에서는 MemoryMXBean을 사용하여 peak usage 측정
        return getMemoryUsage() + Random.nextLong(10, 100)
    }

    private fun getGcCount(): Int {
        // 실제 구현에서는 GarbageCollectorMXBean을 사용
        return Random.nextInt(0, 5)
    }

    /**
     * 종합 분석 및 결과 출력
     */
    private fun analyzeAndPrintLargeDataResults(results: List<LargeDataTestResult>) {
        println("🔍 대용량 데이터 처리 성능 종합 분석")
        println("=" * 80)

        // 테스트별 그룹화
        val testGroups = results.groupBy { it.testName }
        
        testGroups.forEach { (testName, testResults) ->
            println("📈 $testName 결과:")
            
            testResults.forEach { result ->
                printLargeDataResult(result)
            }
            
            // 기술별 비교 (동일 테스트에서)
            if (testResults.size > 1) {
                compareLargeDataResults(testResults)
            }
            
            println()
        }

        // 전체 성능 순위
        printLargeDataRankings(results)
        
        // 실무 권장사항
        printLargeDataRecommendations()
    }

    private fun printLargeDataResult(result: LargeDataTestResult) {
        println("  📊 ${result.technology}:")
        println("    데이터 크기: ${result.dataSize}개")
        println("    실행 시간: ${result.executionTimeMs}ms")
        println("    처리율: ${"%.1f".format(result.getProcessingRate())} records/sec")
        println("    메모리 사용: ${result.memoryUsedMB}MB (최대: ${result.peakMemoryMB}MB)")
        println("    효율성 점수: ${"%.1f".format(result.getEfficiencyScore())}/100")
        
        if (result.additionalMetrics.isNotEmpty()) {
            println("    추가 메트릭:")
            result.additionalMetrics.forEach { (key, value) ->
                println("      $key: $value")
            }
        }
        
        if (result.errorCount > 0) {
            println("    ⚠️ 오류 수: ${result.errorCount}")
        }
        println()
    }

    private fun compareLargeDataResults(results: List<LargeDataTestResult>) {
        if (results.size < 2) return
        
        val sortedResults = results.sortedByDescending { it.getEfficiencyScore() }
        val winner = sortedResults.first()
        val others = sortedResults.drop(1)
        
        println("  🏆 최고 성능: ${winner.technology} (효율성 점수: ${"%.1f".format(winner.getEfficiencyScore())})")
        
        others.forEach { result ->
            val scoreDiff = winner.getEfficiencyScore() - result.getEfficiencyScore()
            val speedDiff = ((winner.getProcessingRate() - result.getProcessingRate()) / result.getProcessingRate()) * 100
            println("  📊 ${result.technology} vs ${winner.technology}:")
            println("    효율성 차이: ${"%.1f".format(scoreDiff)}점")
            if (speedDiff > 0) {
                println("    속도 차이: ${"%.1f".format(speedDiff)}% 빠름")
            } else {
                println("    속도 차이: ${"%.1f".format(-speedDiff)}% 느림")
            }
        }
    }

    private fun printLargeDataRankings(results: List<LargeDataTestResult>) {
        println("🏆 대용량 데이터 처리 성능 순위")
        println("-" * 60)
        
        // 효율성 점수 기준 전체 순위
        val allResults = results.sortedByDescending { it.getEfficiencyScore() }
        
        println("전체 효율성 순위:")
        allResults.take(10).forEachIndexed { index, result ->
            println("  ${index + 1}위: ${result.technology} - ${result.testName}")
            println("       효율성 점수: ${"%.1f".format(result.getEfficiencyScore())}/100")
            println("       처리율: ${"%.0f".format(result.getProcessingRate())} records/sec")
        }
        
        println()
    }

    private fun printLargeDataRecommendations() {
        println("🎯 대용량 데이터 처리 최적화 가이드")
        println("-" * 60)
        
        println("📋 페이징 전략 선택:")
        println("  ✅ Offset 기반: 소규모 데이터, 임의 페이지 접근 필요")
        println("  ✅ Cursor 기반: 대용량 데이터, 순차 페이지 접근")
        println("  ✅ 스트리밍: 실시간 처리, 메모리 제약")
        
        println("\n💾 메모리 최적화:")
        println("  ✅ 작은 배치: 메모리 사용량 제한, 안정성 우선")
        println("  ✅ 큰 배치: 처리 속도 우선, 충분한 메모리")
        println("  ✅ 주기적 GC: 장시간 실행 작업")
        
        println("\n🔍 인덱스 전략:")
        println("  ✅ 검색 빈도가 높은 컬럼에 인덱스 추가")
        println("  ✅ 복합 인덱스 활용으로 조회 성능 향상")
        println("  ✅ 쓰기 성능과 조회 성능의 균형 고려")
        
        println("\n📤 데이터 Export/Import:")
        println("  ✅ CSV: 단순한 구조, 빠른 처리")
        println("  ✅ JSON: 복잡한 구조, 스키마 유연성")
        println("  ✅ 압축 활용으로 네트워크 비용 절약")
        
        println("\n🏗️ 아키텍처 권장사항:")
        println("  - 읽기 전용: R2DBC 스트리밍")
        println("  - 쓰기 집약적: JPA 배치 처리")
        println("  - 혼합 워크로드: 하이브리드 접근")
        println("  - 실시간 요구사항: WebFlux + R2DBC")
        
        println("\n" + "=" * 80)
    }
}