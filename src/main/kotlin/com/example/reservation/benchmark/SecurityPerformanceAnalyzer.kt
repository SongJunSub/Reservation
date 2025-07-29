package com.example.reservation.benchmark

import com.example.reservation.entity.User
import com.example.reservation.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureTimeMillis

data class SecurityMetrics(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val operationType: String,
    val algorithm: String,
    val keySize: Int = 0,
    val dataSize: Int,
    val operationTime: Long,
    val throughput: Double,
    val memoryUsed: Long = 0,
    val cpuIntensive: Boolean = false,
    val errorCount: Long = 0,
    val successCount: Long = 0
)

data class JWTPerformanceResult(
    val tokenGeneration: SecurityMetrics,
    val tokenValidation: SecurityMetrics,
    val tokenParsing: SecurityMetrics,
    val overallThroughput: Double,
    val memoryFootprint: Long,
    val recommendations: List<String>
)

data class CryptoPerformanceResult(
    val encryptionMetrics: SecurityMetrics,
    val decryptionMetrics: SecurityMetrics,
    val hashingMetrics: SecurityMetrics,
    val passwordMetrics: SecurityMetrics,
    val overallScore: Double,
    val recommendations: List<String>
)

data class ProtocolPerformanceResult(
    val httpMetrics: SecurityMetrics,
    val httpsMetrics: SecurityMetrics,
    val performanceDifference: Double,
    val securityOverhead: Double,
    val recommendations: List<String>
)

data class RateLimitingResult(
    val rateLimitOverhead: SecurityMetrics,
    val throughputImpact: Double,
    val memoryImpact: Long,
    val latencyIncrease: Double,
    val recommendations: List<String>
)

