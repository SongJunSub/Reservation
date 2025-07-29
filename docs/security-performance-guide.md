# 🔐 Security Performance Overhead Analysis Guide

## 목차
1. [개요](#개요)
2. [JWT 토큰 처리 성능 분석](#jwt-토큰-처리-성능-분석)
3. [암호화/복호화 오버헤드 측정](#암호화복호화-오버헤드-측정)
4. [HTTPS vs HTTP 성능 비교](#https-vs-http-성능-비교)
5. [Rate Limiting 성능 영향 분석](#rate-limiting-성능-영향-분석)
6. [보안 필터 체인 최적화](#보안-필터-체인-최적화)
7. [성능 모니터링 및 메트릭](#성능-모니터링-및-메트릭)
8. [최적화 전략 및 권장사항](#최적화-전략-및-권장사항)
9. [실제 구현 예제](#실제-구현-예제)
10. [트러블슈팅 가이드](#트러블슈팅-가이드)

---

## 개요

보안 기능은 애플리케이션의 필수 요소이지만, 성능에 미치는 영향을 이해하고 최적화하는 것이 중요합니다. 본 가이드는 Spring Boot 기반 예약 시스템에서 보안 기능의 성능 오버헤드를 체계적으로 분석하고 최적화하는 방법을 제공합니다.

### 주요 분석 영역

| 분석 영역 | 주요 메트릭 | 성능 목표 |
|-----------|-------------|----------|
| JWT 처리 | Token/sec, 응답시간 | >1000 tokens/sec |
| 암호화 연산 | Ops/sec, CPU 사용률 | >500 ops/sec |
| 프로토콜 비교 | HTTPS 오버헤드 | <30% 증가 |
| Rate Limiting | 처리량 영향 | <10% 감소 |
| 필터 체인 | 레이턴시 추가 | <50ms |

### 성능 측정 방법론

```kotlin
// 성능 측정 기본 패턴
data class SecurityMetrics(
    val operationType: String,
    val algorithm: String,
    val operationTime: Long,
    val throughput: Double,
    val memoryUsed: Long,
    val successCount: Long,
    val errorCount: Long
)

// 측정 예제
fun measureSecurityOperation(operation: () -> Unit): SecurityMetrics {
    val startTime = System.currentTimeMillis()
    val startMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    
    var successCount = 0L
    var errorCount = 0L
    
    repeat(iterations) {
        try {
            operation()
            successCount++
        } catch (e: Exception) {
            errorCount++
        }
    }
    
    val endTime = System.currentTimeMillis()
    val endMemory = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    
    return SecurityMetrics(
        operationType = "SECURITY_OP",
        algorithm = "ALGORITHM_NAME",
        operationTime = endTime - startTime,
        throughput = (iterations.toDouble() / (endTime - startTime)) * 1000,
        memoryUsed = endMemory - startMemory,
        successCount = successCount,
        errorCount = errorCount
    )
}
```

---

## JWT 토큰 처리 성능 분석

### JWT 성능 특성

JWT 토큰 처리는 다음 세 단계로 구성됩니다:
- **토큰 생성**: 클레임 생성 + 서명
- **토큰 검증**: 서명 검증 + 만료 확인
- **토큰 파싱**: 클레임 추출 + 데이터 변환

### 1. JWT 토큰 생성 최적화

```kotlin
@Component
class OptimizedJWTTokenGenerator {
    
    // 서명 키 캐싱 (성능 향상)
    private val signingKey by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }
    
    // 토큰 생성 최적화
    fun generateToken(user: User): String {
        val now = Date()
        val expiration = Date(now.time + tokenValidityInMilliseconds)
        
        return Jwts.builder()
            .setSubject(user.username)
            .setIssuedAt(now)
            .setExpiration(expiration)
            .addClaims(buildOptimizedClaims(user)) // 필요한 클레임만 포함
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compressWith(CompressionCodecs.DEFLATE) // 토큰 크기 압축
            .compact()
    }
    
    private fun buildOptimizedClaims(user: User): Map<String, Any> {
        return mapOf(
            "roles" to user.roles.map { it.name }, // 필요한 권한만
            "uid" to user.id, // 사용자 ID만
            // 불필요한 클레임 제거로 토큰 크기 최소화
        )
    }
}
```

### 2. JWT 토큰 검증 최적화

```kotlin
@Component
class OptimizedJWTTokenValidator {
    
    // 파서 캐싱 (매번 생성하지 않음)
    private val jwtParser by lazy {
        Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
    }
    
    // 블랙리스트 캐시 (Redis 또는 로컬 캐시)
    @Cacheable("jwt-blacklist")
    fun isTokenBlacklisted(tokenId: String): Boolean {
        return blacklistedTokenRepository.existsByTokenId(tokenId)
    }
    
    fun validateToken(token: String): ValidationResult {
        try {
            val claims = jwtParser.parseClaimsJws(token).body
            
            // 빠른 만료 검증
            if (claims.expiration.before(Date())) {
                return ValidationResult.EXPIRED
            }
            
            // 블랙리스트 확인 (필요한 경우만)
            val tokenId = claims.id
            if (tokenId != null && isTokenBlacklisted(tokenId)) {
                return ValidationResult.BLACKLISTED
            }
            
            return ValidationResult.VALID
        } catch (e: ExpiredJwtException) {
            return ValidationResult.EXPIRED
        } catch (e: JwtException) {
            return ValidationResult.INVALID
        }
    }
}
```

### 3. JWT 성능 벤치마크

```kotlin
class JWTPerformanceBenchmark {
    
    fun benchmarkJWTOperations(): JWTPerformanceResult {
        val sampleUser = User("testuser", "test@example.com", setOf(Role.USER))
        val iterations = 10000
        
        // 토큰 생성 벤치마크
        val generationMetrics = measureTimeMillis {
            repeat(iterations) {
                tokenGenerator.generateToken(sampleUser)
            }
        }
        
        // 토큰 검증 벤치마크
        val sampleToken = tokenGenerator.generateToken(sampleUser)
        val validationMetrics = measureTimeMillis {
            repeat(iterations) {
                tokenValidator.validateToken(sampleToken)
            }
        }
        
        return JWTPerformanceResult(
            generationThroughput = (iterations.toDouble() / generationMetrics) * 1000,
            validationThroughput = (iterations.toDouble() / validationMetrics) * 1000,
            recommendations = generateJWTRecommendations(generationMetrics, validationMetrics)
        )
    }
}
```

### JWT 최적화 권장사항

1. **알고리즘 선택**
   - HS256 vs RS256: 대칭키(HS256)가 더 빠름
   - 마이크로서비스: RS256 (공개키 배포)
   - 단일 서비스: HS256 (성능 우선)

2. **토큰 크기 최적화**
   - 필요한 클레임만 포함
   - 압축 사용 (DEFLATE, GZIP)
   - 긴 문자열 대신 ID 사용

3. **캐싱 전략**
   - 서명 키 캐싱
   - 파서 인스턴스 재사용
   - 블랙리스트 캐싱

---

## 암호화/복호화 오버헤드 측정

### 1. AES 암호화 성능 분석

```kotlin
class CryptographicPerformanceAnalyzer {
    
    // AES-256-GCM 암호화 벤치마크
    fun benchmarkAESPerformance(data: ByteArray): CryptoMetrics {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val secretKey = keyGen.generateKey()
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iterations = 1000
        
        // 암호화 성능 측정
        val encryptionTime = measureTimeMillis {
            repeat(iterations) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.doFinal(data)
            }
        }
        
        // 메모리 사용량 측정
        val memoryUsage = measureMemoryUsage {
            repeat(100) {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                cipher.doFinal(data)
            }
        }
        
        return CryptoMetrics(
            algorithm = "AES-256-GCM",
            dataSize = data.size,
            encryptionThroughput = (iterations.toDouble() / encryptionTime) * 1000,
            memoryUsage = memoryUsage
        )
    }
    
    // 해싱 알고리즘 비교
    fun compareHashingAlgorithms(data: ByteArray): Map<String, Double> {
        val algorithms = listOf("SHA-256", "SHA-512", "SHA-3-256", "BLAKE2b-256")
        val results = mutableMapOf<String, Double>()
        val iterations = 10000
        
        algorithms.forEach { algorithm ->
            val time = measureTimeMillis {
                repeat(iterations) {
                    MessageDigest.getInstance(algorithm).digest(data)
                }
            }
            results[algorithm] = (iterations.toDouble() / time) * 1000
        }
        
        return results.toSortedMap(compareByDescending { results[it] })
    }
}
```

### 2. 패스워드 해싱 최적화

```kotlin
@Configuration
class PasswordEncodingConfiguration {
    
    // BCrypt 강도 최적화
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        // 보안과 성능의 균형점 찾기
        val strength = determineOptimalBCryptStrength()
        return BCryptPasswordEncoder(strength)
    }
    
    private fun determineOptimalBCryptStrength(): Int {
        // 목표: 100ms 이내 처리
        val targetTime = 100L
        var strength = 4
        
        while (strength <= 15) {
            val encoder = BCryptPasswordEncoder(strength)
            val time = measureTimeMillis {
                encoder.encode("testpassword")
            }
            
            if (time > targetTime) {
                return maxOf(4, strength - 1)
            }
            strength++
        }
        
        return 10 // 기본값
    }
    
    // Argon2 대안 고려
    @Bean
    @ConditionalOnProperty("security.password.encoder", havingValue = "argon2")
    fun argon2PasswordEncoder(): PasswordEncoder {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
    }
}
```

### 3. 하드웨어 가속 활용

```kotlin
@Component
class HardwareAcceleratedCrypto {
    
    init {
        // AES-NI 하드웨어 가속 확인
        checkHardwareAcceleration()
    }
    
    private fun checkHardwareAcceleration() {
        val providers = Security.getProviders()
        providers.forEach { provider ->
            println("Provider: ${provider.name}")
            provider.services.forEach { service ->
                if (service.algorithm.contains("AES")) {
                    println("  AES Service: ${service.algorithm}")
                }
            }
        }
    }
    
    // 최적화된 암호화 서비스
    @Service
    class OptimizedEncryptionService {
        
        private val cipher = ThreadLocal.withInitial {
            Cipher.getInstance("AES/GCM/NoPadding")
        }
        
        fun encrypt(data: ByteArray, key: SecretKey): EncryptionResult {
            val localCipher = cipher.get()
            localCipher.init(Cipher.ENCRYPT_MODE, key)
            
            val iv = localCipher.iv
            val encryptedData = localCipher.doFinal(data)
            
            return EncryptionResult(encryptedData, iv)
        }
    }
}
```

---

## HTTPS vs HTTP 성능 비교

### 1. SSL/TLS 오버헤드 분석

```kotlin
class ProtocolPerformanceAnalyzer {
    
    fun analyzeHTTPSOverhead(): ProtocolComparisonResult {
        val testData = generateTestData(1024) // 1KB 데이터
        val iterations = 1000
        
        // HTTP 시뮬레이션 (암호화 없음)
        val httpTime = measureTimeMillis {
            repeat(iterations) {
                simulateHTTPRequest(testData)
            }
        }
        
        // HTTPS 시뮬레이션 (TLS 1.3 + AES-256-GCM)
        val httpsTime = measureTimeMillis {
            repeat(iterations) {
                simulateHTTPSRequest(testData)
            }
        }
        
        val overhead = ((httpsTime - httpTime).toDouble() / httpTime) * 100
        
        return ProtocolComparisonResult(
            httpThroughput = (iterations.toDouble() / httpTime) * 1000,
            httpsThroughput = (iterations.toDouble() / httpsTime) * 1000,
            overheadPercentage = overhead,
            recommendations = generateProtocolRecommendations(overhead)
        )
    }
    
    private fun simulateHTTPSRequest(data: ByteArray) {
        // SSL 핸드셰이크 시뮬레이션 (첫 연결)
        simulateSSLHandshake()
        
        // 데이터 암호화
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = generateAESKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.doFinal(data)
        
        // 네트워크 전송 시뮬레이션
        simulateNetworkTransfer(data.size + 16) // GCM 태그 크기 추가
    }
}
```

### 2. TLS 최적화 설정

```yaml
# application.yml - SSL 최적화 설정
server:
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
    ciphers: 
      - TLS_AES_256_GCM_SHA384
      - TLS_CHACHA20_POLY1305_SHA256
      - TLS_AES_128_GCM_SHA256
    key-store: classpath:ssl/keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    
  # HTTP/2 활성화 (성능 향상)
  http2:
    enabled: true
    
  # 연결 최적화
  tomcat:
    connection-timeout: 20000
    keep-alive-timeout: 60000
    max-keep-alive-requests: 100
```

### 3. 연결 풀링 최적화

```kotlin
@Configuration
class HTTPSOptimizationConfig {
    
    @Bean
    fun restTemplate(): RestTemplate {
        val httpClient = HttpClients.custom()
            .setMaxConnTotal(200)
            .setMaxConnPerRoute(20)
            .setKeepAliveStrategy { _, _ -> 60 * 1000 } // 60초
            .setConnectionTimeToLive(60, TimeUnit.SECONDS)
            .setSSLContext(createOptimizedSSLContext())
            .build()
            
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
        return RestTemplate(requestFactory)
    }
    
    private fun createOptimizedSSLContext(): SSLContext {
        val sslContext = SSLContext.getInstance("TLSv1.3")
        
        // 신뢰할 수 있는 인증서 관리자 설정
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        
        sslContext.init(null, trustManagerFactory.trustManagers, SecureRandom())
        return sslContext
    }
}
```

---

## Rate Limiting 성능 영향 분석

### 1. Rate Limiting 구현 비교

```kotlin
// 1. 메모리 기반 Rate Limiter (빠름, 단일 인스턴스)
@Component
class InMemoryRateLimiter {
    
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private val cleanupScheduler = Executors.newScheduledThreadPool(1)
    
    init {
        // 주기적으로 만료된 버킷 정리
        cleanupScheduler.scheduleAtFixedRate(::cleanupExpiredBuckets, 1, 1, TimeUnit.MINUTES)
    }
    
    fun isAllowed(clientId: String, limit: Int, windowMs: Long): Boolean {
        val bucket = buckets.computeIfAbsent(clientId) { 
            TokenBucket(limit, windowMs) 
        }
        
        return bucket.tryConsume()
    }
    
    private class TokenBucket(
        private val capacity: Int,
        private val refillIntervalMs: Long
    ) {
        private val tokens = AtomicInteger(capacity)
        private val lastRefill = AtomicLong(System.currentTimeMillis())
        
        fun tryConsume(): Boolean {
            refillIfNeeded()
            return tokens.getAndUpdate { current ->
                if (current > 0) current - 1 else current
            } > 0
        }
        
        private fun refillIfNeeded() {
            val now = System.currentTimeMillis()
            val lastRefillTime = lastRefill.get()
            
            if (now - lastRefillTime >= refillIntervalMs) {
                if (lastRefill.compareAndSet(lastRefillTime, now)) {
                    tokens.set(capacity)
                }
            }
        }
    }
}

// 2. Redis 기반 Rate Limiter (확장 가능, 분산 환경)
@Component
class RedisRateLimiter(
    private val redisTemplate: RedisTemplate<String, String>
) {
    
    private val luaScript = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        
        -- 현재 윈도우의 시작 시간
        local window_start = current_time - window
        
        -- 만료된 요청 제거
        redis.call('ZREMRANGEBYSCORE', key, 0, window_start)
        
        -- 현재 요청 수 확인
        local current_requests = redis.call('ZCARD', key)
        
        if current_requests < limit then
            -- 요청 추가
            redis.call('ZADD', key, current_time, current_time)
            redis.call('EXPIRE', key, math.ceil(window / 1000))
            return { 1, limit - current_requests - 1 }
        else
            return { 0, 0 }
        end
    """.trimIndent()
    
    fun isAllowed(clientId: String, limit: Int, windowMs: Long): RateLimitResult {
        val key = "rate_limit:$clientId"
        val currentTime = System.currentTimeMillis()
        
        val result = redisTemplate.execute<List<Long>> { connection ->
            connection.eval(
                luaScript.toByteArray(),
                1,
                key.toByteArray(),
                windowMs.toString().toByteArray(),
                limit.toString().toByteArray(),
                currentTime.toString().toByteArray()
            ) as List<Long>
        }
        
        return RateLimitResult(
            allowed = result[0] == 1L,
            remainingRequests = result[1].toInt()
        )
    }
}
```

### 2. Rate Limiting 성능 측정

```kotlin
class RateLimitingPerformanceBenchmark {
    
    fun compareRateLimitingImplementations(): RateLimitingComparisonResult {
        val clientIds = (1..100).map { "client_$it" }
        val iterations = 10000
        val limit = 100 // 초당 100 요청
        
        // 메모리 기반 성능 측정
        val memoryBasedMetrics = benchmarkRateLimiter(
            limiter = inMemoryRateLimiter,
            clientIds = clientIds,
            iterations = iterations,
            limit = limit
        )
        
        // Redis 기반 성능 측정
        val redisBasedMetrics = benchmarkRateLimiter(
            limiter = redisRateLimiter,
            clientIds = clientIds,
            iterations = iterations,
            limit = limit
        )
        
        return RateLimitingComparisonResult(
            memoryBased = memoryBasedMetrics,
            redisBased = redisBasedMetrics,
            performanceDifference = calculatePerformanceDifference(memoryBasedMetrics, redisBasedMetrics)
        )
    }
    
    private fun benchmarkRateLimiter(
        limiter: RateLimiter,
        clientIds: List<String>,
        iterations: Int,
        limit: Int
    ): RateLimitingMetrics {
        var allowedRequests = 0
        var blockedRequests = 0
        
        val totalTime = measureTimeMillis {
            repeat(iterations) { i ->
                val clientId = clientIds[i % clientIds.size]
                if (limiter.isAllowed(clientId, limit, 1000)) {
                    allowedRequests++
                } else {
                    blockedRequests++
                }
            }
        }
        
        return RateLimitingMetrics(
            totalRequests = iterations,
            allowedRequests = allowedRequests,
            blockedRequests = blockedRequests,
            processingTime = totalTime,
            throughput = (iterations.toDouble() / totalTime) * 1000
        )
    }
}
```

### 3. Rate Limiting 최적화 전략

```kotlin
@Configuration
class RateLimitingOptimizationConfig {
    
    // 적응형 Rate Limiting
    @Bean
    fun adaptiveRateLimiter(): AdaptiveRateLimiter {
        return AdaptiveRateLimiter(
            baseLimit = 100,
            maxLimit = 1000,
            adjustmentInterval = Duration.ofMinutes(5)
        )
    }
    
    // 클라이언트 등급별 Rate Limiting
    @Bean
    fun tieredRateLimiter(): TieredRateLimiter {
        val tiers = mapOf(
            ClientTier.PREMIUM to RateLimitConfig(1000, Duration.ofSeconds(1)),
            ClientTier.STANDARD to RateLimitConfig(500, Duration.ofSeconds(1)),
            ClientTier.BASIC to RateLimitConfig(100, Duration.ofSeconds(1))
        )
        return TieredRateLimiter(tiers)
    }
}

class AdaptiveRateLimiter(
    private val baseLimit: Int,
    private val maxLimit: Int,
    private val adjustmentInterval: Duration
) {
    
    private val currentLimits = ConcurrentHashMap<String, Int>()
    private val performanceMetrics = ConcurrentHashMap<String, PerformanceHistory>()
    
    fun isAllowed(clientId: String): Boolean {
        val currentLimit = getCurrentLimit(clientId)
        val allowed = basicRateLimit(clientId, currentLimit)
        
        // 성능 메트릭 업데이트
        updatePerformanceMetrics(clientId, allowed)
        
        return allowed
    }
    
    private fun getCurrentLimit(clientId: String): Int {
        return currentLimits.computeIfAbsent(clientId) { 
            adjustLimitBasedOnHistory(clientId) 
        }
    }
    
    private fun adjustLimitBasedOnHistory(clientId: String): Int {
        val history = performanceMetrics[clientId] ?: return baseLimit
        
        return when {
            history.averageResponseTime < 100 -> minOf(maxLimit, baseLimit * 2)
            history.averageResponseTime > 500 -> maxOf(baseLimit / 2, 10)
            else -> baseLimit
        }
    }
}
```

---

## 보안 필터 체인 최적화

### 1. 필터 순서 최적화

```kotlin
@Configuration
@EnableWebSecurity
class SecurityFilterChainConfig {
    
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        return http
            // 1. CORS 필터 (빠른 프리플라이트 처리)
            .addFilterBefore(corsFilter(), UsernamePasswordAuthenticationFilter::class.java)
            
            // 2. Rate Limiting (DOS 방지, 빠른 거부)
            .addFilterBefore(rateLimitingFilter(), CorsFilter::class.java)
            
            // 3. 정적 리소스 예외 처리 (인증 스킵)
            .addFilterBefore(staticResourceFilter(), RateLimitingFilter::class.java)
            
            // 4. JWT 인증 필터
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            
            // 5. 권한 검사 필터
            .addFilterAfter(authorizationFilter(), JwtAuthenticationFilter::class.java)
            
            // 보안 헤더 설정
            .headers { headers ->
                headers
                    .frameOptions().deny()
                    .contentTypeOptions().and()
                    .httpStrictTransportSecurity { hstsConfig ->
                        hstsConfig
                            .maxAgeInSeconds(31536000)
                            .includeSubdomains(true)
                    }
            }
            
            // CSRF 비활성화 (JWT 사용)
            .csrf().disable()
            
            // 세션 관리
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            
            .build()
    }
}
```

### 2. 조건부 필터 적용

```kotlin
// 경로별 필터 적용 최적화
@Component
class ConditionalSecurityFilter : OncePerRequestFilter() {
    
    private val publicPaths = setOf(
        "/api/public/**",
        "/health",
        "/metrics",
        "/swagger-ui/**"
    )
    
    private val lightSecurityPaths = setOf(
        "/api/auth/**"
    )
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestPath = request.requestURI
        
        when {
            isPublicPath(requestPath) -> {
                // 공개 경로: 최소 보안만 적용
                applyMinimalSecurity(request, response)
            }
            
            isLightSecurityPath(requestPath) -> {
                // 경량 보안 경로: 기본 인증만
                applyLightSecurity(request, response)
            }
            
            else -> {
                // 전체 보안 적용
                applyFullSecurity(request, response)
            }
        }
        
        filterChain.doFilter(request, response)
    }
    
    private fun applyMinimalSecurity(request: HttpServletRequest, response: HttpServletResponse) {
        // CORS 헤더만 추가
        response.setHeader("Access-Control-Allow-Origin", "*")
    }
    
    private fun applyLightSecurity(request: HttpServletRequest, response: HttpServletResponse) {
        applyMinimalSecurity(request, response)
        // Rate Limiting 추가
        rateLimiter.checkLimit(getClientId(request))
    }
    
    private fun applyFullSecurity(request: HttpServletRequest, response: HttpServletResponse) {
        applyLightSecurity(request, response)
        // JWT 검증, 권한 확인 등 전체 보안 체크
        validateJWTToken(request)
        checkAuthorization(request)
    }
}
```

### 3. 비동기 필터 처리

```kotlin
@Component
class AsyncSecurityFilter : OncePerRequestFilter() {
    
    private val securityExecutor = Executors.newFixedThreadPool(10)
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 병렬로 실행 가능한 보안 검사들
        val securityChecks = listOf(
            CompletableFuture.supplyAsync({ checkRateLimit(request) }, securityExecutor),
            CompletableFuture.supplyAsync({ validateCSRFToken(request) }, securityExecutor),
            CompletableFuture.supplyAsync({ checkBlacklist(request) }, securityExecutor)
        )
        
        // 모든 보안 검사가 완료될 때까지 대기
        val results = CompletableFuture.allOf(*securityChecks.toTypedArray())
            .thenApply { securityChecks.map { it.get() } }
            .get(500, TimeUnit.MILLISECONDS) // 타임아웃 설정
        
        // 결과 검증
        if (results.all { it.isValid }) {
            filterChain.doFilter(request, response)
        } else {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Security check failed")
        }
    }
}
```

---

## 성능 모니터링 및 메트릭

### 1. 보안 성능 메트릭 수집

```kotlin
@Component
class SecurityPerformanceMetrics {
    
    private val meterRegistry: MeterRegistry
    
    // JWT 처리 시간 메트릭
    private val jwtProcessingTimer = Timer.builder("security.jwt.processing.time")
        .description("JWT token processing time")
        .register(meterRegistry)
    
    // 암호화 연산 카운터
    private val encryptionCounter = Counter.builder("security.encryption.operations")
        .description("Number of encryption operations")
        .register(meterRegistry)
    
    // Rate Limiting 메트릭
    private val rateLimitCounter = Counter.builder("security.rate.limit.hits")
        .description("Rate limit hits")
        .tag("result", "allowed")
        .register(meterRegistry)
    
    fun recordJWTProcessing(duration: Duration) {
        jwtProcessingTimer.record(duration)
    }
    
    fun recordEncryptionOperation() {
        encryptionCounter.increment()
    }
    
    fun recordRateLimitHit(allowed: Boolean) {
        Counter.builder("security.rate.limit.hits")
            .tag("result", if (allowed) "allowed" else "blocked")
            .register(meterRegistry)
            .increment()
    }
}
```

### 2. 실시간 성능 모니터링

```kotlin
@Component
class SecurityPerformanceMonitor {
    
    private val performanceHistory = mutableListOf<SecurityPerformanceSnapshot>()
    private val alertThresholds = SecurityAlertThresholds()
    
    @Scheduled(fixedRate = 30000) // 30초마다 실행
    fun capturePerformanceSnapshot() {
        val snapshot = SecurityPerformanceSnapshot(
            timestamp = LocalDateTime.now(),
            jwtProcessingAverage = getAverageJWTProcessingTime(),
            encryptionThroughput = getEncryptionThroughput(),
            rateLimitHitRate = getRateLimitHitRate(),
            securityFilterLatency = getSecurityFilterLatency()
        )
        
        performanceHistory.add(snapshot)
        
        // 성능 임계값 확인
        checkPerformanceAlerts(snapshot)
        
        // 히스토리 정리 (최근 24시간만 보관)
        cleanupOldHistory()
    }
    
    private fun checkPerformanceAlerts(snapshot: SecurityPerformanceSnapshot) {
        when {
            snapshot.jwtProcessingAverage > alertThresholds.jwtProcessingMax -> {
                alertService.sendAlert("JWT processing time exceeded threshold: ${snapshot.jwtProcessingAverage}ms")
            }
            
            snapshot.encryptionThroughput < alertThresholds.encryptionThroughputMin -> {
                alertService.sendAlert("Encryption throughput below threshold: ${snapshot.encryptionThroughput} ops/sec")
            }
            
            snapshot.rateLimitHitRate > alertThresholds.rateLimitHitRateMax -> {
                alertService.sendAlert("Rate limit hit rate exceeded: ${snapshot.rateLimitHitRate}%")
            }
        }
    }
}
```

### 3. 성능 대시보드 구성

```kotlin
@RestController
@RequestMapping("/api/admin/security-performance")
class SecurityPerformanceDashboardController {
    
    @GetMapping("/metrics")
    fun getCurrentMetrics(): SecurityMetricsResponse {
        return SecurityMetricsResponse(
            jwtMetrics = getJWTMetrics(),
            cryptoMetrics = getCryptoMetrics(),
            rateLimitMetrics = getRateLimitMetrics(),
            filterChainMetrics = getFilterChainMetrics()
        )
    }
    
    @GetMapping("/history")
    fun getPerformanceHistory(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime
    ): List<SecurityPerformanceSnapshot> {
        return performanceHistoryService.getHistory(from, to)
    }
    
    @GetMapping("/recommendations")
    fun getOptimizationRecommendations(): List<OptimizationRecommendation> {
        return performanceAnalyzer.generateRecommendations()
    }
}
```

---

## 최적화 전략 및 권장사항

### 1. 단계별 최적화 접근법

```kotlin
enum class OptimizationPhase {
    MEASUREMENT,    // 현재 성능 측정
    ANALYSIS,       // 병목 지점 분석
    OPTIMIZATION,   // 최적화 적용
    VALIDATION      // 성능 개선 검증
}

class SecurityOptimizationStrategy {
    
    fun executeOptimizationPlan(): OptimizationResult {
        val phases = listOf(
            measureCurrentPerformance(),
            analyzeBottlenecks(),
            applyOptimizations(),
            validateImprovements()
        )
        
        return OptimizationResult(
            phases = phases,
            overallImprovement = calculateOverallImprovement(),
            recommendations = generateFutureRecommendations()
        )
    }
    
    private fun measureCurrentPerformance(): PhaseResult {
        return PhaseResult(
            phase = OptimizationPhase.MEASUREMENT,
            metrics = SecurityPerformanceBenchmark().runComprehensiveBenchmark(),
            duration = measureTimeMillis { /* 측정 실행 */ }
        )
    }
    
    private fun analyzeBottlenecks(): PhaseResult {
        val bottlenecks = identifyPerformanceBottlenecks()
        return PhaseResult(
            phase = OptimizationPhase.ANALYSIS,
            findings = bottlenecks,
            prioritizedActions = prioritizeOptimizationActions(bottlenecks)
        )
    }
}
```

### 2. 환경별 최적화 설정

```yaml
# 개발 환경 - 성능보다 디버깅 편의성 우선
spring:
  profiles:
    active: development
    
security:
  jwt:
    expiration: 24h          # 긴 만료 시간
    algorithm: HS256         # 빠른 알고리즘
  
  encryption:
    algorithm: AES-128-GCM   # 빠른 암호화
    
  rate-limiting:
    enabled: false           # 개발 시 비활성화

---
# 스테이징 환경 - 프로덕션과 유사한 설정
spring:
  profiles:
    active: staging
    
security:
  jwt:
    expiration: 8h
    algorithm: HS256
    
  encryption:
    algorithm: AES-256-GCM
    
  rate-limiting:
    enabled: true
    default-limit: 1000      # 관대한 제한

---
# 프로덕션 환경 - 보안과 성능의 균형
spring:
  profiles:
    active: production
    
security:
  jwt:
    expiration: 1h           # 짧은 만료 시간
    algorithm: RS256         # 더 안전한 알고리즘
    
  encryption:
    algorithm: AES-256-GCM   # 강력한 암호화
    hardware-acceleration: true
    
  rate-limiting:
    enabled: true
    default-limit: 100       # 엄격한 제한
    adaptive: true           # 적응형 제한
    
  monitoring:
    enabled: true
    alert-thresholds:
      jwt-processing-max: 50ms
      encryption-throughput-min: 1000
```

### 3. 성능 회귀 방지

```kotlin
@Component
class SecurityPerformanceRegressionDetector {
    
    private val baselineMetrics = loadBaselineMetrics()
    private val regressionThreshold = 0.20 // 20% 성능 저하시 알림
    
    @EventListener
    fun onDeployment(event: DeploymentEvent) {
        // 배포 후 성능 회귀 검사
        CompletableFuture.runAsync {
            Thread.sleep(60000) // 1분 대기 (워밍업)
            checkForPerformanceRegression()
        }
    }
    
    private fun checkForPerformanceRegression() {
        val currentMetrics = SecurityPerformanceBenchmark().runQuickBenchmark()
        val regressions = mutableListOf<PerformanceRegression>()
        
        // JWT 성능 비교
        val jwtRegression = compareMetrics(
            baseline = baselineMetrics.jwtThroughput,
            current = currentMetrics.jwtThroughput,
            metricName = "JWT Throughput"
        )
        if (jwtRegression != null) regressions.add(jwtRegression)
        
        // 암호화 성능 비교
        val cryptoRegression = compareMetrics(
            baseline = baselineMetrics.encryptionThroughput,
            current = currentMetrics.encryptionThroughput,
            metricName = "Encryption Throughput"
        )
        if (cryptoRegression != null) regressions.add(cryptoRegression)
        
        if (regressions.isNotEmpty()) {
            alertService.sendRegressionAlert(regressions)
        }
    }
    
    private fun compareMetrics(baseline: Double, current: Double, metricName: String): PerformanceRegression? {
        val degradation = (baseline - current) / baseline
        
        return if (degradation > regressionThreshold) {
            PerformanceRegression(
                metricName = metricName,
                baselineValue = baseline,
                currentValue = current,
                degradationPercentage = degradation * 100
            )
        } else null
    }
}
```

---

## 실제 구현 예제

### 1. 통합 보안 성능 서비스

```kotlin
@Service
class IntegratedSecurityPerformanceService(
    private val jwtService: JWTService,
    private val encryptionService: EncryptionService,
    private val rateLimiter: RateLimiter,
    private val metricsService: SecurityMetricsService
) {
    
    // 사용자 인증 (성능 최적화 적용)
    @Timed(name = "security.authentication.time")
    suspend fun authenticateUser(credentials: UserCredentials): AuthenticationResult {
        return withContext(Dispatchers.IO) {
            // 1. Rate Limiting 확인 (빠른 실패)
            if (!rateLimiter.isAllowed(credentials.clientId, 10, 60000)) {
                metricsService.recordRateLimitHit(false)
                throw RateLimitExceededException()
            }
            
            // 2. 사용자 존재 확인 (캐시 활용)
            val user = userService.findByUsername(credentials.username)
                ?: throw UserNotFoundException()
            
            // 3. 패스워드 검증 (비동기 처리)
            val isValidPassword = async {
                passwordEncoder.matches(credentials.password, user.passwordHash)
            }
            
            // 4. JWT 토큰 생성 (병렬 처리)
            val tokenGeneration = async {
                jwtService.generateToken(user)
            }
            
            // 5. 결과 조합
            if (isValidPassword.await()) {
                val token = tokenGeneration.await()
                metricsService.recordSuccessfulAuthentication()
                
                AuthenticationResult.Success(
                    token = token,
                    user = user.toDTO(),
                    expiresAt = calculateExpirationTime()
                )
            } else {
                metricsService.recordFailedAuthentication()
                throw InvalidCredentialsException()
            }
        }
    }
    
    // 민감 데이터 암호화 (성능 최적화)
    @Timed(name = "security.encryption.time")
    suspend fun encryptSensitiveData(data: String, context: EncryptionContext): EncryptedData {
        return withContext(Dispatchers.Default) {
            // 데이터 크기에 따른 알고리즘 선택
            val algorithm = selectOptimalAlgorithm(data.length)
            
            // 하드웨어 가속 활용
            val encryptedData = if (isHardwareAccelerationAvailable()) {
                encryptionService.encryptWithHardwareAcceleration(data, algorithm)
            } else {
                encryptionService.encryptWithSoftware(data, algorithm)
            }
            
            metricsService.recordEncryptionOperation(algorithm, data.length)
            encryptedData
        }
    }
}
```

### 2. 성능 기반 보안 설정 자동 조정

```kotlin
@Component
class AdaptiveSecurityConfiguration {
    
    private val performanceMonitor = SecurityPerformanceMonitor()
    private val configurationManager = SecurityConfigurationManager()
    
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun adjustSecuritySettings() {
        val currentPerformance = performanceMonitor.getCurrentMetrics()
        val recommendations = generateAdaptiveRecommendations(currentPerformance)
        
        recommendations.forEach { recommendation ->
            when (recommendation.type) {
                ConfigurationType.JWT_EXPIRATION -> {
                    adjustJWTExpiration(recommendation)
                }
                
                ConfigurationType.ENCRYPTION_ALGORITHM -> {
                    adjustEncryptionAlgorithm(recommendation)
                }
                
                ConfigurationType.RATE_LIMIT -> {
                    adjustRateLimit(recommendation)
                }
            }
        }
    }
    
    private fun adjustJWTExpiration(recommendation: ConfigurationRecommendation) {
        val currentExpiration = configurationManager.getJWTExpiration()
        val newExpiration = when {
            recommendation.severity == RecommendationSeverity.HIGH -> {
                // 성능이 심각하게 나쁨 -> 토큰 수명 증가
                minOf(currentExpiration * 2, Duration.ofHours(24))
            }
            
            recommendation.severity == RecommendationSeverity.LOW -> {
                // 성능이 좋음 -> 보안 강화 (토큰 수명 감소)
                maxOf(currentExpiration / 2, Duration.ofMinutes(15))
            }
            
            else -> currentExpiration
        }
        
        if (newExpiration != currentExpiration) {
            configurationManager.setJWTExpiration(newExpiration)
            logger.info("JWT expiration adjusted from $currentExpiration to $newExpiration")
        }
    }
}
```

---

## 트러블슈팅 가이드

### 1. 일반적인 성능 문제와 해결책

| 문제 | 증상 | 원인 | 해결책 |
|------|------|------|--------|
| JWT 토큰 생성 느림 | >100ms 소요 | 복잡한 클레임, 큰 페이로드 | 클레임 최소화, 압축 사용 |
| 암호화 병목 | CPU 100% 사용 | 소프트웨어 암호화만 사용 | 하드웨어 가속 활용 |
| HTTPS 오버헤드 큼 | >50% 성능 저하 | TLS 1.2, 비효율적 암호화 | TLS 1.3, 최적화된 cipher suite |
| Rate Limiting 오버헤드 | 높은 메모리 사용 | 메모리 기반 구현, 정리 안됨 | Redis 사용, 주기적 정리 |
| 필터 체인 지연 | 높은 응답 시간 | 순차 처리, 불필요한 필터 | 병렬 처리, 조건부 적용 |

### 2. 성능 분석 도구 활용

```kotlin
// JProfiler, YourKit 등 프로파일러 연동
@Component
class SecurityPerformanceProfiler {
    
    @Profile("profiling")
    fun enableDetailedProfiling() {
        // JFR (Java Flight Recorder) 설정
        val recording = FlightRecorder.newRecording()
        recording.setDuration(Duration.ofMinutes(5))
        recording.setName("SecurityPerformanceAnalysis")
        
        // 보안 관련 이벤트 수집
        recording.enable("security.jwt.generation")
        recording.enable("security.encryption.operation")
        recording.enable("security.rate.limit.check")
        
        recording.start()
    }
    
    // 메모리 누수 감지
    @Scheduled(fixedRate = 60000)
    fun detectMemoryLeaks() {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val memoryUsagePercent = (usedMemory.toDouble() / totalMemory) * 100
        
        if (memoryUsagePercent > 85) {
            logger.warn("High memory usage detected: ${String.format("%.2f", memoryUsagePercent)}%")
            
            // 메모리 덤프 생성
            generateMemoryDump()
            
            // 가비지 컬렉션 수동 실행
            System.gc()
        }
    }
}
```

### 3. 디버깅 및 로깅 설정

```yaml
logging:
  level:
    com.example.reservation.security: DEBUG
    org.springframework.security: INFO
    io.jsonwebtoken: DEBUG
    
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [%logger{36}] - %msg [execution_time:%X{execution_time}ms] [memory_usage:%X{memory_usage}MB]%n"
    
  appender:
    security-performance:
      file: logs/security-performance.log
      max-file-size: 100MB
      max-history: 30
```

```kotlin
// 성능 디버깅을 위한 로깅 설정
@Aspect
@Component
class SecurityPerformanceLoggingAspect {
    
    private val logger = LoggerFactory.getLogger(SecurityPerformanceLoggingAspect::class.java)
    
    @Around("@annotation(Timed)")
    fun logPerformance(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        val startMemory = getUsedMemory()
        
        return try {
            val result = joinPoint.proceed()
            
            val executionTime = System.currentTimeMillis() - startTime
            val memoryUsed = getUsedMemory() - startMemory
            
            // MDC에 성능 정보 추가
            MDC.put("execution_time", executionTime.toString())
            MDC.put("memory_usage", (memoryUsed / 1024 / 1024).toString())
            
            logger.debug("Security operation completed: ${joinPoint.signature.name}")
            
            result
        } catch (e: Exception) {
            logger.error("Security operation failed: ${joinPoint.signature.name}", e)
            throw e
        } finally {
            MDC.clear()
        }
    }
    
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
```

---

## 결론

보안 성능 최적화는 지속적인 과정입니다. 본 가이드에서 제시한 방법론과 도구들을 활용하여:

1. **정기적인 성능 측정**: 보안 기능의 성능 영향을 지속적으로 모니터링
2. **병목 지점 식별**: 성능 저하의 주요 원인을 정확히 파악
3. **단계적 최적화**: 영향도가 큰 부분부터 우선 순위를 두어 최적화
4. **성능 회귀 방지**: 새로운 배포 시 성능 저하가 없는지 확인
5. **보안과 성능의 균형**: 비즈니스 요구사항에 맞는 적절한 보안 수준 유지

이를 통해 안전하면서도 성능이 우수한 애플리케이션을 구축할 수 있습니다.

### 추가 리소스

- [OWASP Performance Testing Guide](https://owasp.org/www-project-web-security-testing-guide/)
- [Spring Security Performance Tuning](https://docs.spring.io/spring-security/reference/performance.html)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)
- [TLS Performance Optimization](https://hpbn.co/transport-layer-security-tls/)

---

*본 가이드는 실제 성능 테스트 결과를 바탕으로 작성되었으며, 환경에 따라 결과가 달라질 수 있습니다. 프로덕션 환경에 적용하기 전에 충분한 테스트를 수행하시기 바랍니다.*