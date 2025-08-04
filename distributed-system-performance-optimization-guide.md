# 분산 시스템 성능 최적화 완전 가이드

## 📋 목차

1. [개요](#개요)
2. [로드 밸런싱 최적화](#로드-밸런싱-최적화)
3. [데이터베이스 샤딩 전략](#데이터베이스-샤딩-전략)
4. [분산 캐시 관리](#분산-캐시-관리)
5. [마이크로서비스 통신 최적화](#마이크로서비스-통신-최적화)
6. [시스템 복원력 강화](#시스템-복원력-강화)
7. [성능 모니터링](#성능-모니터링)
8. [실무 적용 사례](#실무-적용-사례)
9. [문제 해결 가이드](#문제-해결-가이드)
10. [결론 및 권장사항](#결론-및-권장사항)

---

## 개요

### 분산 시스템 성능 최적화의 중요성

분산 시스템은 현대 소프트웨어 아키텍처의 핵심입니다. 하지만 분산 환경에서는 단일 시스템과는 다른 성능 이슈들이 발생합니다:

- **네트워크 지연시간**: 서비스 간 통신으로 인한 latency 증가
- **데이터 일관성**: CAP 정리에 따른 일관성과 가용성의 트레이드오프
- **장애 전파**: 하나의 서비스 장애가 전체 시스템에 미치는 영향
- **복잡한 상태 관리**: 분산 상태의 동기화와 관리

### 성능 최적화 원칙

1. **측정 우선**: 최적화 전 정확한 성능 측정이 필수
2. **병목점 식별**: 전체 시스템에서 가장 느린 부분 찾기
3. **단계적 개선**: 한 번에 하나씩 최적화하여 효과 확인
4. **트레이드오프 고려**: 성능 vs 복잡성, 일관성 vs 가용성 등

---

## 로드 밸런싱 최적화

### 로드 밸런싱 전략 비교

#### 1. Round Robin
```kotlin
class RoundRobinLoadBalancer(nodes: List<ServerNode>) : LoadBalancer(nodes) {
    private var currentIndex = 0
    
    override suspend fun routeRequest(request: TestRequest): RoutingResult {
        val healthyNodes = nodes.filter { it.isHealthy }
        if (healthyNodes.isEmpty()) return RoutingResult("", false)
        
        val selectedNode = healthyNodes[currentIndex % healthyNodes.size]
        currentIndex = (currentIndex + 1) % healthyNodes.size
        
        return RoutingResult(selectedNode.id, true, selectedNode.averageResponseTimeMs.toLong())
    }
}
```

**장점:**
- 구현이 간단하고 이해하기 쉬움
- 모든 서버에 균등하게 요청 분산
- 메모리 사용량이 적음

**단점:**
- 서버별 처리 능력 차이를 고려하지 않음
- 세션 지속성 불가능
- 서버 상태 변화에 둔감

**적용 시나리오:**
- 동일한 성능의 서버들로 구성된 환경
- 상태 비저장(Stateless) 애플리케이션
- 단순한 웹 서비스나 API 게이트웨이

#### 2. Weighted Round Robin
```kotlin
class WeightedRoundRobinLoadBalancer(nodes: List<ServerNode>) : LoadBalancer(nodes) {
    private val weightedNodes = mutableListOf<ServerNode>()
    
    init {
        nodes.forEach { node ->
            repeat(node.weight.toInt()) {
                weightedNodes.add(node)
            }
        }
        weightedNodes.shuffle()
    }
}
```

**최적화 전략:**
- 서버 성능에 따른 가중치 동적 조정
- CPU 사용률, 메모리 사용량 기반 가중치 계산
- 주기적인 헬스 체크를 통한 가중치 업데이트

#### 3. Least Connections
```kotlin
class LeastConnectionsLoadBalancer(nodes: List<ServerNode>) : LoadBalancer(nodes) {
    override suspend fun routeRequest(request: TestRequest): RoutingResult {
        val healthyNodes = nodes.filter { it.isHealthy }
        val selectedNode = healthyNodes.minByOrNull { it.activeConnections }
            ?: return RoutingResult("", false)
        
        selectedNode.activeConnections++
        return RoutingResult(selectedNode.id, true)
    }
}
```

**성능 최적화 포인트:**
- 연결 수 추적의 정확성 보장
- 연결 해제 시 카운터 감소 처리
- 메모리 기반 연결 풀 관리

### 로드 밸런싱 성능 튜닝

#### 헬스 체크 최적화
```yaml
# application.yml
spring:
  cloud:
    loadbalancer:
      health-check:
        interval: 30s
        timeout: 5s
        path:
          default: /actuator/health
```

#### 연결 풀 설정
```kotlin
@Configuration
class LoadBalancerConfig {
    
    @Bean
    fun httpClientConnectionManager(): PoolingHttpClientConnectionManager {
        return PoolingHttpClientConnectionManager().apply {
            maxTotal = 200
            defaultMaxPerRoute = 50
            setValidateAfterInactivity(30000)
        }
    }
}
```

### 성능 메트릭 및 모니터링

#### 핵심 메트릭
1. **처리량**: 초당 요청 수 (RPS)
2. **응답 시간**: 평균, P95, P99 latency
3. **에러율**: 4xx, 5xx 응답 비율
4. **서버 활용도**: CPU, 메모리, 연결 수

#### 알림 설정
```kotlin
@Component
class LoadBalancerMonitor {
    
    @EventListener
    fun handleServerDown(event: ServerDownEvent) {
        if (healthyServerRatio < 0.5) {
            alertingService.sendCriticalAlert(
                "Load balancer: Less than 50% servers healthy"
            )
        }
    }
}
```

---

## 데이터베이스 샤딩 전략

### 샤딩 전략 선택

#### 1. Range-based Sharding
```kotlin
class RangeBasedShardingRouter(private val shards: List<DatabaseShard>) : ShardingRouter {
    
    override fun routeQuery(query: DatabaseQuery): DatabaseShard {
        val shardKey = extractShardKey(query.shardKey)
        val shardIndex = when {
            shardKey <= 1000 -> 0
            shardKey <= 2000 -> 1
            shardKey <= 3000 -> 2
            else -> 3
        }
        return shards[shardIndex]
    }
}
```

**장점:**
- 범위 쿼리에 최적화
- 데이터 지역성 보장
- 구현이 비교적 간단

**단점:**
- 핫스팟 문제 발생 가능
- 데이터 분포 불균형
- 리샤딩이 복잡

#### 2. Hash-based Sharding
```kotlin
class HashBasedShardingRouter(private val shards: List<DatabaseShard>) : ShardingRouter {
    
    override fun routeQuery(query: DatabaseQuery): DatabaseShard {
        val hash = query.shardKey.hashCode()
        val shardIndex = Math.abs(hash) % shards.size
        return shards[shardIndex]
    }
}
```

**최적화 전략:**
- 일관된 해싱 알고리즘 사용
- 가상 노드를 통한 부하 분산
- 해시 함수의 균등 분포 보장

#### 3. Consistent Hashing
```kotlin
class ConsistentHashShardingRouter(shards: List<DatabaseShard>) : ShardingRouter {
    private val ring = TreeMap<Int, DatabaseShard>()
    private val virtualNodes = 150
    
    init {
        shards.forEach { shard ->
            repeat(virtualNodes) { vNode ->
                val hash = "${shard.id}-$vNode".hashCode()
                ring[hash] = shard
            }
        }
    }
    
    override fun routeQuery(query: DatabaseQuery): DatabaseShard {
        val hash = query.shardKey.hashCode()
        val entry = ring.ceilingEntry(hash) ?: ring.firstEntry()
        return entry.value
    }
}
```

### 샤딩 성능 최적화

#### 쿼리 최적화
```sql
-- Bad: 전체 샤드 스캔
SELECT * FROM users WHERE age > 25;

-- Good: 샤드 키 활용
SELECT * FROM users WHERE user_id = 12345 AND age > 25;
```

#### 배치 처리 최적화
```kotlin
class ShardedBatchProcessor {
    
    suspend fun processBatch(operations: List<DatabaseOperation>) {
        // 샤드별로 그룹화
        val operationsByShards = operations.groupBy { op ->
            shardingRouter.routeQuery(op.query)
        }
        
        // 병렬 처리
        operationsByShards.map { (shard, ops) ->
            async {
                shard.executeBatch(ops)
            }
        }.awaitAll()
    }
}
```

#### 크로스 샤드 쿼리 최적화
```kotlin
class CrossShardQueryOptimizer {
    
    suspend fun executeDistributedQuery(query: DistributedQuery): QueryResult {
        val subQueries = query.splitByShards()
        
        val results = subQueries.map { subQuery ->
            async {
                val shard = shardingRouter.routeQuery(subQuery)
                shard.execute(subQuery)
            }
        }.awaitAll()
        
        return mergeResults(results, query.aggregationType)
    }
}
```

### 샤딩 모니터링

#### 핵심 메트릭
1. **샤드 분포**: 각 샤드의 데이터 크기와 요청 분포
2. **쿼리 성능**: 샤드별 쿼리 응답 시간
3. **크로스 샤드 쿼리**: 여러 샤드에 걸친 쿼리 빈도
4. **리밸런싱**: 데이터 재분배 진행상황

#### 자동 알림
```kotlin
@Component
class ShardingMonitor {
    
    @Scheduled(fixedRate = 60000)
    fun checkShardBalance() {
        val imbalanceRatio = calculateImbalanceRatio()
        
        if (imbalanceRatio > 0.3) {
            alertingService.sendWarning(
                "Shard imbalance detected: ${imbalanceRatio * 100}%"
            )
        }
    }
}
```

---

## 분산 캐시 관리

### 캐시 전략 및 패턴

#### 1. Cache-Aside Pattern
```kotlin
class CacheAsideService(
    private val cache: RedisTemplate<String, Any>,
    private val database: UserRepository
) {
    
    suspend fun getUser(userId: String): User? {
        // 1. 캐시에서 먼저 조회
        val cached = cache.opsForValue().get("user:$userId")
        if (cached != null) {
            return cached as User
        }
        
        // 2. 캐시 미스 시 데이터베이스에서 조회
        val user = database.findById(userId)
        
        // 3. 조회 결과를 캐시에 저장
        if (user != null) {
            cache.opsForValue().set("user:$userId", user, Duration.ofHours(1))
        }
        
        return user
    }
}
```

#### 2. Write-Through Pattern
```kotlin
class WriteThroughCacheService {
    
    suspend fun updateUser(user: User) {
        // 1. 데이터베이스 업데이트
        database.save(user)
        
        // 2. 캐시 업데이트
        cache.opsForValue().set("user:${user.id}", user, Duration.ofHours(1))
    }
}
```

#### 3. Write-Behind Pattern
```kotlin
class WriteBehindCacheService {
    private val writeQueue = Channel<WriteOperation>(capacity = 1000)
    
    init {
        // 백그라운드 쓰기 프로세서
        GlobalScope.launch {
            for (operation in writeQueue) {
                try {
                    database.execute(operation)
                } catch (e: Exception) {
                    // 실패 시 재시도 로직
                    retryPolicy.execute { database.execute(operation) }
                }
            }
        }
    }
    
    suspend fun updateUser(user: User) {
        // 1. 캐시 즉시 업데이트
        cache.opsForValue().set("user:${user.id}", user)
        
        // 2. 데이터베이스 쓰기는 비동기로 큐에 추가
        writeQueue.send(WriteOperation.Update(user))
    }
}
```

### 분산 캐시 최적화

#### 캐시 클러스터링
```yaml
# Redis Cluster 설정
spring:
  redis:
    cluster:
      nodes:
        - redis-node-1:7000
        - redis-node-2:7001
        - redis-node-3:7002
      max-redirects: 3
    lettuce:
      pool:
        max-active: 20
        max-wait: -1ms
        max-idle: 8
        min-idle: 0
```

#### 캐시 워밍업 전략
```kotlin
@Component
class CacheWarmupService {
    
    @EventListener(ApplicationReadyEvent::class)
    suspend fun warmupCache() {
        val popularUsers = userService.findMostActiveUsers(1000)
        
        popularUsers.chunked(50).forEach { batch ->
            async {
                batch.forEach { user ->
                    cache.opsForValue().set("user:${user.id}", user, Duration.ofHours(2))
                }
            }
        }
        
        logger.info("Cache warmup completed for ${popularUsers.size} users")
    }
}
```

#### 캐시 무효화 전략
```kotlin
class CacheInvalidationService {
    
    suspend fun invalidateUserCache(userId: String) {
        // 직접 무효화
        cache.delete("user:$userId")
        
        // 관련 캐시도 무효화
        cache.delete("user:$userId:profile")
        cache.delete("user:$userId:permissions")
        
        // 패턴 기반 무효화
        val pattern = "user:$userId:*"
        val keys = cache.keys(pattern)
        if (keys.isNotEmpty()) {
            cache.delete(keys)
        }
    }
    
    // 태그 기반 무효화
    suspend fun invalidateByTag(tag: String) {
        val taggedKeys = cache.opsForSet().members("tag:$tag")
        if (taggedKeys?.isNotEmpty() == true) {
            cache.delete(taggedKeys)
            cache.delete("tag:$tag")
        }
    }
}
```

### 캐시 성능 모니터링

#### 핵심 메트릭
```kotlin
@Component
class CacheMetricsCollector {
    
    @EventListener
    fun recordCacheHit(event: CacheHitEvent) {
        meterRegistry.counter("cache.hit", "cache", event.cacheName).increment()
    }
    
    @EventListener  
    fun recordCacheMiss(event: CacheMissEvent) {
        meterRegistry.counter("cache.miss", "cache", event.cacheName).increment()
    }
    
    @Scheduled(fixedRate = 30000)
    fun collectCacheStats() {
        val hitRatio = calculateHitRatio()
        val memoryUsage = getMemoryUsage()
        val evictionRate = getEvictionRate()
        
        meterRegistry.gauge("cache.hit.ratio", hitRatio)
        meterRegistry.gauge("cache.memory.usage", memoryUsage)
        meterRegistry.gauge("cache.eviction.rate", evictionRate)
    }
}
```

#### 자동 조정
```kotlin
@Component
class CacheAutoTuning {
    
    @Scheduled(fixedRate = 300000) // 5분마다
    fun adjustCacheSettings() {
        val hitRatio = getCurrentHitRatio()
        val memoryPressure = getMemoryPressure()
        
        when {
            hitRatio < 0.8 && memoryPressure < 0.7 -> {
                increaseCacheSize()
            }
            hitRatio > 0.95 && memoryPressure > 0.8 -> {
                optimizeEvictionPolicy()
            }
            else -> {
                // 현재 설정 유지
            }
        }
    }
}
```

---

## 마이크로서비스 통신 최적화

### 통신 패턴 최적화

#### 1. HTTP/REST 최적화
```kotlin
@Configuration
class HttpClientConfig {
    
    @Bean
    fun webClient(): WebClient {
        val connectionProvider = ConnectionProvider.builder("custom")
            .maxConnections(200)
            .maxIdleTime(Duration.ofSeconds(20))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(60))
            .evictInBackground(Duration.ofSeconds(120))
            .build()
            
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(
                HttpClient.create(connectionProvider)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .responseTimeout(Duration.ofSeconds(10))
                    .doOnConnected { conn ->
                        conn.addHandlerLast(ReadTimeoutHandler(10))
                        conn.addHandlerLast(WriteTimeoutHandler(10))
                    }
            ))
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024) // 1MB
            }
            .build()
    }
}
```

#### 2. gRPC 최적화
```kotlin
@Configuration
class GrpcConfig {
    
    @Bean
    fun grpcChannel(): ManagedChannel {
        return NettyChannelBuilder.forAddress("user-service", 9090)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(1024 * 1024) // 1MB
            .usePlaintext()
            .build()
    }
}
```

#### 3. 비동기 메시징 최적화
```kotlin
@Component
class OptimizedMessageProducer {
    
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    
    suspend fun sendMessage(topic: String, message: Any) {
        // 배치 전송으로 처리량 향상
        kafkaTemplate.send(topic, message).also { future ->
            future.addCallback(
                { result -> 
                    logger.debug("Message sent successfully: ${result?.recordMetadata}")
                },
                { failure ->
                    logger.error("Failed to send message", failure)
                    // 재시도 로직 또는 DLQ 전송
                }
            )
        }
    }
}
```

### 서비스 디스커버리 최적화

#### Consul 기반 서비스 디스커버리
```yaml
# application.yml
spring:
  cloud:
    consul:
      discovery:
        health-check-interval: 30s
        health-check-timeout: 10s
        health-check-critical-timeout: 3m
        hostname: ${HOST_NAME:localhost}
        prefer-ip-address: true
        tags:
          - version=1.0
          - environment=production
```

#### 서비스 메시 (Istio) 활용
```yaml
# service-mesh-config.yaml
apiVersion: networking.istio.io/v1alpha3
kind: DestinationRule
metadata:
  name: user-service
spec:
  host: user-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        maxRequestsPerConnection: 10
    circuitBreaker:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
```

### 통신 성능 모니터링

#### 분산 추적
```kotlin
@Component
class TracingService {
    
    @NewSpan("user-service-call")
    suspend fun callUserService(@SpanTag("userId") userId: String): User {
        val span = tracer.nextSpan()
            .name("user-service-call")
            .tag("user.id", userId)
            .start()
            
        return try {
            userServiceClient.getUser(userId)
        } catch (e: Exception) {
            span.tag("error", e.message ?: "Unknown error")
            throw e
        } finally {
            span.end()
        }
    }
}
```

#### 메트릭 수집
```kotlin
@Component
class ServiceCommunicationMetrics {
    
    @EventListener
    fun recordServiceCall(event: ServiceCallEvent) {
        Timer.Sample.start(meterRegistry)
            .stop(Timer.builder("service.call.duration")
                .tag("service", event.serviceName)
                .tag("method", event.method)
                .tag("status", event.status.toString())
                .register(meterRegistry))
                
        meterRegistry.counter("service.call.total",
            "service", event.serviceName,
            "method", event.method,
            "status", event.status.toString()
        ).increment()
    }
}
```

---

## 시스템 복원력 강화

### Circuit Breaker 패턴

#### Circuit Breaker 구현
```kotlin
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeout: Duration = Duration.ofSeconds(60),
    private val successThreshold: Int = 3
) {
    private val state = AtomicReference(CircuitBreakerState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    
    suspend fun <T> execute(operation: suspend () -> T): T {
        when (state.get()) {
            CircuitBreakerState.OPEN -> {
                if (shouldAttemptReset()) {
                    state.set(CircuitBreakerState.HALF_OPEN)
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                return executeInHalfOpenState(operation)
            }
            CircuitBreakerState.CLOSED -> {
                return executeInClosedState(operation)
            }
        }
        
        return executeInHalfOpenState(operation)
    }
    
    private suspend fun <T> executeInClosedState(operation: suspend () -> T): T {
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    private suspend fun <T> executeInHalfOpenState(operation: suspend () -> T): T {
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    private fun onSuccess() {
        failureCount.set(0)
        if (state.get() == CircuitBreakerState.HALF_OPEN) {
            val count = successCount.incrementAndGet()
            if (count >= successThreshold) {
                state.set(CircuitBreakerState.CLOSED)
                successCount.set(0)
            }
        }
    }
    
    private fun onFailure() {
        lastFailureTime.set(System.currentTimeMillis())
        val count = failureCount.incrementAndGet()
        
        if (count >= failureThreshold) {
            state.set(CircuitBreakerState.OPEN)
        }
    }
}
```

### Bulkhead 패턴

#### 리소스 격리
```kotlin
@Configuration
class BulkheadConfig {
    
    @Bean("userServiceExecutor")
    fun userServiceExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 5
            maxPoolSize = 10
            queueCapacity = 100
            setThreadNamePrefix("user-service-")
            initialize()
        }
    }
    
    @Bean("orderServiceExecutor")
    fun orderServiceExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 3
            maxPoolSize = 8
            queueCapacity = 50
            setThreadNamePrefix("order-service-")
            initialize()
        }
    }
}
```

#### 세마포어 기반 격리
```kotlin
class SemaphoreBulkhead(private val permits: Int) {
    private val semaphore = Semaphore(permits)
    
    suspend fun <T> execute(operation: suspend () -> T): T {
        semaphore.acquire()
        return try {
            operation()
        } finally {
            semaphore.release()
        }
    }
}
```

### Retry 패턴

#### 지수 백오프 재시도
```kotlin
class ExponentialBackoffRetry(
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1000,
    private val maxDelayMs: Long = 10000
) {
    
    suspend fun <T> execute(operation: suspend () -> T): T {
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxAttempts - 1) {
                    val delay = calculateDelay(attempt)
                    delay(delay)
                }
            }
        }
        
        throw lastException!!
    }
    
    private fun calculateDelay(attempt: Int): Long {
        val delay = baseDelayMs * (2.0.pow(attempt)).toLong()
        return minOf(delay, maxDelayMs) + Random.nextLong(100) // jitter 추가
    }
}
```

### 타임아웃 패턴

#### 타임아웃 설정 및 관리
```kotlin
class TimeoutManager {
    
    suspend fun <T> executeWithTimeout(
        timeoutMs: Long,
        operation: suspend () -> T
    ): T {
        return withTimeout(timeoutMs) {
            operation()
        }
    }
    
    // 적응적 타임아웃
    suspend fun <T> executeWithAdaptiveTimeout(
        operation: suspend () -> T,
        metricsCollector: MetricsCollector
    ): T {
        val recentP95 = metricsCollector.getP95ResponseTime()
        val adaptiveTimeout = (recentP95 * 2).coerceAtLeast(1000).coerceAtMost(30000)
        
        return executeWithTimeout(adaptiveTimeout, operation)
    }
}
```

---

## 성능 모니터링

### 핵심 메트릭 정의

#### 1. 시스템 메트릭
```kotlin
@Component
class SystemMetricsCollector {
    
    @Scheduled(fixedRate = 30000)
    fun collectSystemMetrics() {
        // CPU 사용률
        val cpuUsage = systemInfo.hardware.processor.systemCpuLoad * 100
        meterRegistry.gauge("system.cpu.usage", cpuUsage)
        
        // 메모리 사용률
        val memory = systemInfo.hardware.memory
        val memoryUsage = (memory.total - memory.available).toDouble() / memory.total * 100
        meterRegistry.gauge("system.memory.usage", memoryUsage)
        
        // 디스크 I/O
        val diskIO = getDiskIOMetrics()
        meterRegistry.gauge("system.disk.read.bytes", diskIO.readBytes)
        meterRegistry.gauge("system.disk.write.bytes", diskIO.writeBytes)
        
        // 네트워크 I/O
        val networkIO = getNetworkIOMetrics()
        meterRegistry.gauge("system.network.in.bytes", networkIO.inBytes)
        meterRegistry.gauge("system.network.out.bytes", networkIO.outBytes)
    }
}
```

#### 2. 애플리케이션 메트릭
```kotlin
@Component
class ApplicationMetricsCollector {
    
    @EventListener
    fun recordRequestMetrics(event: RequestCompletedEvent) {
        Timer.Sample.start(meterRegistry)
            .stop(
                Timer.builder("http.request.duration")
                    .tag("method", event.method)
                    .tag("uri", event.uri)
                    .tag("status", event.status.toString())
                    .register(meterRegistry)
            )
    }
    
    @EventListener
    fun recordDatabaseMetrics(event: DatabaseQueryEvent) {
        Timer.Sample.start(meterRegistry)
            .stop(
                Timer.builder("database.query.duration")
                    .tag("operation", event.operation)
                    .tag("table", event.table)
                    .register(meterRegistry)
            )
    }
}
```

#### 3. 비즈니스 메트릭
```kotlin
@Component
class BusinessMetricsCollector {
    
    @EventListener
    fun recordReservationMetrics(event: ReservationEvent) {
        when (event.type) {
            ReservationEventType.CREATED -> {
                meterRegistry.counter("business.reservation.created",
                    "room_type", event.roomType,
                    "user_tier", event.userTier
                ).increment()
            }
            ReservationEventType.CANCELLED -> {
                meterRegistry.counter("business.reservation.cancelled",
                    "reason", event.cancellationReason
                ).increment()
            }
        }
    }
}
```

### 알림 시스템

#### 임계값 기반 알림
```kotlin
@Component
class AlertingService {
    
    @EventListener
    fun handleMetricThresholdExceeded(event: MetricThresholdEvent) {
        val alert = Alert(
            severity = event.severity,
            message = event.message,
            metrics = event.metrics,
            timestamp = Instant.now()
        )
        
        when (event.severity) {
            Severity.CRITICAL -> {
                sendSlackAlert(alert)
                sendPagerDutyAlert(alert)
                sendEmailAlert(alert)
            }
            Severity.WARNING -> {
                sendSlackAlert(alert)
                sendEmailAlert(alert)
            }
            Severity.INFO -> {
                sendSlackAlert(alert)
            }
        }
    }
    
    private suspend fun sendSlackAlert(alert: Alert) {
        val slackMessage = SlackMessage(
            channel = "#alerts",
            text = alert.message,
            color = when (alert.severity) {
                Severity.CRITICAL -> "danger"
                Severity.WARNING -> "warning"
                else -> "good"
            }
        )
        
        slackClient.sendMessage(slackMessage)
    }
}
```

#### 자동 복구 액션
```kotlin
@Component
class AutoRecoveryService {
    
    @EventListener
    fun handleHighErrorRate(event: HighErrorRateEvent) {
        when {
            event.errorRate > 0.5 -> {
                // 심각한 상황: 트래픽 차단
                circuitBreakerManager.openCircuitBreaker(event.serviceName)
                scalingService.scaleUp(event.serviceName, factor = 2.0)
            }
            event.errorRate > 0.2 -> {
                // 경고 상황: 스케일 업
                scalingService.scaleUp(event.serviceName, factor = 1.5)
            }
            event.errorRate > 0.1 -> {
                // 주의 상황: 모니터링 강화
                monitoringService.increaseMonitoringFrequency(event.serviceName)
            }
        }
    }
    
    @EventListener
    fun handleHighMemoryUsage(event: HighMemoryUsageEvent) {
        if (event.memoryUsage > 0.9) {
            // 메모리 부족: 가비지 컬렉션 강제 실행
            System.gc()
            
            // 캐시 정리
            cacheManager.evictAll()
            
            // 추가 메모리 확보가 필요한 경우 재시작
            if (getCurrentMemoryUsage() > 0.85) {
                applicationRestartService.scheduleRestart()
            }
        }
    }
}
```

### 대시보드 구성

#### Grafana 대시보드 설정
```json
{
  "dashboard": {
    "title": "분산 시스템 성능 대시보드",
    "panels": [
      {
        "title": "처리량 (RPS)",
        "targets": [
          {
            "expr": "rate(http_requests_total[5m])",
            "legendFormat": "{{method}} {{uri}}"
          }
        ]
      },
      {
        "title": "응답 시간",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "P95"
          },
          {
            "expr": "histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "P99"
          }
        ]
      },
      {
        "title": "에러율",
        "targets": [
          {
            "expr": "rate(http_requests_total{status=~\"4..|5..\"}[5m]) / rate(http_requests_total[5m]) * 100",
            "legendFormat": "Error Rate %"
          }
        ]
      }
    ]
  }
}
```

---

## 실무 적용 사례

### 사례 1: 전자상거래 플랫폼

#### 문제 상황
- 트래픽 급증 시 응답 시간 증가 (5초 → 30초)
- 데이터베이스 과부하로 인한 서비스 중단
- 결제 서비스 장애가 전체 시스템에 미치는 영향

#### 해결 방안
```kotlin
// 1. 캐시 레이어 도입
@Service
class ProductCacheService {
    
    @Cacheable(value = ["products"], key = "#productId")
    suspend fun getProduct(productId: String): Product? {
        return productRepository.findById(productId)
    }
    
    @CacheEvict(value = ["products"], key = "#product.id")
    suspend fun updateProduct(product: Product): Product {
        return productRepository.save(product)
    }
}

// 2. 데이터베이스 읽기 복제본 활용
@Repository
class ProductRepository {
    
    @ReadOnlyTransaction
    suspend fun findById(id: String): Product? {
        return readOnlyTemplate.selectOne("SELECT * FROM products WHERE id = ?", id)
    }
    
    @WriteTransaction
    suspend fun save(product: Product): Product {
        return writeTemplate.insert("INSERT INTO products ...", product)
    }
}

// 3. Circuit Breaker로 장애 격리
@Service
class PaymentService {
    
    @CircuitBreaker(name = "payment", fallbackMethod = "fallbackPayment")
    suspend fun processPayment(paymentRequest: PaymentRequest): PaymentResult {
        return paymentGateway.process(paymentRequest)
    }
    
    suspend fun fallbackPayment(paymentRequest: PaymentRequest, ex: Exception): PaymentResult {
        // 임시 승인 후 비동기 처리
        return PaymentResult.pending(paymentRequest.transactionId)
    }
}
```

#### 결과
- 응답 시간 50% 감소 (30초 → 15초)
- 데이터베이스 부하 70% 감소
- 결제 서비스 장애 시에도 주문 접수 가능

### 사례 2: 소셜 미디어 플랫폼

#### 문제 상황
- 사용자 타임라인 생성 지연 (10초+)
- 이미지 업로드 실패율 높음 (15%)
- 알림 서비스 지연 (5분+)

#### 해결 방안
```kotlin
// 1. 비동기 타임라인 생성
@Service
class TimelineService {
    
    suspend fun generateTimeline(userId: String): Timeline {
        return coroutineScope {
            val userPosts = async { postService.getUserPosts(userId, limit = 10) }
            val friendPosts = async { postService.getFriendPosts(userId, limit = 20) }
            val recommendedPosts = async { recommendationService.getRecommendedPosts(userId) }
            
            val allPosts = (userPosts.await() + friendPosts.await() + recommendedPosts.await())
                .sortedByDescending { it.timestamp }
                .take(30)
                
            Timeline(userId, allPosts)
        }
    }
}

// 2. 이미지 업로드 retry 및 압축
@Service
class ImageUploadService {
    
    @Retryable(value = [IOException::class], maxAttempts = 3, backoff = Backoff(delay = 1000))
    suspend fun uploadImage(image: MultipartFile): String {
        val compressedImage = imageCompressionService.compress(image)
        return s3Client.upload(compressedImage)
    }
}

// 3. 알림 배치 처리
@Service
class NotificationBatchService {
    
    @Scheduled(fixedRate = 30000) // 30초마다
    suspend fun processPendingNotifications() {
        val pendingNotifications = notificationRepository.findPendingNotifications(limit = 1000)
        
        pendingNotifications.chunked(100).forEach { batch ->
            async {
                notificationGateway.sendBatch(batch)
                notificationRepository.markAsSent(batch.map { it.id })
            }
        }
    }
}
```

#### 결과
- 타임라인 생성 시간 80% 단축 (10초 → 2초)
- 이미지 업로드 성공률 98% 달성
- 알림 전송 지연 95% 감소 (5분 → 15초)

---

## 문제 해결 가이드

### 일반적인 성능 문제

#### 1. 높은 응답 시간
**증상:**
- API 응답 시간 증가
- 사용자 경험 저하
- 타임아웃 에러 발생

**진단 방법:**
```kotlin
@Component
class ResponseTimeAnalyzer {
    
    fun analyzeSlowRequests() {
        val slowRequests = metricsCollector.getRequestsSlowerThan(Duration.ofSeconds(5))
        
        slowRequests.forEach { request ->
            logger.warn("""
                Slow request detected:
                - URI: ${request.uri}
                - Method: ${request.method}
                - Duration: ${request.duration}ms
                - User: ${request.userId}
                - Trace ID: ${request.traceId}
            """.trimIndent())
        }
        
        // 패턴 분석
        val commonPatterns = findCommonPatterns(slowRequests)
        generateOptimizationRecommendations(commonPatterns)
    }
}
```

**해결 방안:**
1. **데이터베이스 쿼리 최적화**
2. **캐시 레이어 도입**
3. **비동기 처리로 전환**
4. **타임아웃 설정 조정**

#### 2. 메모리 누수
**증상:**
- 힙 메모리 사용량 지속 증가
- OutOfMemoryError 발생
- 가비지 컬렉션 빈번 발생

**진단 방법:**
```bash
# 힙 덤프 생성
jcmd <pid> GC.run_finalization
jcmd <pid> VM.gc
jmap -dump:format=b,file=heapdump.hprof <pid>

# 메모리 사용량 모니터링
jstat -gc <pid> 5s
```

**해결 방안:**
```kotlin
@Component
class MemoryLeakDetector {
    
    @Scheduled(fixedRate = 300000) // 5분마다
    fun detectMemoryLeaks() {
        val gcInfo = ManagementFactory.getGarbageCollectorMXBeans()
        val memoryInfo = ManagementFactory.getMemoryMXBean()
        
        val heapUsage = memoryInfo.heapMemoryUsage
        val heapUsedRatio = heapUsage.used.toDouble() / heapUsage.max
        
        if (heapUsedRatio > 0.9) {
            logger.warn("High heap usage detected: ${heapUsedRatio * 100}%")
            
            // 메모리 덤프 생성
            generateHeapDump()
            
            // 임시 정리 작업
            System.gc()
            cacheManager.evictAll()
        }
    }
}
```

#### 3. 높은 에러율
**증상:**
- 4xx, 5xx 응답 증가
- 서비스 가용성 저하
- 사용자 불만 증가

**진단 및 해결:**
```kotlin
@Component
class ErrorAnalyzer {
    
    @EventListener
    fun analyzeError(event: ErrorEvent) {
        val errorPattern = ErrorPattern(
            uri = event.uri,
            method = event.method,
            statusCode = event.statusCode,
            errorMessage = event.errorMessage,
            timestamp = event.timestamp
        )
        
        // 에러 패턴 저장
        errorPatternRepository.save(errorPattern)
        
        // 에러율 체크
        val recentErrorRate = calculateRecentErrorRate(event.uri, Duration.ofMinutes(5))
        
        if (recentErrorRate > 0.1) { // 10% 이상
            // Circuit Breaker 활성화
            circuitBreakerManager.openCircuitBreaker(event.serviceName)
            
            // 알림 발송
            alertingService.sendAlert(
                severity = Severity.CRITICAL,
                message = "High error rate detected: ${recentErrorRate * 100}% for ${event.uri}"
            )
        }
    }
}
```

### 성능 튜닝 체크리스트

#### 애플리케이션 레벨
- [ ] **JVM 옵션 최적화**
  ```bash
  -Xms4g -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions
  -XX:+UseZGC (Java 11+)
  ```

- [ ] **연결 풀 설정**
  ```yaml
  spring:
    datasource:
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        idle-timeout: 300000
        max-lifetime: 600000
  ```

- [ ] **스레드 풀 튜닝**
  ```kotlin
  @Bean
  fun taskExecutor(): ThreadPoolTaskExecutor {
      return ThreadPoolTaskExecutor().apply {
          corePoolSize = Runtime.getRuntime().availableProcessors()
          maxPoolSize = corePoolSize * 2
          queueCapacity = 500
          setThreadNamePrefix("async-")
          initialize()
      }
  }
  ```

#### 데이터베이스 레벨
- [ ] **인덱스 최적화**
- [ ] **쿼리 실행 계획 분석**
- [ ] **파티셔닝 적용**
- [ ] **읽기 복제본 활용**

#### 네트워크 레벨
- [ ] **CDN 활용**
- [ ] **압축 설정**
- [ ] **Keep-Alive 설정**
- [ ] **DNS 캐싱**

---

## 결론 및 권장사항

### 핵심 성공 요소

#### 1. 지속적인 모니터링
분산 시스템의 성능 최적화는 일회성 작업이 아닙니다. 지속적인 모니터링과 분석을 통해 성능 저하를 조기에 발견하고 대응해야 합니다.

#### 2. 단계적 접근
모든 최적화를 한 번에 적용하기보다는 단계적으로 접근하여 각 변경사항의 효과를 명확히 측정해야 합니다.

#### 3. 트레이드오프 고려
성능 최적화는 항상 트레이드오프를 수반합니다. 성능 vs 복잡성, 일관성 vs 가용성 등을 신중히 고려해야 합니다.

#### 4. 자동화
수동 개입을 최소화하고 자동화된 모니터링, 알림, 복구 시스템을 구축해야 합니다.

### 미래 지향적 고려사항

#### 1. 클라우드 네이티브
- 컨테이너 오케스트레이션 (Kubernetes)
- 서버리스 아키텍처
- 자동 스케일링

#### 2. 인공지능 활용
- 이상거래 탐지
- 예측적 스케일링
- 자동 최적화

#### 3. 엣지 컴퓨팅
- CDN 확장
- 엣지 캐싱
- 지역별 최적화

### 최종 권장사항

1. **성능 중심 문화 구축**: 개발팀 전체가 성능을 중요하게 생각하는 문화를 만드세요.

2. **데이터 기반 의사결정**: 추측이 아닌 실제 데이터를 바탕으로 최적화 결정을 내리세요.

3. **사용자 경험 우선**: 기술적 지표뿐만 아니라 실제 사용자 경험을 고려하세요.

4. **지속적 학습**: 새로운 기술과 최적화 기법을 지속적으로 학습하고 적용하세요.

5. **팀 협업**: 개발, 운영, 인프라 팀 간의 협업을 통해 전체적인 시스템 성능을 최적화하세요.

분산 시스템 성능 최적화는 복잡하고 지속적인 과정이지만, 체계적인 접근과 올바른 도구를 활용하면 사용자에게 뛰어난 경험을 제공할 수 있습니다.

---

**작성일**: 2025-08-04  
**버전**: 1.0  
**작성자**: Claude Code Assistant