@Component
class SecurityPerformanceAnalyzer(
    @Autowired private val userRepository: UserRepository
) : CommandLineRunner {

    @Value("\${jwt.secret:defaultSecretKeyForTesting123456789}")
    private lateinit var jwtSecret: String

    private val passwordEncoder: PasswordEncoder = BCryptPasswordEncoder()
    private val secureRandom = SecureRandom()
    
    private val performanceLog = mutableListOf<SecurityMetrics>()
    private val requestCounter = AtomicLong(0)
    
    override fun run(vararg args: String?) {
        println("🔐 시작: Security Performance Overhead Analysis")
        println("=" * 80)
        
        runBlocking {
            when (args.getOrNull(0)) {
                "jwt" -> analyzeJWTPerformance()
                "crypto" -> analyzeCryptoPerformance()
                "protocol" -> analyzeProtocolPerformance()
                "ratelimit" -> analyzeRateLimitingImpact()
                "filter-chain" -> analyzeSecurityFilterChain()
                "comprehensive" -> runComprehensiveAnalysis()
                else -> runComprehensiveAnalysis()
            }
        }
        
        generatePerformanceReport()
    }
    
    // ============================================================
    // JWT 토큰 처리 성능 분석
    // ============================================================
    
    suspend fun analyzeJWTPerformance(): JWTPerformanceResult {
        println("\n📊 Phase 1: JWT Token Processing Performance Analysis")
        println("-".repeat(60))
        
        val secretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
        val sampleClaims = mapOf(
            "sub" to "user123",
            "name" to "Test User",
            "email" to "test@example.com",
            "roles" to listOf("USER", "ADMIN"),
            "exp" to Date(System.currentTimeMillis() + 3600000)
        )
        
        // JWT 토큰 생성 성능 측정
        val tokenGenerationMetrics = measureJWTGeneration(secretKey, sampleClaims)
        
        // JWT 토큰 검증 성능 측정
        val generatedToken = Jwts.builder()
            .setClaims(sampleClaims)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 3600000))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact()
            
        val tokenValidationMetrics = measureJWTValidation(secretKey, generatedToken)
        
        // JWT 토큰 파싱 성능 측정
        val tokenParsingMetrics = measureJWTParsing(secretKey, generatedToken)
        
        val overallThroughput = calculateOverallThroughput(
            listOf(tokenGenerationMetrics, tokenValidationMetrics, tokenParsingMetrics)
        )
        
        val result = JWTPerformanceResult(
            tokenGeneration = tokenGenerationMetrics,
            tokenValidation = tokenValidationMetrics,
            tokenParsing = tokenParsingMetrics,
            overallThroughput = overallThroughput,
            memoryFootprint = measureJWTMemoryFootprint(),
            recommendations = generateJWTRecommendations(tokenGenerationMetrics, tokenValidationMetrics)
        )
        
        printJWTAnalysisResults(result)
        return result
    }
    
    private suspend fun measureJWTGeneration(secretKey: javax.crypto.SecretKey, claims: Map<String, Any>): SecurityMetrics {
        val iterations = 10000
        val dataSize = claims.toString().toByteArray().size
        var successCount = 0L
        var errorCount = 0L
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                try {
                    Jwts.builder()
                        .setClaims(claims)
                        .setIssuedAt(Date())
                        .setExpiration(Date(System.currentTimeMillis() + 3600000))
                        .signWith(secretKey, SignatureAlgorithm.HS256)
                        .compact()
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }
        }
        
        return SecurityMetrics(
            operationType = "JWT_GENERATION",
            algorithm = "HS256",
            keySize = 256,
            dataSize = dataSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            cpuIntensive = true,
            successCount = successCount,
            errorCount = errorCount
        )
    }
    
    private suspend fun measureJWTValidation(secretKey: javax.crypto.SecretKey, token: String): SecurityMetrics {
        val iterations = 10000
        val dataSize = token.toByteArray().size
        var successCount = 0L
        var errorCount = 0L
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                try {
                    Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token)
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }
        }
        
        return SecurityMetrics(
            operationType = "JWT_VALIDATION",
            algorithm = "HS256",
            keySize = 256,
            dataSize = dataSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            cpuIntensive = true,
            successCount = successCount,
            errorCount = errorCount
        )
    }
    
    private suspend fun measureJWTParsing(secretKey: javax.crypto.SecretKey, token: String): SecurityMetrics {
        val iterations = 10000
        val dataSize = token.toByteArray().size
        var successCount = 0L
        var errorCount = 0L
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                try {
                    val claims = Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(token)
                        .body
                    
                    // 클레임 데이터에 접근하여 실제 파싱 작업 수행
                    claims["sub"]
                    claims["name"]
                    claims["email"]
                    claims["roles"]
                    
                    successCount++
                } catch (e: Exception) {
                    errorCount++
                }
            }
        }
        
        return SecurityMetrics(
            operationType = "JWT_PARSING",
            algorithm = "HS256",
            keySize = 256,
            dataSize = dataSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            cpuIntensive = false,
            successCount = successCount,
            errorCount = errorCount
        )
    }
    
    // ============================================================
    // 암호화/복호화 오버헤드 측정
    // ============================================================
    
    suspend fun analyzeCryptoPerformance(): CryptoPerformanceResult {
        println("\n🔒 Phase 2: Encryption/Decryption Overhead Analysis")
        println("-".repeat(60))
        
        val testData = "This is a test message for encryption performance analysis. ".repeat(100)
        
        // AES 암호화/복호화 성능 측정
        val aesMetrics = measureAESPerformance(testData)
        
        // 해싱 알고리즘 성능 측정
        val hashingMetrics = measureHashingPerformance(testData)
        
        // 패스워드 인코딩 성능 측정
        val passwordMetrics = measurePasswordEncodingPerformance()
        
        val overallScore = calculateCryptoScore(aesMetrics.first, aesMetrics.second, hashingMetrics, passwordMetrics)
        
        val result = CryptoPerformanceResult(
            encryptionMetrics = aesMetrics.first,
            decryptionMetrics = aesMetrics.second,
            hashingMetrics = hashingMetrics,
            passwordMetrics = passwordMetrics,
            overallScore = overallScore,
            recommendations = generateCryptoRecommendations(aesMetrics.first, hashingMetrics, passwordMetrics)
        )
        
        printCryptoAnalysisResults(result)
        return result
    }
    
    private suspend fun measureAESPerformance(data: String): Pair<SecurityMetrics, SecurityMetrics> {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
        val iterations = 1000
        
        // 암호화 성능 측정
        var encryptedData: ByteArray = byteArrayOf()
        var iv: ByteArray = byteArrayOf()
        val encryptionTime = measureTimeMillis {
            repeat(iterations) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                iv = cipher.iv
                encryptedData = cipher.doFinal(dataBytes)
            }
        }
        
        val encryptionMetrics = SecurityMetrics(
            operationType = "AES_ENCRYPTION",
            algorithm = "AES-256-GCM",
            keySize = 256,
            dataSize = dataBytes.size,
            operationTime = encryptionTime,
            throughput = (iterations.toDouble() / encryptionTime) * 1000,
            cpuIntensive = true,
            successCount = iterations.toLong()
        )
        
        // 복호화 성능 측정
        val decryptionTime = measureTimeMillis {
            repeat(iterations) {
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
                cipher.doFinal(encryptedData)
            }
        }
        
        val decryptionMetrics = SecurityMetrics(
            operationType = "AES_DECRYPTION",
            algorithm = "AES-256-GCM",
            keySize = 256,
            dataSize = encryptedData.size,
            operationTime = decryptionTime,
            throughput = (iterations.toDouble() / decryptionTime) * 1000,
            cpuIntensive = true,
            successCount = iterations.toLong()
        )
        
        return Pair(encryptionMetrics, decryptionMetrics)
    }
    
    private suspend fun measureHashingPerformance(data: String): SecurityMetrics {
        val algorithms = listOf("SHA-256", "SHA-512", "MD5")
        val iterations = 10000
        val dataBytes = data.toByteArray(StandardCharsets.UTF_8)
        
        var bestAlgorithm = ""
        var bestThroughput = 0.0
        var totalTime = 0L
        
        algorithms.forEach { algorithm ->
            val time = measureTimeMillis {
                repeat(iterations) {
                    MessageDigest.getInstance(algorithm).digest(dataBytes)
                }
            }
            
            val throughput = (iterations.toDouble() / time) * 1000
            if (throughput > bestThroughput) {
                bestThroughput = throughput
                bestAlgorithm = algorithm
                totalTime = time
            }
        }
        
        return SecurityMetrics(
            operationType = "HASHING",
            algorithm = bestAlgorithm,
            dataSize = dataBytes.size,
            operationTime = totalTime,
            throughput = bestThroughput,
            cpuIntensive = true,
            successCount = iterations.toLong()
        )
    }
    
    private suspend fun measurePasswordEncodingPerformance(): SecurityMetrics {
        val passwords = listOf(
            "password123",
            "strongPassword!@#",
            "verylongpasswordwithmanycharacters123456789"
        )
        
        val iterations = 100 // BCrypt는 의도적으로 느리므로 적은 반복
        var totalTime = 0L
        var successCount = 0L
        
        passwords.forEach { password ->
            val time = measureTimeMillis {
                repeat(iterations) {
                    try {
                        val encoded = passwordEncoder.encode(password)
                        passwordEncoder.matches(password, encoded)
                        successCount++
                    } catch (e: Exception) {
                        // 오류 처리
                    }
                }
            }
            totalTime += time
        }
        
        return SecurityMetrics(
            operationType = "PASSWORD_ENCODING",
            algorithm = "BCrypt",
            dataSize = passwords.sumOf { it.length },
            operationTime = totalTime,
            throughput = (successCount.toDouble() / totalTime) * 1000,
            cpuIntensive = true,
            successCount = successCount
        )
    }
    
    // ============================================================
    // HTTPS vs HTTP 성능 비교
    // ============================================================
    
    suspend fun analyzeProtocolPerformance(): ProtocolPerformanceResult {
        println("\n🌐 Phase 3: HTTPS vs HTTP Performance Analysis")
        println("-".repeat(60))
        
        // HTTP 시뮬레이션 (SSL 오버헤드 없음)
        val httpMetrics = simulateHTTPPerformance()
        
        // HTTPS 시뮬레이션 (SSL 핸드셰이크 + 암호화 오버헤드)
        val httpsMetrics = simulateHTTPSPerformance()
        
        val performanceDifference = ((httpsMetrics.operationTime - httpMetrics.operationTime).toDouble() / httpMetrics.operationTime) * 100
        val securityOverhead = httpsMetrics.operationTime - httpMetrics.operationTime
        
        val result = ProtocolPerformanceResult(
            httpMetrics = httpMetrics,
            httpsMetrics = httpsMetrics,
            performanceDifference = performanceDifference,
            securityOverhead = securityOverhead.toDouble(),
            recommendations = generateProtocolRecommendations(performanceDifference)
        )
        
        printProtocolAnalysisResults(result)
        return result
    }
    
    private suspend fun simulateHTTPPerformance(): SecurityMetrics {
        val iterations = 10000
        val requestSize = 1024 // 1KB 요청 시뮬레이션
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                // HTTP 요청 시뮬레이션 (네트워크 지연 시뮬레이션)
                delay(1) // 1ms 기본 네트워크 지연
                
                // 단순 데이터 전송 시뮬레이션
                val data = ByteArray(requestSize)
                secureRandom.nextBytes(data)
            }
        }
        
        return SecurityMetrics(
            operationType = "HTTP_REQUEST",
            algorithm = "None",
            dataSize = requestSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            cpuIntensive = false,
            successCount = iterations.toLong()
        )
    }
    
    private suspend fun simulateHTTPSPerformance(): SecurityMetrics {
        val iterations = 10000
        val requestSize = 1024 // 1KB 요청 시뮬레이션
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                // HTTPS 오버헤드 시뮬레이션
                delay(1) // 기본 네트워크 지연
                
                // SSL 핸드셰이크 시뮬레이션 (첫 연결시에만 발생하지만 여기서는 시뮬레이션)
                if (it % 100 == 0) { // 100개 요청마다 새 연결 가정
                    delay(10) // SSL 핸드셰이크 추가 지연
                }
                
                // 데이터 암호화 시뮬레이션
                val data = ByteArray(requestSize)
                secureRandom.nextBytes(data)
                
                // AES 암호화 시뮬레이션
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val key = keyGen.generateKey()
                cipher.init(Cipher.ENCRYPT_MODE, key)
                cipher.doFinal(data)
            }
        }
        
        return SecurityMetrics(
            operationType = "HTTPS_REQUEST",
            algorithm = "TLS-1.3-AES-256",
            keySize = 256,
            dataSize = requestSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            cpuIntensive = true,
            successCount = iterations.toLong()
        )
    }
    
    // ============================================================
    // Rate Limiting 성능 영향 분석
    // ============================================================
    
    suspend fun analyzeRateLimitingImpact(): RateLimitingResult {
        println("\n⏱️ Phase 4: Rate Limiting Performance Impact Analysis")
        println("-".repeat(60))
        
        // Rate Limiting 없는 경우와 있는 경우 비교
        val withoutRateLimitMetrics = measurePerformanceWithoutRateLimit()
        val withRateLimitMetrics = measurePerformanceWithRateLimit()
        
        val throughputImpact = ((withoutRateLimitMetrics.throughput - withRateLimitMetrics.throughput) / withoutRateLimitMetrics.throughput) * 100
        val memoryImpact = estimateRateLimitMemoryUsage()
        val latencyIncrease = withRateLimitMetrics.operationTime.toDouble() - withoutRateLimitMetrics.operationTime
        
        val result = RateLimitingResult(
            rateLimitOverhead = withRateLimitMetrics,
            throughputImpact = throughputImpact,
            memoryImpact = memoryImpact,
            latencyIncrease = latencyIncrease,
            recommendations = generateRateLimitRecommendations(throughputImpact, memoryImpact)
        )
        
        printRateLimitAnalysisResults(result)
        return result
    }
    
    private suspend fun measurePerformanceWithoutRateLimit(): SecurityMetrics {
        val iterations = 10000
        val requestSize = 512
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                // 기본 요청 처리 시뮬레이션
                processRequest(requestSize)
            }
        }
        
        return SecurityMetrics(
            operationType = "NO_RATE_LIMIT",
            algorithm = "None",
            dataSize = requestSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            successCount = iterations.toLong()
        )
    }
    
    private suspend fun measurePerformanceWithRateLimit(): SecurityMetrics {
        val iterations = 10000
        val requestSize = 512
        val rateLimitMap = mutableMapOf<String, MutableList<Long>>()
        val rateLimit = 100 // 초당 100 요청
        var blockedRequests = 0L
        var successfulRequests = 0L
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                val clientId = "client_${it % 10}" // 10개 클라이언트 시뮬레이션
                val currentTime = System.currentTimeMillis()
                
                // Rate Limiting 체크
                val clientRequests = rateLimitMap.getOrPut(clientId) { mutableListOf() }
                
                // 1초 이전 요청들 제거
                clientRequests.removeAll { it < currentTime - 1000 }
                
                if (clientRequests.size < rateLimit) {
                    clientRequests.add(currentTime)
                    processRequest(requestSize)
                    successfulRequests++
                } else {
                    // Rate limit 초과
                    blockedRequests++
                    delay(1) // 거부 처리 시간
                }
            }
        }
        
        return SecurityMetrics(
            operationType = "WITH_RATE_LIMIT",
            algorithm = "Token_Bucket",
            dataSize = requestSize,
            operationTime = totalTime,
            throughput = (successfulRequests.toDouble() / totalTime) * 1000,
            successCount = successfulRequests,
            errorCount = blockedRequests
        )
    }
    
    private suspend fun processRequest(size: Int) {
        // 요청 처리 시뮬레이션
        delay(1)
        val data = ByteArray(size)
        secureRandom.nextBytes(data)
    }
    
    // ============================================================
    // 보안 필터 체인 최적화 분석
    // ============================================================
    
    suspend fun analyzeSecurityFilterChain() {
        println("\n🔗 Phase 5: Security Filter Chain Optimization Analysis")
        println("-".repeat(60))
        
        val filterChainConfigurations = listOf(
            "Basic" to listOf("CORS", "CSRF", "Authentication"),
            "Enhanced" to listOf("CORS", "CSRF", "Authentication", "Authorization", "RateLimit"),
            "Full" to listOf("CORS", "CSRF", "Authentication", "Authorization", "RateLimit", "XSS", "ContentType", "HSTS")
        )
        
        filterChainConfigurations.forEach { (name, filters) ->
            val metrics = measureFilterChainPerformance(name, filters)
            performanceLog.add(metrics)
            
            println("📋 Filter Chain: $name")
            println("   Filters: ${filters.joinToString(", ")}")
            println("   Processing Time: ${metrics.operationTime}ms")
            println("   Throughput: ${"%.2f".format(metrics.throughput)} req/sec")
            println("   Memory Overhead: ${estimateFilterMemoryUsage(filters)}KB")
            println()
        }
        
        generateFilterChainRecommendations()
    }
    
    private suspend fun measureFilterChainPerformance(name: String, filters: List<String>): SecurityMetrics {
        val iterations = 1000
        val requestSize = 1024
        
        val totalTime = measureTimeMillis {
            repeat(iterations) {
                filters.forEach { filter ->
                    when (filter) {
                        "CORS" -> delay(1)
                        "CSRF" -> delay(2)
                        "Authentication" -> delay(5)
                        "Authorization" -> delay(3)
                        "RateLimit" -> delay(2)
                        "XSS" -> delay(1)
                        "ContentType" -> delay(1)
                        "HSTS" -> delay(1)
                    }
                }
                processRequest(requestSize)
            }
        }
        
        return SecurityMetrics(
            operationType = "FILTER_CHAIN_$name",
            algorithm = "Multi_Filter",
            dataSize = requestSize,
            operationTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000,
            memoryUsed = estimateFilterMemoryUsage(filters),
            successCount = iterations.toLong()
        )
    }
    
    // ============================================================
    // 종합 성능 분석
    // ============================================================
    
    suspend fun runComprehensiveAnalysis() {
        println("🔍 Running Comprehensive Security Performance Analysis")
        println("=" * 80)
        
        val jwtResult = analyzeJWTPerformance()
        val cryptoResult = analyzeCryptoPerformance()
        val protocolResult = analyzeProtocolPerformance()
        val rateLimitResult = analyzeRateLimitingImpact()
        
        analyzeSecurityFilterChain()
        
        println("\n📈 COMPREHENSIVE ANALYSIS SUMMARY")
        println("=" * 80)
        
        val overallScore = calculateOverallSecurityScore(jwtResult, cryptoResult, protocolResult, rateLimitResult)
        
        println("Overall Security Performance Score: ${"%.2f".format(overallScore)}/100")
        println()
        
        generateComprehensiveRecommendations(jwtResult, cryptoResult, protocolResult, rateLimitResult)
    }
    
    // ============================================================
    // 유틸리티 메서드들
    // ============================================================
    
    private fun calculateOverallThroughput(metrics: List<SecurityMetrics>): Double {
        return metrics.map { it.throughput }.average()
    }
    
    private fun measureJWTMemoryFootprint(): Long {
        val runtime = Runtime.getRuntime()
        val beforeMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // JWT 토큰 1000개 생성
        val secretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray())
        val tokens = mutableListOf<String>()
        
        repeat(1000) {
            val token = Jwts.builder()
                .setSubject("user$it")
                .setIssuedAt(Date())
                .setExpiration(Date(System.currentTimeMillis() + 3600000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact()
            tokens.add(token)
        }
        
        val afterMemory = runtime.totalMemory() - runtime.freeMemory()
        return afterMemory - beforeMemory
    }
    
    private fun calculateCryptoScore(encryption: SecurityMetrics, decryption: SecurityMetrics, 
                                   hashing: SecurityMetrics, password: SecurityMetrics): Double {
        val encryptionScore = minOf(encryption.throughput / 1000 * 25, 25.0)
        val decryptionScore = minOf(decryption.throughput / 1000 * 25, 25.0)
        val hashingScore = minOf(hashing.throughput / 10000 * 25, 25.0)
        val passwordScore = minOf(password.throughput / 10 * 25, 25.0)
        
        return encryptionScore + decryptionScore + hashingScore + passwordScore
    }
    
    private fun estimateRateLimitMemoryUsage(): Long {
        // Rate Limiting을 위한 메모리 사용량 추정 (KB)
        val clientCount = 10000 // 예상 클라이언트 수
        val memoryPerClient = 256 // 클라이언트당 메모리 사용량 (bytes)
        return (clientCount * memoryPerClient) / 1024 // KB로 변환
    }
    
    private fun estimateFilterMemoryUsage(filters: List<String>): Long {
        return filters.size * 64L // 필터당 64KB 추정
    }
    
    private fun calculateOverallSecurityScore(jwt: JWTPerformanceResult, crypto: CryptoPerformanceResult,
                                            protocol: ProtocolPerformanceResult, rateLimit: RateLimitingResult): Double {
        val jwtScore = minOf(jwt.overallThroughput / 1000 * 25, 25.0)
        val cryptoScore = crypto.overallScore * 0.25
        val protocolScore = maxOf(25 - (protocol.performanceDifference * 0.5), 0.0)
        val rateLimitScore = maxOf(25 - (rateLimit.throughputImpact * 0.5), 0.0)
        
        return jwtScore + cryptoScore + protocolScore + rateLimitScore
    }
    
    // ============================================================
    // 추천사항 생성 메서드들
    // ============================================================
    
    private fun generateJWTRecommendations(generation: SecurityMetrics, validation: SecurityMetrics): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (generation.throughput < 500) {
            recommendations.add("JWT 토큰 생성 성능이 낮습니다. 비동기 처리나 토큰 캐싱을 고려하세요.")
        }
        
        if (validation.throughput < 1000) {
            recommendations.add("JWT 검증 성능 개선을 위해 공개키 캐싱을 구현하세요.")
        }
        
        if (generation.errorCount > 0 || validation.errorCount > 0) {
            recommendations.add("JWT 처리 중 오류가 발생했습니다. 키 관리와 토큰 형식을 검토하세요.")
        }
        
        recommendations.add("JWT 토큰 만료 시간을 적절히 설정하여 보안과 성능의 균형을 맞추세요.")
        recommendations.add("RS256 대신 HS256 사용을 고려하여 성능을 개선할 수 있습니다.")
        
        return recommendations
    }
    
    private fun generateCryptoRecommendations(encryption: SecurityMetrics, hashing: SecurityMetrics, 
                                            password: SecurityMetrics): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (encryption.throughput < 100) {
            recommendations.add("암호화 성능이 낮습니다. 하드웨어 가속을 활용하거나 더 빠른 암호화 방식을 고려하세요.")
        }
        
        if (hashing.throughput < 1000) {
            recommendations.add("해싱 성능 개선을 위해 SHA-256 대신 BLAKE2를 고려해보세요.")
        }
        
        if (password.throughput < 10) {
            recommendations.add("패스워드 인코딩이 너무 느립니다. BCrypt rounds 수를 조정하거나 Argon2 사용을 고려하세요.")
        }
        
        recommendations.add("민감한 데이터는 AES-256-GCM으로 암호화하고, 해시는 솔트를 사용하세요.")
        recommendations.add("암호화 키는 안전한 키 관리 시스템을 통해 관리하세요.")
        
        return recommendations
    }
    
    private fun generateProtocolRecommendations(performanceDifference: Double): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (performanceDifference > 50) {
            recommendations.add("HTTPS 오버헤드가 큽니다. HTTP/2나 TLS 1.3 사용을 고려하세요.")
            recommendations.add("SSL 세션 재사용과 OCSP Stapling을 활성화하세요.")
        } else if (performanceDifference > 20) {
            recommendations.add("HTTPS 성능은 양호하나, 연결 풀링과 Keep-Alive를 최적화하세요.")
        }
        
        recommendations.add("CDN을 활용하여 SSL 종료 지점을 최적화하세요.")
        recommendations.add("HSTS와 certificate pinning으로 보안을 강화하세요.")
        
        return recommendations
    }
    
    private fun generateRateLimitRecommendations(throughputImpact: Double, memoryImpact: Long): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (throughputImpact > 30) {
            recommendations.add("Rate Limiting 오버헤드가 큽니다. Redis를 사용한 분산 Rate Limiting을 고려하세요.")
        }
        
        if (memoryImpact > 10000) { // 10MB 이상
            recommendations.add("Rate Limiting 메모리 사용량이 높습니다. 만료 정책을 개선하세요.")
        }
        
        recommendations.add("Token Bucket 대신 Sliding Window 방식을 고려해보세요.")
        recommendations.add("클라이언트별 차등 Rate Limiting을 구현하세요.")
        
        return recommendations
    }
    
    private fun generateFilterChainRecommendations() {
        println("🎯 Security Filter Chain Optimization Recommendations:")
        println("   1. 필수 필터만 활성화하여 성능 최적화")
        println("   2. 필터 순서 최적화 (빠른 거부 조건을 앞쪽에 배치)")
        println("   3. 필터별 비동기 처리 구현")
        println("   4. 캐싱을 활용한 중복 검증 방지")
        println("   5. 조건부 필터 적용 (특정 엔드포인트에만 적용)")
        println()
    }
    
    private fun generateComprehensiveRecommendations(jwt: JWTPerformanceResult, crypto: CryptoPerformanceResult,
                                                   protocol: ProtocolPerformanceResult, rateLimit: RateLimitingResult) {
        println("🔧 COMPREHENSIVE OPTIMIZATION RECOMMENDATIONS")
        println("-" * 60)
        
        println("1. JWT 최적화:")
        jwt.recommendations.forEach { println("   • $it") }
        
        println("\n2. 암호화 최적화:")
        crypto.recommendations.forEach { println("   • $it") }
        
        println("\n3. 프로토콜 최적화:")
        protocol.recommendations.forEach { println("   • $it") }
        
        println("\n4. Rate Limiting 최적화:")
        rateLimit.recommendations.forEach { println("   • $it") }
        
        println("\n5. 전체적인 권장사항:")
        println("   • 보안과 성능의 균형점을 찾아 적절한 보안 수준을 설정하세요")
        println("   • 정기적인 보안 성능 모니터링을 구현하세요")
        println("   • 보안 정책을 환경별로 차등 적용하세요 (개발/스테이징/프로덕션)")
        println("   • 보안 오버헤드를 고려한 인프라 용량 계획을 수립하세요")
        println("   • 보안 성능 메트릭을 모니터링 대시보드에 포함시키세요")
    }
    
    // ============================================================
    // 결과 출력 메서드들
    // ============================================================
    
    private fun printJWTAnalysisResults(result: JWTPerformanceResult) {
        println("\n📊 JWT Performance Analysis Results:")
        println("-" * 50)
        
        with(result.tokenGeneration) {
            println("Token Generation:")
            println("  • Algorithm: $algorithm")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} tokens/sec")
            println("  • Success Rate: ${"%.2f".format((successCount.toDouble() / (successCount + errorCount)) * 100)}%")
        }
        
        with(result.tokenValidation) {
            println("\nToken Validation:")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} validations/sec")
            println("  • Success Rate: ${"%.2f".format((successCount.toDouble() / (successCount + errorCount)) * 100)}%")
        }
        
        with(result.tokenParsing) {
            println("\nToken Parsing:")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} parses/sec")
            println("  • Success Rate: ${"%.2f".format((successCount.toDouble() / (successCount + errorCount)) * 100)}%")
        }
        
        println("\nOverall JWT Performance:")
        println("  • Average Throughput: ${"%.2f".format(result.overallThroughput)} ops/sec")
        println("  • Memory Footprint: ${result.memoryFootprint / 1024}KB for 1000 tokens")
        
        performanceLog.addAll(listOf(result.tokenGeneration, result.tokenValidation, result.tokenParsing))
    }
    
    private fun printCryptoAnalysisResults(result: CryptoPerformanceResult) {
        println("\n🔒 Cryptographic Performance Analysis Results:")
        println("-" * 50)
        
        with(result.encryptionMetrics) {
            println("AES Encryption (${algorithm}):")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} ops/sec")
            println("  • Data Size: ${dataSize} bytes")
        }
        
        with(result.decryptionMetrics) {
            println("\nAES Decryption (${algorithm}):")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} ops/sec")
        }
        
        with(result.hashingMetrics) {
            println("\nBest Hashing Algorithm (${algorithm}):")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} hashes/sec")
        }
        
        with(result.passwordMetrics) {
            println("\nPassword Encoding (${algorithm}):")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} encodings/sec")
        }
        
        println("\nOverall Crypto Score: ${"%.2f".format(result.overallScore)}/100")
        
        performanceLog.addAll(listOf(result.encryptionMetrics, result.decryptionMetrics, 
                                   result.hashingMetrics, result.passwordMetrics))
    }
    
    private fun printProtocolAnalysisResults(result: ProtocolPerformanceResult) {
        println("\n🌐 Protocol Performance Analysis Results:")
        println("-" * 50)
        
        with(result.httpMetrics) {
            println("HTTP Performance:")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} req/sec")
        }
        
        with(result.httpsMetrics) {
            println("\nHTTPS Performance (${algorithm}):")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} req/sec")
        }
        
        println("\nProtocol Comparison:")
        println("  • Performance Difference: ${"%.2f".format(result.performanceDifference)}%")
        println("  • Security Overhead: ${"%.2f".format(result.securityOverhead)}ms")
        
        if (result.performanceDifference < 10) {
            println("  • ✅ HTTPS 오버헤드가 적절합니다")
        } else if (result.performanceDifference < 30) {
            println("  • ⚠️ HTTPS 오버헤드가 다소 높습니다")
        } else {
            println("  • ❌ HTTPS 오버헤드가 매우 높습니다")
        }
        
        performanceLog.addAll(listOf(result.httpMetrics, result.httpsMetrics))
    }
    
    private fun printRateLimitAnalysisResults(result: RateLimitingResult) {
        println("\n⏱️ Rate Limiting Impact Analysis Results:")
        println("-" * 50)
        
        with(result.rateLimitOverhead) {
            println("Rate Limiting Performance:")
            println("  • Algorithm: ${algorithm}")
            println("  • Processing Time: ${operationTime}ms")
            println("  • Throughput: ${"%.2f".format(throughput)} req/sec")
            println("  • Success Rate: ${"%.2f".format((successCount.toDouble() / (successCount + errorCount)) * 100)}%")
            println("  • Blocked Requests: ${errorCount}")
        }
        
        println("\nImpact Analysis:")
        println("  • Throughput Impact: ${"%.2f".format(result.throughputImpact)}%")
        println("  • Memory Impact: ${result.memoryImpact}KB")
        println("  • Latency Increase: ${"%.2f".format(result.latencyIncrease)}ms")
        
        if (result.throughputImpact < 10) {
            println("  • ✅ Rate Limiting 오버헤드가 적절합니다")
        } else if (result.throughputImpact < 30) {
            println("  • ⚠️ Rate Limiting 오버헤드가 다소 높습니다")
        } else {
            println("  • ❌ Rate Limiting 오버헤드가 매우 높습니다")
        }
        
        performanceLog.add(result.rateLimitOverhead)
    }
    
    // ============================================================
    // 보고서 생성
    // ============================================================
    
    private fun generatePerformanceReport() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val reportPath = "/Users/anb-28/Reservation/reports/security-performance-report-$timestamp.csv"
        
        // 보고서 디렉토리 생성
        val reportDir = java.io.File("/Users/anb-28/Reservation/reports")
        if (!reportDir.exists()) {
            reportDir.mkdirs()
        }
        
        // CSV 보고서 생성
        val csvContent = StringBuilder()
        csvContent.appendLine("Timestamp,Operation Type,Algorithm,Key Size,Data Size,Operation Time (ms),Throughput (ops/sec),Memory Used (KB),CPU Intensive,Success Count,Error Count")
        
        performanceLog.forEach { metric ->
            csvContent.appendLine("${metric.timestamp},${metric.operationType},${metric.algorithm},${metric.keySize},${metric.dataSize},${metric.operationTime},${"%.2f".format(metric.throughput)},${metric.memoryUsed},${metric.cpuIntensive},${metric.successCount},${metric.errorCount}")
        }
        
        java.io.File(reportPath).writeText(csvContent.toString())
        
        println("\n📋 Performance Report Generated:")
        println("   Location: $reportPath")
        println("   Total Metrics: ${performanceLog.size}")
        println("   Analysis Complete! 🎉")
    }
}