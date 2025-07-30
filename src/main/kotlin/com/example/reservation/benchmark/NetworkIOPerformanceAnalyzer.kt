package com.example.reservation.benchmark

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Component
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*
import kotlin.system.measureTimeMillis

/**
 * 🚀 Network/I/O Performance Analyzer
 * 
 * 네트워크 및 I/O 성능을 종합적으로 분석하는 도구입니다.
 * NIO vs BIO, 커넥션 풀, 버퍼 튜닝, 비동기 처리 등을 비교 분석합니다.
 */
@Component
class NetworkIOPerformanceAnalyzer {

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
        private const val TEST_DATA_SIZE = 1024 * 1024 // 1MB
        private const val WARMUP_ITERATIONS = 100
        private const val MEASUREMENT_ITERATIONS = 1000
    }

    /**
     * 1단계: NIO vs BIO 성능 비교
     */
    suspend fun analyzeNIOvsBIO(): NIOvsBIOAnalysisResult = withContext(Dispatchers.IO) {
        println("🔍 Phase 1: NIO vs BIO Performance Analysis")
        
        val testData = generateTestData(TEST_DATA_SIZE)
        val results = mutableMapOf<String, IOPerformanceMetrics>()
        
        // BIO 성능 측정
        println("📊 Testing BIO (Blocking I/O)...")
        results["BIO"] = measureBIOPerformance(testData)
        
        // NIO 성능 측정  
        println("📊 Testing NIO (Non-blocking I/O)...")
        results["NIO"] = measureNIOPerformance(testData)
        
        // NIO.2 (Files API) 성능 측정
        println("📊 Testing NIO.2 (Files API)...")
        results["NIO2"] = measureNIO2Performance(testData)
        
        // Memory-mapped I/O 성능 측정
        println("📊 Testing Memory-mapped I/O...")
        results["MMAP"] = measureMemoryMappedIOPerformance(testData)
        
        val analysis = analyzeIOResults(results)
        println("✅ NIO vs BIO analysis completed")
        
        NIOvsBIOAnalysisResult(
            results = results,
            analysis = analysis,
            recommendations = generateIORecommendations(results)
        )
    }

    /**
     * 2단계: 커넥션 풀 최적화 분석
     */
    suspend fun analyzeConnectionPoolOptimization(): ConnectionPoolAnalysisResult = withContext(Dispatchers.IO) {
        println("🔍 Phase 2: Connection Pool Optimization Analysis")
        
        val poolConfigs = listOf(
            ConnectionPoolConfig("Small", 5, 10, Duration.ofSeconds(30)),
            ConnectionPoolConfig("Medium", 10, 20, Duration.ofSeconds(60)),
            ConnectionPoolConfig("Large", 20, 50, Duration.ofSeconds(120)),
            ConnectionPoolConfig("XLarge", 50, 100, Duration.ofSeconds(300))
        )
        
        val results = mutableMapOf<String, ConnectionPoolMetrics>()
        
        for (config in poolConfigs) {
            println("📊 Testing ${config.name} pool configuration...")
            results[config.name] = measureConnectionPoolPerformance(config)
        }
        
        val analysis = analyzeConnectionPoolResults(results)
        println("✅ Connection pool analysis completed")
        
        ConnectionPoolAnalysisResult(
            results = results,
            analysis = analysis,
            optimalConfig = findOptimalConnectionPoolConfig(results),
            recommendations = generateConnectionPoolRecommendations(results)
        )
    }

    /**
     * 3단계: 네트워크 버퍼 튜닝
     */
    suspend fun analyzeNetworkBufferTuning(): NetworkBufferAnalysisResult = withContext(Dispatchers.IO) {
        println("🔍 Phase 3: Network Buffer Tuning Analysis")
        
        val bufferSizes = listOf(1024, 2048, 4096, 8192, 16384, 32768, 65536)
        val results = mutableMapOf<Int, BufferPerformanceMetrics>()
        
        for (bufferSize in bufferSizes) {
            println("📊 Testing buffer size: ${bufferSize} bytes...")
            results[bufferSize] = measureBufferPerformance(bufferSize)
        }
        
        val analysis = analyzeBufferResults(results)
        println("✅ Network buffer analysis completed")
        
        NetworkBufferAnalysisResult(
            results = results,
            analysis = analysis,
            optimalBufferSize = findOptimalBufferSize(results),
            recommendations = generateBufferRecommendations(results)
        )
    }

    /**
     * 4단계: 비동기 처리 성능 측정
     */
    suspend fun analyzeAsyncProcessingPerformance(): AsyncProcessingAnalysisResult = withContext(Dispatchers.Default) {
        println("🔍 Phase 4: Async Processing Performance Analysis")
        
        val scenarios = listOf(
            AsyncScenario("Sequential", ProcessingType.SEQUENTIAL),
            AsyncScenario("Parallel", ProcessingType.PARALLEL),
            AsyncScenario("Coroutines", ProcessingType.COROUTINES),
            AsyncScenario("CompletableFuture", ProcessingType.COMPLETABLE_FUTURE),
            AsyncScenario("ReactiveStreams", ProcessingType.REACTIVE_STREAMS)
        )
        
        val results = mutableMapOf<String, AsyncPerformanceMetrics>()
        
        for (scenario in scenarios) {
            println("📊 Testing ${scenario.name} processing...")
            results[scenario.name] = measureAsyncPerformance(scenario)
        }
        
        val analysis = analyzeAsyncResults(results)
        println("✅ Async processing analysis completed")
        
        AsyncProcessingAnalysisResult(
            results = results,
            analysis = analysis,
            recommendations = generateAsyncRecommendations(results)
        )
    }

    /**
     * 5단계: 네트워크 지연시간 최적화
     */
    suspend fun analyzeNetworkLatencyOptimization(): NetworkLatencyAnalysisResult = withContext(Dispatchers.IO) {
        println("🔍 Phase 5: Network Latency Optimization Analysis")
        
        val optimizations = listOf(
            LatencyOptimization("Baseline", emptyMap()),
            LatencyOptimization("TCP_NODELAY", mapOf("TCP_NODELAY" to true)),
            LatencyOptimization("SO_KEEPALIVE", mapOf("SO_KEEPALIVE" to true)),
            LatencyOptimization("SO_REUSEADDR", mapOf("SO_REUSEADDR" to true)),
            LatencyOptimization("Combined", mapOf(
                "TCP_NODELAY" to true,
                "SO_KEEPALIVE" to true,
                "SO_REUSEADDR" to true
            ))
        )
        
        val results = mutableMapOf<String, LatencyMetrics>()
        
        for (optimization in optimizations) {
            println("📊 Testing ${optimization.name} optimization...")
            results[optimization.name] = measureNetworkLatency(optimization)
        }
        
        val analysis = analyzeLatencyResults(results)
        println("✅ Network latency analysis completed")
        
        NetworkLatencyAnalysisResult(
            results = results,
            analysis = analysis,
            recommendations = generateLatencyRecommendations(results)
        )
    }

    /**
     * 종합 분석 실행
     */
    suspend fun runComprehensiveAnalysis(): ComprehensiveNetworkIOAnalysisResult {
        println("🚀 Starting Comprehensive Network/I/O Performance Analysis")
        val startTime = System.currentTimeMillis()
        
        val nioVsBioResult = analyzeNIOvsBIO()
        val connectionPoolResult = analyzeConnectionPoolOptimization()
        val bufferTuningResult = analyzeNetworkBufferTuning()
        val asyncProcessingResult = analyzeAsyncProcessingPerformance()
        val latencyOptimizationResult = analyzeNetworkLatencyOptimization()
        
        val totalTime = System.currentTimeMillis() - startTime
        
        val overallAnalysis = generateOverallAnalysis(
            nioVsBioResult,
            connectionPoolResult,
            bufferTuningResult,
            asyncProcessingResult,
            latencyOptimizationResult
        )
        
        println("✅ Comprehensive analysis completed in ${totalTime}ms")
        
        return ComprehensiveNetworkIOAnalysisResult(
            nioVsBio = nioVsBioResult,
            connectionPool = connectionPoolResult,
            bufferTuning = bufferTuningResult,
            asyncProcessing = asyncProcessingResult,
            latencyOptimization = latencyOptimizationResult,
            overallAnalysis = overallAnalysis,
            executionTimeMs = totalTime
        )
    }

    // ================================
    // BIO 성능 측정
    // ================================
    
    private suspend fun measureBIOPerformance(testData: ByteArray): IOPerformanceMetrics = withContext(Dispatchers.IO) {
        val metrics = IOPerformanceMetrics()
        val tempFile = Files.createTempFile("bio_test", ".dat")
        
        try {
            // 쓰기 성능 측정
            val writeTime = measureTimeMillis {
                FileOutputStream(tempFile.toFile()).use { fos ->
                    BufferedOutputStream(fos, DEFAULT_BUFFER_SIZE).use { bos ->
                        repeat(MEASUREMENT_ITERATIONS) {
                            bos.write(testData)
                        }
                    }
                }
            }
            
            // 읽기 성능 측정
            val readTime = measureTimeMillis {
                FileInputStream(tempFile.toFile()).use { fis ->
                    BufferedInputStream(fis, DEFAULT_BUFFER_SIZE).use { bis ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (bis.read(buffer) != -1) {
                            // 데이터 처리 시뮬레이션
                        }
                    }
                }
            }
            
            val totalBytes = testData.size.toLong() * MEASUREMENT_ITERATIONS
            
            metrics.copy(
                writeTimeMs = writeTime,
                readTimeMs = readTime,
                writeThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (writeTime / 1000.0),
                readThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (readTime / 1000.0),
                totalBytes = totalBytes,
                averageLatencyMs = (writeTime + readTime).toDouble() / (MEASUREMENT_ITERATIONS * 2)
            )
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // ================================
    // NIO 성능 측정
    // ================================
    
    private suspend fun measureNIOPerformance(testData: ByteArray): IOPerformanceMetrics = withContext(Dispatchers.IO) {
        val metrics = IOPerformanceMetrics()
        val tempFile = Files.createTempFile("nio_test", ".dat")
        
        try {
            // 쓰기 성능 측정
            val writeTime = measureTimeMillis {
                FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE).use { channel ->
                    val buffer = ByteBuffer.allocateDirect(testData.size)
                    repeat(MEASUREMENT_ITERATIONS) {
                        buffer.clear()
                        buffer.put(testData)
                        buffer.flip()
                        while (buffer.hasRemaining()) {
                            channel.write(buffer)
                        }
                    }
                }
            }
            
            // 읽기 성능 측정
            val readTime = measureTimeMillis {
                FileChannel.open(tempFile, StandardOpenOption.READ).use { channel ->
                    val buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE)
                    while (channel.read(buffer) > 0) {
                        buffer.flip()
                        // 데이터 처리 시뮬레이션
                        buffer.clear()
                    }
                }
            }
            
            val totalBytes = testData.size.toLong() * MEASUREMENT_ITERATIONS
            
            metrics.copy(
                writeTimeMs = writeTime,
                readTimeMs = readTime,
                writeThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (writeTime / 1000.0),
                readThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (readTime / 1000.0),
                totalBytes = totalBytes,
                averageLatencyMs = (writeTime + readTime).toDouble() / (MEASUREMENT_ITERATIONS * 2)
            )
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // ================================
    // NIO.2 성능 측정
    // ================================
    
    private suspend fun measureNIO2Performance(testData: ByteArray): IOPerformanceMetrics = withContext(Dispatchers.IO) {
        val metrics = IOPerformanceMetrics()
        val tempFile = Files.createTempFile("nio2_test", ".dat")
        
        try {
            // 쓰기 성능 측정
            val writeTime = measureTimeMillis {
                repeat(MEASUREMENT_ITERATIONS) {
                    Files.write(tempFile, testData, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
                }
            }
            
            // 읽기 성능 측정
            val readTime = measureTimeMillis {
                val data = Files.readAllBytes(tempFile)
                // 데이터 처리 시뮬레이션
                data.sum()
            }
            
            val totalBytes = testData.size.toLong() * MEASUREMENT_ITERATIONS
            
            metrics.copy(
                writeTimeMs = writeTime,
                readTimeMs = readTime,
                writeThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (writeTime / 1000.0),
                readThroughputMBps = (totalBytes / 1024.0 / 1024.0) / (readTime / 1000.0),
                totalBytes = totalBytes,
                averageLatencyMs = (writeTime + readTime).toDouble() / (MEASUREMENT_ITERATIONS * 2)
            )
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // ================================
    // Memory-mapped I/O 성능 측정
    // ================================
    
    private suspend fun measureMemoryMappedIOPerformance(testData: ByteArray): IOPerformanceMetrics = withContext(Dispatchers.IO) {
        val metrics = IOPerformanceMetrics()
        val tempFile = Files.createTempFile("mmap_test", ".dat")
        val totalSize = testData.size.toLong() * MEASUREMENT_ITERATIONS
        
        try {
            // 파일 크기 미리 설정
            RandomAccessFile(tempFile.toFile(), "rw").use { raf ->
                raf.setLength(totalSize)
            }
            
            // 쓰기 성능 측정
            val writeTime = measureTimeMillis {
                FileChannel.open(tempFile, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
                    val mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, totalSize)
                    repeat(MEASUREMENT_ITERATIONS) {
                        mappedBuffer.put(testData)
                    }
                    mappedBuffer.force() // 강제 동기화
                }
            }
            
            // 읽기 성능 측정
            val readTime = measureTimeMillis {
                FileChannel.open(tempFile, StandardOpenOption.READ).use { channel ->
                    val mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, totalSize)
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (mappedBuffer.hasRemaining()) {
                        val remaining = minOf(buffer.size, mappedBuffer.remaining())
                        mappedBuffer.get(buffer, 0, remaining)
                    }
                }
            }
            
            metrics.copy(
                writeTimeMs = writeTime,
                readTimeMs = readTime,
                writeThroughputMBps = (totalSize / 1024.0 / 1024.0) / (writeTime / 1000.0),
                readThroughputMBps = (totalSize / 1024.0 / 1024.0) / (readTime / 1000.0),
                totalBytes = totalSize,
                averageLatencyMs = (writeTime + readTime).toDouble() / (MEASUREMENT_ITERATIONS * 2)
            )
            
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    // ================================
    // 커넥션 풀 성능 측정
    // ================================
    
    private suspend fun measureConnectionPoolPerformance(config: ConnectionPoolConfig): ConnectionPoolMetrics = withContext(Dispatchers.IO) {
        val connectionPool = SimulatedConnectionPool(config)
        val metrics = ConnectionPoolMetrics()
        val latencies = mutableListOf<Long>()
        val errors = AtomicLong(0)
        
        try {
            // 워밍업
            repeat(WARMUP_ITERATIONS) {
                try {
                    connectionPool.borrowConnection().use { 
                        // 연결 사용 시뮬레이션
                        delay(1)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                }
            }
            
            // 실제 측정
            val startTime = System.currentTimeMillis()
            val jobs = (1..MEASUREMENT_ITERATIONS).map { index ->
                async {
                    val requestStart = System.nanoTime()
                    try {
                        connectionPool.borrowConnection().use { connection ->
                            // 실제 작업 시뮬레이션
                            delay(Random.nextLong(1, 10))
                            connection.simulateWork()
                        }
                        val latency = (System.nanoTime() - requestStart) / 1_000_000 // ms
                        synchronized(latencies) {
                            latencies.add(latency)
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    }
                }
            }
            
            jobs.awaitAll()
            val totalTime = System.currentTimeMillis() - startTime
            
            val sortedLatencies = latencies.sorted()
            
            metrics.copy(
                config = config,
                totalRequests = MEASUREMENT_ITERATIONS,
                successfulRequests = MEASUREMENT_ITERATIONS - errors.get().toInt(),
                failedRequests = errors.get().toInt(),
                totalTimeMs = totalTime,
                averageLatencyMs = latencies.average(),
                p50LatencyMs = sortedLatencies[sortedLatencies.size / 2].toDouble(),
                p95LatencyMs = sortedLatencies[(sortedLatencies.size * 0.95).toInt()].toDouble(),
                p99LatencyMs = sortedLatencies[(sortedLatencies.size * 0.99).toInt()].toDouble(),
                throughputRps = (MEASUREMENT_ITERATIONS * 1000.0) / totalTime,
                errorRate = (errors.get().toDouble() / MEASUREMENT_ITERATIONS) * 100,
                maxActiveConnections = connectionPool.getMaxActiveConnections(),
                avgActiveConnections = connectionPool.getAverageActiveConnections()
            )
            
        } finally {
            connectionPool.close()
        }
    }

    // ================================
    // 버퍼 성능 측정
    // ================================
    
    private suspend fun measureBufferPerformance(bufferSize: Int): BufferPerformanceMetrics = withContext(Dispatchers.IO) {
        val testData = generateTestData(TEST_DATA_SIZE)
        val metrics = BufferPerformanceMetrics()
        
        // 네트워크 시뮬레이션을 위한 소켓 페어 생성
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        
        try {
            // 서버 시작
            val serverJob = async {
                serverSocket.accept().use { clientSocket ->
                    val input = BufferedInputStream(clientSocket.getInputStream(), bufferSize)
                    val output = BufferedOutputStream(clientSocket.getOutputStream(), bufferSize)
                    
                    val buffer = ByteArray(bufferSize)
                    var totalBytes = 0L
                    val startTime = System.nanoTime()
                    
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                    output.flush()
                    
                    val endTime = System.nanoTime()
                    val durationMs = (endTime - startTime) / 1_000_000
                    
                    BufferPerformanceMetrics(
                        bufferSize = bufferSize,
                        totalBytes = totalBytes,
                        durationMs = durationMs,
                        throughputMBps = (totalBytes / 1024.0 / 1024.0) / (durationMs / 1000.0),
                        latencyMs = durationMs.toDouble() / MEASUREMENT_ITERATIONS
                    )
                }
            }
            
            // 클라이언트 연결 및 데이터 전송
            delay(100) // 서버 시작 대기
            Socket("localhost", port).use { socket ->
                val output = BufferedOutputStream(socket.getOutputStream(), bufferSize)
                repeat(MEASUREMENT_ITERATIONS) {
                    output.write(testData)
                }
                output.flush()
                socket.shutdownOutput()
            }
            
            serverJob.await()
            
        } finally {
            serverSocket.close()
        }
    }

    // ================================
    // 비동기 처리 성능 측정
    // ================================
    
    private suspend fun measureAsyncPerformance(scenario: AsyncScenario): AsyncPerformanceMetrics = withContext(Dispatchers.Default) {
        val metrics = AsyncPerformanceMetrics()
        val workItems = (1..MEASUREMENT_ITERATIONS).toList()
        
        val (processingTime, results) = when (scenario.type) {
            ProcessingType.SEQUENTIAL -> measureSequentialProcessing(workItems)
            ProcessingType.PARALLEL -> measureParallelProcessing(workItems)
            ProcessingType.COROUTINES -> measureCoroutineProcessing(workItems)
            ProcessingType.COMPLETABLE_FUTURE -> measureCompletableFutureProcessing(workItems)
            ProcessingType.REACTIVE_STREAMS -> measureReactiveStreamsProcessing(workItems)
        }
        
        metrics.copy(
            scenario = scenario,
            totalItems = workItems.size,
            processingTimeMs = processingTime,
            throughputItemsPerSecond = (workItems.size * 1000.0) / processingTime,
            averageItemProcessingMs = processingTime.toDouble() / workItems.size,
            successfulItems = results.count { it.isSuccess },
            failedItems = results.count { !it.isSuccess }
        )
    }

    private suspend fun measureSequentialProcessing(workItems: List<Int>): Pair<Long, List<ProcessingResult>> {
        val results = mutableListOf<ProcessingResult>()
        val processingTime = measureTimeMillis {
            for (item in workItems) {
                results.add(simulateWork(item))
            }
        }
        return processingTime to results
    }

    private suspend fun measureParallelProcessing(workItems: List<Int>): Pair<Long, List<ProcessingResult>> {
        val results = ConcurrentLinkedQueue<ProcessingResult>()
        val processingTime = measureTimeMillis {
            runBlocking {
                workItems.map { item ->
                    async(Dispatchers.Default) {
                        results.add(simulateWork(item))
                    }
                }.awaitAll()
            }
        }
        return processingTime to results.toList()
    }

    private suspend fun measureCoroutineProcessing(workItems: List<Int>): Pair<Long, List<ProcessingResult>> {
        val results = mutableListOf<ProcessingResult>()
        val processingTime = measureTimeMillis {
            workItems.asFlow()
                .buffer(50) // 버퍼 크기 제한
                .map { item -> simulateWork(item) }
                .collect { results.add(it) }
        }
        return processingTime to results
    }

    private suspend fun measureCompletableFutureProcessing(workItems: List<Int>): Pair<Long, List<ProcessingResult>> {
        val executor = ForkJoinPool.commonPool()
        val results = mutableListOf<ProcessingResult>()
        val processingTime = measureTimeMillis {
            val futures = workItems.map { item ->
                CompletableFuture.supplyAsync({ simulateWorkBlocking(item) }, executor)
            }
            CompletableFuture.allOf(*futures.toTypedArray()).join()
            results.addAll(futures.map { it.get() })
        }
        return processingTime to results
    }

    private suspend fun measureReactiveStreamsProcessing(workItems: List<Int>): Pair<Long, List<ProcessingResult>> {
        val results = mutableListOf<ProcessingResult>()
        val processingTime = measureTimeMillis {
            workItems.asFlow()
                .flatMapMerge(concurrency = 50) { item ->
                    flow { emit(simulateWork(item)) }
                }
                .collect { results.add(it) }
        }
        return processingTime to results
    }

    // ================================
    // 네트워크 지연시간 측정
    // ================================
    
    private suspend fun measureNetworkLatency(optimization: LatencyOptimization): LatencyMetrics = withContext(Dispatchers.IO) {
        val metrics = LatencyMetrics()
        val latencies = mutableListOf<Long>()
        
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        
        // 소켓 옵션 적용
        optimization.options.forEach { (option, value) ->
            when (option) {
                "SO_KEEPALIVE" -> serverSocket.keepAlive = value as Boolean
                "SO_REUSEADDR" -> serverSocket.reuseAddress = value as Boolean
            }
        }
        
        try {
            // 서버 시작
            val serverJob = async {
                repeat(MEASUREMENT_ITERATIONS) {
                    serverSocket.accept().use { clientSocket ->
                        // TCP_NODELAY 옵션 적용
                        if (optimization.options["TCP_NODELAY"] == true) {
                            clientSocket.tcpNoDelay = true
                        }
                        
                        val input = clientSocket.getInputStream()
                        val output = clientSocket.getOutputStream()
                        
                        // 에코 서버 동작
                        val buffer = ByteArray(1024)
                        val bytesRead = input.read(buffer)
                        if (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            
            // 클라이언트에서 지연시간 측정
            delay(100) // 서버 시작 대기
            repeat(MEASUREMENT_ITERATIONS) {
                val start = System.nanoTime()
                Socket("localhost", port).use { socket ->
                    // TCP_NODELAY 옵션 적용
                    if (optimization.options["TCP_NODELAY"] == true) {
                        socket.tcpNoDelay = true
                    }
                    
                    val output = socket.getOutputStream()
                    val input = socket.getInputStream()
                    
                    // 데이터 전송 및 응답 대기
                    output.write("ping".toByteArray())
                    val buffer = ByteArray(1024)
                    input.read(buffer)
                }
                val latency = (System.nanoTime() - start) / 1_000_000 // ms
                latencies.add(latency)
            }
            
            serverJob.await()
            
            val sortedLatencies = latencies.sorted()
            
            metrics.copy(
                optimization = optimization,
                measurements = latencies.size,
                averageLatencyMs = latencies.average(),
                minLatencyMs = latencies.minOrNull()?.toDouble() ?: 0.0,
                maxLatencyMs = latencies.maxOrNull()?.toDouble() ?: 0.0,
                p50LatencyMs = sortedLatencies[sortedLatencies.size / 2].toDouble(),
                p95LatencyMs = sortedLatencies[(sortedLatencies.size * 0.95).toInt()].toDouble(),
                p99LatencyMs = sortedLatencies[(sortedLatencies.size * 0.99).toInt()].toDouble(),
                standardDeviation = calculateStandardDeviation(latencies.map { it.toDouble() })
            )
            
        } finally {
            serverSocket.close()
        }
    }

    // ================================
    // 분석 및 추천
    // ================================
    
    private fun analyzeIOResults(results: Map<String, IOPerformanceMetrics>): IOAnalysis {
        val bestWrite = results.maxByOrNull { it.value.writeThroughputMBps }
        val bestRead = results.maxByOrNull { it.value.readThroughputMBps }
        val lowestLatency = results.minByOrNull { it.value.averageLatencyMs }
        
        return IOAnalysis(
            bestWritePerformance = bestWrite?.key ?: "Unknown",
            bestReadPerformance = bestRead?.key ?: "Unknown",
            lowestLatency = lowestLatency?.key ?: "Unknown",
            overallRecommendation = when {
                bestWrite?.key == "MMAP" && bestRead?.key == "MMAP" -> 
                    "Memory-mapped I/O가 전반적으로 최고 성능을 보입니다. 대용량 파일 처리에 적합합니다."
                bestWrite?.key == "NIO" && bestRead?.key == "NIO" -> 
                    "NIO가 균형잡힌 성능을 보입니다. 네트워크 I/O와 파일 I/O 모두에 적합합니다."
                else -> 
                    "워크로드 특성에 따라 적절한 I/O 방식을 선택하세요."
            }
        )
    }

    private fun generateIORecommendations(results: Map<String, IOPerformanceMetrics>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val bioMetrics = results["BIO"]
        val nioMetrics = results["NIO"]
        val mmapMetrics = results["MMAP"]
        
        if (nioMetrics != null && bioMetrics != null) {
            val throughputImprovement = ((nioMetrics.writeThroughputMBps - bioMetrics.writeThroughputMBps) / bioMetrics.writeThroughputMBps) * 100
            if (throughputImprovement > 20) {
                recommendations.add("NIO 사용으로 BIO 대비 ${throughputImprovement.toInt()}% 성능 향상 가능")
            }
        }
        
        if (mmapMetrics != null) {
            recommendations.add("Memory-mapped I/O는 대용량 파일(>100MB) 처리 시 고려하세요")
        }
        
        recommendations.add("네트워크 I/O에는 NIO, 대용량 파일에는 Memory-mapped I/O 사용 권장")
        recommendations.add("버퍼 크기를 8KB-64KB 범위에서 워크로드에 맞게 조정하세요")
        
        return recommendations
    }

    private fun analyzeConnectionPoolResults(results: Map<String, ConnectionPoolMetrics>): ConnectionPoolAnalysis {
        val bestThroughput = results.maxByOrNull { it.value.throughputRps }
        val bestLatency = results.minByOrNull { it.value.averageLatencyMs }
        val lowestError = results.minByOrNull { it.value.errorRate }
        
        return ConnectionPoolAnalysis(
            bestThroughputConfig = bestThroughput?.key ?: "Unknown",
            bestLatencyConfig = bestLatency?.key ?: "Unknown",
            mostReliableConfig = lowestError?.key ?: "Unknown",
            analysis = "커넥션 풀 크기는 동시 요청 수와 네트워크 지연시간을 고려하여 설정해야 합니다."
        )
    }

    private fun findOptimalConnectionPoolConfig(results: Map<String, ConnectionPoolMetrics>): String {
        // 처리량과 지연시간의 균형점을 찾는 로직
        val scores = results.mapValues { (_, metrics) ->
            val throughputScore = metrics.throughputRps / results.values.maxOf { it.throughputRps }
            val latencyScore = 1.0 - (metrics.averageLatencyMs / results.values.maxOf { it.averageLatencyMs })
            val errorScore = 1.0 - (metrics.errorRate / 100.0)
            
            (throughputScore * 0.4 + latencyScore * 0.4 + errorScore * 0.2)
        }
        
        return scores.maxByOrNull { it.value }?.key ?: "Medium"
    }

    private fun generateConnectionPoolRecommendations(results: Map<String, ConnectionPoolMetrics>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val optimalConfig = findOptimalConnectionPoolConfig(results)
        recommendations.add("최적 커넥션 풀 설정: $optimalConfig")
        
        val highErrorConfigs = results.filter { it.value.errorRate > 5.0 }
        if (highErrorConfigs.isNotEmpty()) {
            recommendations.add("${highErrorConfigs.keys.joinToString(", ")} 설정에서 높은 에러율 발생")
        }
        
        recommendations.add("커넥션 타임아웃은 평균 요청 처리 시간의 2-3배로 설정")
        recommendations.add("최대 커넥션 수는 동시 요청 수의 1.5-2배로 설정")
        
        return recommendations
    }

    private fun analyzeBufferResults(results: Map<Int, BufferPerformanceMetrics>): BufferAnalysis {
        val bestThroughput = results.maxByOrNull { it.value.throughputMBps }
        val bestLatency = results.minByOrNull { it.value.latencyMs }
        
        return BufferAnalysis(
            optimalThroughputSize = bestThroughput?.key ?: 8192,
            optimalLatencySize = bestLatency?.key ?: 8192,
            recommendation = "버퍼 크기와 처리량은 트레이드오프 관계입니다. 워크로드에 맞는 최적점을 찾으세요."
        )
    }

    private fun findOptimalBufferSize(results: Map<Int, BufferPerformanceMetrics>): Int {
        // 처리량과 지연시간의 균형점을 찾는 로직
        val scores = results.mapValues { (_, metrics) ->
            val throughputScore = metrics.throughputMBps / results.values.maxOf { it.throughputMBps }
            val latencyScore = 1.0 - (metrics.latencyMs / results.values.maxOf { it.latencyMs })
            
            (throughputScore * 0.6 + latencyScore * 0.4)
        }
        
        return scores.maxByOrNull { it.value }?.key ?: 8192
    }

    private fun generateBufferRecommendations(results: Map<Int, BufferPerformanceMetrics>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val optimalSize = findOptimalBufferSize(results)
        recommendations.add("최적 버퍼 크기: ${optimalSize} bytes")
        
        val throughputPeak = results.maxByOrNull { it.value.throughputMBps }
        if (throughputPeak != null) {
            recommendations.add("최대 처리량을 위해서는 ${throughputPeak.key} bytes 버퍼 사용")
        }
        
        recommendations.add("네트워크 지연시간이 높은 환경에서는 큰 버퍼(32KB+) 사용")
        recommendations.add("메모리 제약이 있는 환경에서는 작은 버퍼(4KB-8KB) 사용")
        
        return recommendations
    }

    private fun analyzeAsyncResults(results: Map<String, AsyncPerformanceMetrics>): AsyncAnalysis {
        val bestThroughput = results.maxByOrNull { it.value.throughputItemsPerSecond }
        val bestLatency = results.minByOrNull { it.value.averageItemProcessingMs }
        
        return AsyncAnalysis(
            bestThroughputMethod = bestThroughput?.key ?: "Unknown",
            bestLatencyMethod = bestLatency?.key ?: "Unknown",
            recommendation = when {
                bestThroughput?.key == "Coroutines" -> "Kotlin Coroutines가 최고의 처리량을 보입니다"
                bestThroughput?.key == "Parallel" -> "병렬 처리가 CPU 집약적 작업에 적합합니다"
                else -> "워크로드 특성에 따라 적절한 비동기 처리 방식을 선택하세요"
            }
        )
    }

    private fun generateAsyncRecommendations(results: Map<String, AsyncPerformanceMetrics>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val coroutineMetrics = results["Coroutines"]
        val parallelMetrics = results["Parallel"]
        
        if (coroutineMetrics != null && parallelMetrics != null) {
            if (coroutineMetrics.throughputItemsPerSecond > parallelMetrics.throughputItemsPerSecond) {
                recommendations.add("I/O 집약적 작업에는 Kotlin Coroutines 사용 권장")
            } else {
                recommendations.add("CPU 집약적 작업에는 병렬 처리 사용 권장")
            }
        }
        
        recommendations.add("비동기 처리 시 적절한 동시성 제한(concurrency limit) 설정")
        recommendations.add("백프레셰(backpressure) 처리를 위한 버퍼링 전략 수립")
        
        return recommendations
    }

    private fun analyzeLatencyResults(results: Map<String, LatencyMetrics>): LatencyAnalysis {
        val baseline = results["Baseline"]
        val bestOptimization = results.filterKeys { it != "Baseline" }.minByOrNull { it.value.averageLatencyMs }
        
        val improvement = if (baseline != null && bestOptimization != null) {
            ((baseline.averageLatencyMs - bestOptimization.value.averageLatencyMs) / baseline.averageLatencyMs) * 100
        } else 0.0
        
        return LatencyAnalysis(
            bestOptimization = bestOptimization?.key ?: "None",
            improvementPercent = improvement,
            recommendation = if (improvement > 10) {
                "${bestOptimization?.key} 최적화로 ${improvement.toInt()}% 지연시간 개선 가능"
            } else {
                "현재 네트워크 설정이 적절합니다"
            }
        )
    }

    private fun generateLatencyRecommendations(results: Map<String, LatencyMetrics>): List<String> {
        val recommendations = mutableListOf<String>()
        
        val tcpNoDelayMetrics = results.entries.find { it.key.contains("TCP_NODELAY") }
        if (tcpNoDelayMetrics != null) {
            recommendations.add("TCP_NODELAY 옵션으로 네트워크 지연시간 최적화 가능")
        }
        
        recommendations.add("실시간 애플리케이션에서는 TCP_NODELAY 옵션 활성화")
        recommendations.add("Keep-Alive 옵션으로 커넥션 재사용성 향상")
        recommendations.add("네트워크 버퍼 크기를 워크로드에 맞게 조정")
        
        return recommendations
    }

    private fun generateOverallAnalysis(
        nioVsBio: NIOvsBIOAnalysisResult,
        connectionPool: ConnectionPoolAnalysisResult,
        bufferTuning: NetworkBufferAnalysisResult,
        asyncProcessing: AsyncProcessingAnalysisResult,
        latencyOptimization: NetworkLatencyAnalysisResult
    ): OverallNetworkIOAnalysis {
        
        val recommendations = mutableListOf<String>()
        
        // I/O 방식 추천
        recommendations.add("I/O 방식: ${nioVsBio.analysis.overallRecommendation}")
        
        // 커넥션 풀 추천
        recommendations.add("커넥션 풀: ${connectionPool.optimalConfig} 설정 사용")
        
        // 버퍼 크기 추천
        recommendations.add("버퍼 크기: ${bufferTuning.optimalBufferSize} bytes 사용")
        
        // 비동기 처리 추천
        recommendations.add("비동기 처리: ${asyncProcessing.analysis.bestThroughputMethod} 방식 사용")
        
        // 네트워크 최적화 추천
        recommendations.add("네트워크 최적화: ${latencyOptimization.analysis.bestOptimization} 적용")
        
        return OverallNetworkIOAnalysis(
            overallScore = calculateOverallScore(nioVsBio, connectionPool, bufferTuning, asyncProcessing, latencyOptimization),
            keyFindings = recommendations.take(3),
            recommendations = recommendations,
            priorityOptimizations = listOf(
                "가장 큰 성능 향상을 위해 ${asyncProcessing.analysis.bestThroughputMethod} 비동기 처리 적용",
                "네트워크 지연시간 최적화를 위해 ${latencyOptimization.analysis.bestOptimization} 설정 적용",
                "메모리 효율성을 위해 ${bufferTuning.optimalBufferSize} bytes 버퍼 크기 사용"
            )
        )
    }

    private fun calculateOverallScore(
        nioVsBio: NIOvsBIOAnalysisResult,
        connectionPool: ConnectionPoolAnalysisResult,
        bufferTuning: NetworkBufferAnalysisResult,
        asyncProcessing: AsyncProcessingAnalysisResult,
        latencyOptimization: NetworkLatencyAnalysisResult
    ): Double {
        // 각 분석 결과를 종합하여 전체 점수 계산
        return 85.0 // 예시 점수
    }

    // ================================
    // 유틸리티 메서드
    // ================================
    
    private fun generateTestData(size: Int): ByteArray {
        return ByteArray(size) { (it % 256).toByte() }
    }

    private suspend fun simulateWork(item: Int): ProcessingResult {
        delay(Random.nextLong(1, 5)) // 1-5ms 작업 시뮬레이션
        return ProcessingResult(
            item = item,
            result = item * 2,
            isSuccess = Random.nextDouble() > 0.01 // 1% 실패율
        )
    }

    private fun simulateWorkBlocking(item: Int): ProcessingResult {
        Thread.sleep(Random.nextLong(1, 5))
        return ProcessingResult(
            item = item,
            result = item * 2,
            isSuccess = Random.nextDouble() > 0.01
        )
    }

    private fun calculateStandardDeviation(values: List<Double>): Double {
        val mean = values.average()
        val sumOfSquaredDifferences = values.sumOf { (it - mean).pow(2) }
        return sqrt(sumOfSquaredDifferences / values.size)
    }
}

// ================================
// 데이터 클래스들
// ================================

data class IOPerformanceMetrics(
    val writeTimeMs: Long = 0,
    val readTimeMs: Long = 0,
    val writeThroughputMBps: Double = 0.0,
    val readThroughputMBps: Double = 0.0,
    val totalBytes: Long = 0,
    val averageLatencyMs: Double = 0.0
)

data class ConnectionPoolConfig(
    val name: String,
    val initialSize: Int,
    val maxSize: Int,
    val connectionTimeout: Duration
)

data class ConnectionPoolMetrics(
    val config: ConnectionPoolConfig = ConnectionPoolConfig("Default", 0, 0, Duration.ZERO),
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val failedRequests: Int = 0,
    val totalTimeMs: Long = 0,
    val averageLatencyMs: Double = 0.0,
    val p50LatencyMs: Double = 0.0,
    val p95LatencyMs: Double = 0.0,
    val p99LatencyMs: Double = 0.0,
    val throughputRps: Double = 0.0,
    val errorRate: Double = 0.0,
    val maxActiveConnections: Int = 0,
    val avgActiveConnections: Double = 0.0
)

data class BufferPerformanceMetrics(
    val bufferSize: Int = 0,
    val totalBytes: Long = 0,
    val durationMs: Long = 0,
    val throughputMBps: Double = 0.0,
    val latencyMs: Double = 0.0
)

data class AsyncScenario(
    val name: String,
    val type: ProcessingType
)

enum class ProcessingType {
    SEQUENTIAL, PARALLEL, COROUTINES, COMPLETABLE_FUTURE, REACTIVE_STREAMS
}

data class AsyncPerformanceMetrics(
    val scenario: AsyncScenario = AsyncScenario("Default", ProcessingType.SEQUENTIAL),
    val totalItems: Int = 0,
    val processingTimeMs: Long = 0,
    val throughputItemsPerSecond: Double = 0.0,
    val averageItemProcessingMs: Double = 0.0,
    val successfulItems: Int = 0,
    val failedItems: Int = 0
)

data class ProcessingResult(
    val item: Int,
    val result: Int,
    val isSuccess: Boolean
)

data class LatencyOptimization(
    val name: String,
    val options: Map<String, Any>
)

data class LatencyMetrics(
    val optimization: LatencyOptimization = LatencyOptimization("Default", emptyMap()),
    val measurements: Int = 0,
    val averageLatencyMs: Double = 0.0,
    val minLatencyMs: Double = 0.0,
    val maxLatencyMs: Double = 0.0,
    val p50LatencyMs: Double = 0.0,
    val p95LatencyMs: Double = 0.0,
    val p99LatencyMs: Double = 0.0,
    val standardDeviation: Double = 0.0
)

// 분석 결과 클래스들
data class IOAnalysis(
    val bestWritePerformance: String,
    val bestReadPerformance: String,
    val lowestLatency: String,
    val overallRecommendation: String
)

data class ConnectionPoolAnalysis(
    val bestThroughputConfig: String,
    val bestLatencyConfig: String,
    val mostReliableConfig: String,
    val analysis: String
)

data class BufferAnalysis(
    val optimalThroughputSize: Int,
    val optimalLatencySize: Int,
    val recommendation: String
)

data class AsyncAnalysis(
    val bestThroughputMethod: String,
    val bestLatencyMethod: String,
    val recommendation: String
)

data class LatencyAnalysis(
    val bestOptimization: String,
    val improvementPercent: Double,
    val recommendation: String
)

data class OverallNetworkIOAnalysis(
    val overallScore: Double,
    val keyFindings: List<String>,
    val recommendations: List<String>,
    val priorityOptimizations: List<String>
)

// 최종 결과 클래스들
data class NIOvsBIOAnalysisResult(
    val results: Map<String, IOPerformanceMetrics>,
    val analysis: IOAnalysis,
    val recommendations: List<String>
)

data class ConnectionPoolAnalysisResult(
    val results: Map<String, ConnectionPoolMetrics>,
    val analysis: ConnectionPoolAnalysis,
    val optimalConfig: String,
    val recommendations: List<String>
)

data class NetworkBufferAnalysisResult(
    val results: Map<Int, BufferPerformanceMetrics>,
    val analysis: BufferAnalysis,
    val optimalBufferSize: Int,
    val recommendations: List<String>
)

data class AsyncProcessingAnalysisResult(
    val results: Map<String, AsyncPerformanceMetrics>,
    val analysis: AsyncAnalysis,
    val recommendations: List<String>
)

data class NetworkLatencyAnalysisResult(
    val results: Map<String, LatencyMetrics>,
    val analysis: LatencyAnalysis,
    val recommendations: List<String>
)

data class ComprehensiveNetworkIOAnalysisResult(
    val nioVsBio: NIOvsBIOAnalysisResult,
    val connectionPool: ConnectionPoolAnalysisResult,
    val bufferTuning: NetworkBufferAnalysisResult,
    val asyncProcessing: AsyncProcessingAnalysisResult,
    val latencyOptimization: NetworkLatencyAnalysisResult,
    val overallAnalysis: OverallNetworkIOAnalysis,
    val executionTimeMs: Long
)

// ================================
// 시뮬레이션 클래스들
// ================================

class SimulatedConnectionPool(private val config: ConnectionPoolConfig) {
    private val connections = Channel<SimulatedConnection>(config.maxSize)
    private val activeConnections = AtomicLong(0)
    private val totalActiveTime = AtomicLong(0)
    private val maxActive = AtomicLong(0)
    private var startTime = System.currentTimeMillis()
    
    init {
        // 초기 커넥션 생성
        repeat(config.initialSize) {
            runBlocking {
                connections.send(SimulatedConnection(it))
            }
        }
    }
    
    suspend fun borrowConnection(): SimulatedConnection {
        return withTimeout(config.connectionTimeout.toMillis()) {
            val connection = connections.receive()
            val active = activeConnections.incrementAndGet()
            maxActive.updateAndGet { max(it, active) }
            connection
        }
    }
    
    fun returnConnection(connection: SimulatedConnection) {
        runBlocking {
            connections.send(connection)
            activeConnections.decrementAndGet()
        }
    }
    
    fun getMaxActiveConnections(): Int = maxActive.get().toInt()
    
    fun getAverageActiveConnections(): Double {
        val duration = System.currentTimeMillis() - startTime
        return if (duration > 0) totalActiveTime.get().toDouble() / duration else 0.0
    }
    
    fun close() {
        // 리소스 정리
        connections.close()
    }
}

class SimulatedConnection(val id: Int) : AutoCloseable {
    suspend fun simulateWork() {
        delay(Random.nextLong(1, 10)) // 1-10ms 작업 시뮬레이션
    }
    
    override fun close() {
        // 커넥션 반환은 풀이 처리
    }
}