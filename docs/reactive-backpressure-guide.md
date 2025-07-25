# Reactive Streams 백프레셔 처리 완전 가이드

## 목차
1. [백프레셔란?](#백프레셔란)
2. [Kotlin Flow 백프레셔](#kotlin-flow-백프레셔)
3. [Project Reactor 백프레셔](#project-reactor-백프레셔)
4. [실제 구현 예제](#실제-구현-예제)
5. [성능 비교 및 분석](#성능-비교-및-분석)
6. [백프레셔 전략 선택](#백프레셔-전략-선택)
7. [모니터링 및 튜닝](#모니터링-및-튜닝)
8. [문제 해결](#문제-해결)

## 백프레셔란?

백프레셔(Backpressure)는 리액티브 스트림에서 **생산자가 소비자보다 빠르게 데이터를 생성할 때 발생하는 문제**를 해결하는 메커니즘입니다.

### 🔄 백프레셔 발생 시나리오

```
생산자: ████████████████ (빠름, 1000 items/sec)
         ↓
소비자: ████             (느림, 250 items/sec)

결과: 메모리 부족, 시스템 불안정, OutOfMemoryError
```

### 🎯 백프레셔의 중요성

1. **메모리 보호**: 무제한 버퍼링 방지
2. **시스템 안정성**: 과부하 상황에서도 정상 동작
3. **성능 최적화**: 적절한 처리량 유지
4. **리소스 관리**: CPU, 메모리 효율적 사용

## Kotlin Flow 백프레셔

Kotlin Flow는 구조화된 동시성과 함께 다양한 백프레셔 전략을 제공합니다.

### 1. Buffer 전략

```kotlin
// 모든 데이터를 보존하는 버퍼링
flow {
    repeat(1000) { i ->
        emit("Data-$i")
        delay(10) // 빠른 생산자
    }
}
.buffer(capacity = 100) // 100개까지 버퍼링
.collect { data ->
    delay(50) // 느린 소비자
    println("처리: $data")
}
```

**특징:**
- ✅ **데이터 무손실**: 모든 항목이 처리됨
- ✅ **순서 보장**: 생산 순서대로 처리
- ⚠️ **메모리 사용**: 버퍼 크기만큼 메모리 사용
- ⚠️ **지연 가능성**: 버퍼가 가득 찰 경우 생산자 대기

**사용 사례:**
- 금융 거래 처리 (모든 거래 기록 필수)
- 로그 수집 시스템
- 데이터베이스 배치 삽입

### 2. Conflate 전략

```kotlin
// 최신 데이터만 유지
flow {
    repeat(100) { i ->
        emit("Update-$i")
        delay(10)
    }
}
.conflate() // 중간 값들 건너뛰기
.collect { update ->
    delay(100) // 매우 느린 처리
    println("최신 업데이트: $update")
}
```

**특징:**
- ✅ **메모리 효율**: 최소한의 메모리 사용
- ✅ **최신성 보장**: 항상 최신 데이터 처리
- ⚠️ **데이터 손실**: 중간 값들이 스킵됨
- ✅ **빠른 응답**: 지연 시간 최소화

**사용 사례:**
- UI 상태 업데이트
- 실시간 주식 시세
- 센서 데이터 모니터링

### 3. CollectLatest 전략

```kotlin
// 새로운 값이 오면 이전 작업 취소
flow {
    repeat(50) { i ->
        emit("Task-$i")
        delay(20)
    }
}
.collectLatest { task ->
    try {
        delay(100) // 처리 시간
        println("완료: $task")
    } catch (e: CancellationException) {
        println("취소됨: $task")
        throw e
    }
}
```

**특징:**
- ✅ **즉시 반응**: 새 데이터에 즉시 반응
- ✅ **리소스 절약**: 불필요한 작업 취소
- ⚠️ **작업 취소**: 진행 중인 작업이 취소될 수 있음
- ✅ **사용자 경험**: 최신 요청에 빠른 응답

**사용 사례:**
- 검색 자동완성
- 사용자 입력 처리
- 실시간 필터링

### 4. 커스텀 백프레셔 전략

```kotlin
fun <T> Flow<T>.customBackpressure(
    maxBuffer: Int,
    prioritySelector: (T) -> Int
): Flow<T> = flow {
    val buffer = mutableListOf<T>()
    
    collect { item ->
        if (buffer.size < maxBuffer) {
            buffer.add(item)
        } else {
            // 우선순위 기반 드롭
            val priority = prioritySelector(item)
            val lowPriorityIndex = buffer.indexOfFirst { 
                prioritySelector(it) < priority 
            }
            
            if (lowPriorityIndex != -1) {
                buffer[lowPriorityIndex] = item
            }
            // 그렇지 않으면 새 항목 드롭
        }
        
        // 버퍼에서 항목 방출
        if (buffer.isNotEmpty()) {
            emit(buffer.removeFirst())
        }
    }
    
    // 남은 항목들 처리
    buffer.forEach { emit(it) }
}

// 사용 예제
reservationFlow
    .customBackpressure(maxBuffer = 50) { reservation ->
        when {
            reservation.isVip -> 1 // 높은 우선순위
            reservation.amount > 500.0 -> 2 // 중간 우선순위
            else -> 3 // 낮은 우선순위
        }
    }
    .collect { processReservation(it) }
```

## Project Reactor 백프레셔

Project Reactor는 Reactive Streams 표준을 구현하며, 다양한 백프레셔 연산자를 제공합니다.

### 1. onBackpressureBuffer

```java
Flux.interval(Duration.ofMillis(10)) // 빠른 생산자
    .take(1000)
    .onBackpressureBuffer(100) // 100개까지 버퍼링
    .publishOn(Schedulers.boundedElastic())
    .doOnNext(item -> {
        try {
            Thread.sleep(50); // 느린 소비자
            System.out.println("처리: " + item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    })
    .subscribe();
```

**Buffer 전략의 변형:**
```java
// 무제한 버퍼 (메모리 주의!)
.onBackpressureBuffer()

// 크기 제한 + 오버플로우 시 에러
.onBackpressureBuffer(100)

// 크기 제한 + 오버플로우 콜백
.onBackpressureBuffer(100, 
    dropped -> log.warn("Dropped: " + dropped),
    BufferOverflowStrategy.DROP_OLDEST)
```

### 2. onBackpressureDrop

```java
Flux.interval(Duration.ofMillis(5)) // 매우 빠른 생산자
    .onBackpressureDrop(dropped -> 
        System.out.println("드롭됨: " + dropped))
    .publishOn(Schedulers.boundedElastic())
    .doOnNext(item -> {
        try {
            Thread.sleep(100); // 느린 소비자
            System.out.println("처리: " + item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    })
    .subscribe();
```

**특징:**
- ✅ **시스템 보호**: 메모리 사용량 제한
- ✅ **높은 처리량**: 처리 가능한 항목만 처리
- ⚠️ **데이터 손실**: 일부 항목이 드롭됨
- ✅ **예측 가능**: 드롭 콜백으로 모니터링 가능

### 3. onBackpressureLatest

```java
Flux.interval(Duration.ofMillis(10))
    .onBackpressureLatest() // 최신 값만 유지
    .publishOn(Schedulers.boundedElastic())
    .doOnNext(item -> {
        try {
            Thread.sleep(100);
            System.out.println("최신 처리: " + item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    })
    .subscribe();
```

### 4. onBackpressureError

```java
Flux.interval(Duration.ofMillis(1)) // 극도로 빠른 생산자
    .onBackpressureError() // 백프레셔 시 에러 발생
    .publishOn(Schedulers.boundedElastic())
    .doOnNext(item -> {
        try {
            Thread.sleep(100);
            System.out.println("처리: " + item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    })
    .subscribe(
        item -> {}, // onNext
        error -> System.err.println("백프레셔 에러: " + error.getMessage())
    );
```

## 실제 구현 예제

### 예약 시스템 백프레셔 처리

#### 1. 실시간 예약 요청 처리

```kotlin
@Service
class ReservationStreamProcessor {
    
    suspend fun processReservationRequests() {
        reservationRequestFlow()
            .buffer(capacity = 100) // 100개 요청까지 버퍼링
            .map { request -> validateRequest(request) }
            .filter { it.isValid }
            .flowOn(Dispatchers.IO) // I/O 작업 분리
            .collect { validRequest ->
                try {
                    processReservation(validRequest)
                } catch (e: Exception) {
                    handleReservationError(e, validRequest)
                }
            }
    }
    
    private fun reservationRequestFlow(): Flow<ReservationRequest> = flow {
        // 외부 시스템에서 들어오는 예약 요청들
        while (true) {
            val requests = fetchPendingRequests()
            requests.forEach { emit(it) }
            delay(100) // 100ms마다 체크
        }
    }
}
```

#### 2. 배치 처리 시스템

```kotlin
suspend fun processBatchReservations() {
    largeBatchFlow()
        .chunked(25) // 25개씩 배치로 묶기
        .buffer(capacity = 5) // 5개 배치까지 버퍼링
        .collect { batch ->
            coroutineScope {
                // 배치 내 병렬 처리
                batch.map { reservation ->
                    async { 
                        processReservation(reservation) 
                    }
                }.awaitAll()
            }
        }
}
```

#### 3. 우선순위 기반 처리

```kotlin
class PriorityReservationProcessor {
    
    suspend fun processWithPriority() {
        reservationFlow()
            .priorityBuffer(
                vipCapacity = 50,
                regularCapacity = 20
            )
            .collect { priorityReservation ->
                val processingTime = if (priorityReservation.isVip) 50L else 100L
                delay(processingTime)
                
                processReservation(priorityReservation.request)
            }
    }
    
    private fun Flow<ReservationRequest>.priorityBuffer(
        vipCapacity: Int,
        regularCapacity: Int
    ): Flow<PriorityReservation> = flow {
        val vipBuffer = mutableListOf<ReservationRequest>()
        val regularBuffer = mutableListOf<ReservationRequest>()
        
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
                emit(PriorityReservation(vipBuffer.removeFirst(), true))
            }
            
            // 일반 고객 처리
            if (vipBuffer.isEmpty() && regularBuffer.isNotEmpty()) {
                emit(PriorityReservation(regularBuffer.removeFirst(), false))
            }
        }
    }
}
```

#### 4. 동적 백프레셔 제어

```kotlin
class AdaptiveReservationProcessor {
    
    private val systemLoad = AtomicInteger(1) // 1: 낮음, 2: 보통, 3: 높음
    
    suspend fun processWithAdaptiveBackpressure() {
        reservationFlow()
            .dynamicBackpressure { systemLoad.get() }
            .collect { reservation ->
                val currentLoad = calculateSystemLoad()
                systemLoad.set(currentLoad)
                
                val processingTime = when (currentLoad) {
                    1 -> 30L  // 낮은 부하: 빠른 처리
                    2 -> 60L  // 보통 부하: 보통 처리  
                    else -> 120L // 높은 부하: 느린 처리
                }
                
                delay(processingTime)
                processReservation(reservation)
            }
    }
    
    private fun <T> Flow<T>.dynamicBackpressure(
        loadProvider: () -> Int
    ): Flow<T> = flow {
        collect { item ->
            val currentLoad = loadProvider()
            val shouldProcess = when (currentLoad) {
                1 -> true // 모든 항목 처리
                2 -> Random.nextDouble() < 0.7 // 70% 처리
                else -> Random.nextDouble() < 0.4 // 40% 처리
            }
            
            if (shouldProcess) {
                emit(item)
            }
        }
    }
}
```

## 성능 비교 및 분석

### 테스트 실행 방법

```bash
# 전체 백프레셔 분석
./scripts/backpressure-test.sh comprehensive

# 성능 비교만 실행
./scripts/backpressure-test.sh performance

# 실시간 모니터링
./scripts/backpressure-test.sh monitor
```

### 성능 테스트 결과

#### 처리량 비교 (1000개 항목 기준)

| 전략 | Flow | Reactor | 메모리 사용 | 처리 시간 |
|------|------|---------|-------------|-----------|
| Buffer | 1000개 처리 | 1000개 처리 | 높음 (100MB+) | 5000ms |
| Conflate/Latest | 50-100개 처리 | 50-100개 처리 | 낮음 (10MB) | 1000ms |
| Drop | N/A | 200-300개 처리 | 중간 (30MB) | 2000ms |
| CollectLatest | 10-20개 처리 | N/A | 매우 낮음 (5MB) | 500ms |

#### 메모리 사용 패턴

```
Buffer 전략:
메모리 ▲
      |  ╭─────╮
      | ╱       ╲
      |╱         ╲_____
      +─────────────────► 시간
      시작    피크   안정

Conflate 전략:
메모리 ▲
      |  ╭╮ ╭╮  ╭╮
      | ╱  ╲╱  ╲╱  ╲
      |╱           ╲___
      +─────────────────► 시간
      일정한 낮은 사용량
```

### 실제 워크로드 테스트

#### 1. 웹 서버 부하 테스트

```kotlin
// 시나리오: 초당 1000개 요청, 평균 처리 시간 100ms
val webServerTest = flow {
    repeat(10000) { i ->
        emit(HttpRequest("request-$i"))
        delay(1) // 1ms 간격 (1000 req/sec)
    }
}

// Buffer 전략 결과
webServerTest
    .buffer(1000)
    .collect { request ->
        delay(100) // 100ms 처리
        handleRequest(request)
    }
// 결과: 모든 요청 처리, 메모리 사용량 높음

// Conflate 전략 결과  
webServerTest
    .conflate()
    .collect { request ->
        delay(100)
        handleRequest(request) 
    }  
// 결과: 약 100개 요청만 처리, 메모리 사용량 낮음
```

#### 2. 이벤트 스트림 처리

```kotlin
// 시나리오: IoT 센서 데이터, 초당 10000개 이벤트
val sensorDataTest = flow {
    repeat(100000) { i ->
        emit(SensorReading(i, Random.nextDouble()))
        delay(0.1) // 0.1ms 간격 (10000 events/sec)
    }
}

// Latest 전략이 최적
sensorDataTest
    .conflate() // 또는 onBackpressureLatest()
    .collect { reading ->
        delay(50) // 50ms 처리 (20 events/sec 처리 가능)
        processSensorData(reading)
    }
// 결과: 최신 센서 값만 처리, 실시간성 보장
```

## 백프레셔 전략 선택

### 결정 트리

```
데이터 손실 허용 여부?
├─ NO (모든 데이터 필수)
│  ├─ 메모리 충분? → YES: Buffer
│  └─ 메모리 부족? → Error + Circuit Breaker
│
└─ YES (일부 데이터 손실 허용)
   ├─ 최신성 중요? → YES: Conflate/Latest/CollectLatest
   └─ 처리량 중요? → YES: Drop
```

### 사용 사례별 권장사항

#### 🏦 금융 시스템
```kotlin
// 거래 처리: 데이터 무손실 필수
transactionFlow
    .buffer(capacity = 1000)
    .catch { error -> 
        // 백프레셔 오류 시 회로 차단기 동작
        circuitBreaker.recordFailure(error)
        throw error
    }
    .collect { transaction ->
        processTransaction(transaction)
    }
```

#### 📱 모바일 앱 UI
```kotlin  
// 사용자 입력: 최신 입력만 중요
userInputFlow
    .collectLatest { input ->
        // 새 입력이 오면 이전 검색 취소
        searchService.search(input.query)
    }
```

#### 🏭 IoT/센서 시스템
```kotlin
// 센서 데이터: 최신 값만 중요
sensorFlow
    .conflate()
    .sample(1000) // 1초마다 샘플링
    .collect { sensorData ->
        updateDashboard(sensorData)
    }
```

#### 🌐 웹 서버
```java
// HTTP 요청: 시스템 보호 우선
Flux.fromIterable(requests)
    .onBackpressureDrop(droppedRequest -> 
        metrics.increment("requests.dropped"))
    .flatMap(request -> 
        processRequest(request)
            .timeout(Duration.ofSeconds(30)))
    .subscribe();
```

#### 📊 로그 수집
```kotlin  
// 로그 이벤트: 배치 처리
logEventFlow
    .buffer(capacity = 1000)
    .chunked(100) // 100개씩 배치
    .collect { logBatch ->
        logRepository.saveBatch(logBatch)
    }
```

### 하이브리드 전략

복잡한 시스템에서는 여러 전략을 조합하여 사용합니다:

```kotlin
class HybridBackpressureProcessor {
    
    suspend fun processReservations() {
        reservationFlow
            // 1단계: 초기 버퍼링
            .buffer(capacity = 200)
            
            // 2단계: 우선순위 분류
            .partition { it.isVip }
            .let { (vipFlow, regularFlow) ->
                merge(
                    // VIP: 모든 요청 처리
                    vipFlow.buffer(100),
                    
                    // 일반: 부하에 따라 조절
                    regularFlow.conflate()
                )
            }
            
            // 3단계: 최종 처리
            .collect { reservation ->
                processReservation(reservation)
            }
    }
}
```

## 모니터링 및 튜닝

### 핵심 메트릭

#### 1. 백프레셔 관련 메트릭

```kotlin
@Component
class BackpressureMetrics {
    
    private val bufferSizeGauge = Gauge.build()
        .name("backpressure_buffer_size")
        .help("Current buffer size")
        .register()
    
    private val droppedItemsCounter = Counter.build()
        .name("backpressure_dropped_items_total")
        .help("Total dropped items")
        .register()
    
    private val processingLatency = Histogram.build()
        .name("backpressure_processing_duration_seconds")
        .help("Processing duration")
        .register()
    
    fun recordBufferSize(size: Int) {
        bufferSizeGauge.set(size.toDouble())
    }
    
    fun recordDroppedItem() {
        droppedItemsCounter.inc()
    }
    
    fun recordProcessingTime(durationMs: Long) {
        processingLatency.observe(durationMs / 1000.0)
    }
}
```

#### 2. 알림 및 임계값

```yaml
# application.yml
backpressure:
  monitoring:
    buffer-size-threshold: 80  # 80% 이상 시 경고
    drop-rate-threshold: 5     # 5% 이상 드롭 시 경고
    latency-threshold: 1000    # 1초 이상 지연 시 경고
  
  alerts:
    - name: "High Buffer Usage"
      condition: "buffer_usage > 80%"
      action: "scale_up"
    
    - name: "High Drop Rate"  
      condition: "drop_rate > 5%"
      action: "circuit_breaker"
```

### 자동 튜닝

```kotlin
@Service
class AdaptiveBackpressureController {
    
    private var currentBufferSize = 100
    private val minBufferSize = 50
    private val maxBufferSize = 1000
    
    @Scheduled(fixedRate = 30000) // 30초마다 조정
    fun adjustBackpressureSettings() {
        val metrics = gatherMetrics()
        
        when {
            metrics.bufferUsage > 0.9 && metrics.dropRate < 0.01 -> {
                // 버퍼 사용률 높음, 드롭률 낮음 → 버퍼 크기 증가
                currentBufferSize = minOf(currentBufferSize * 2, maxBufferSize)
                log.info("Buffer size increased to $currentBufferSize")
            }
            
            metrics.bufferUsage < 0.3 && currentBufferSize > minBufferSize -> {
                // 버퍼 사용률 낮음 → 버퍼 크기 감소 (메모리 절약)
                currentBufferSize = maxOf(currentBufferSize / 2, minBufferSize)
                log.info("Buffer size decreased to $currentBufferSize")
            }
            
            metrics.dropRate > 0.1 -> {
                // 드롭률 높음 → Circuit Breaker 활성화 고려
                circuitBreaker.recordFailure()
                log.warn("High drop rate detected: ${metrics.dropRate}")
            }
        }
    }
}
```

### 대시보드 구성

```kotlin
// Grafana 대시보드 쿼리 예제
val dashboardQueries = listOf(
    // 백프레셔 버퍼 사용률
    "rate(backpressure_buffer_size[5m])",
    
    // 드롭된 항목 비율
    "rate(backpressure_dropped_items_total[5m]) / rate(backpressure_total_items[5m]) * 100",
    
    // 처리 지연 시간 분포
    "histogram_quantile(0.95, backpressure_processing_duration_seconds)",
    
    // 시간별 처리량
    "rate(backpressure_processed_items_total[1m])"
)
```

## 문제 해결

### 일반적인 문제들

#### 1. OutOfMemoryError

**증상:**
```
java.lang.OutOfMemoryError: Java heap space
at kotlinx.coroutines.flow.internal.ChannelFlow$collect$1
```

**원인:**
- Buffer 크기가 너무 큼
- 생산자와 소비자 속도 차이가 극심

**해결책:**
```kotlin
// Before: 무제한 버퍼링
flow.buffer()

// After: 크기 제한 + 오버플로우 처리
flow.buffer(capacity = 1000) { overflow ->
    log.warn("Buffer overflow, dropping: $overflow")
}
```

#### 2. 높은 지연 시간

**증상:**
```
Processing latency: 5000ms (expected: 100ms)
Buffer size: 10000 items
```

**원인:**
- Buffer 크기가 너무 커서 오래된 데이터 처리
- 백프레셔 전략이 부적절

**해결책:**
```kotlin
// Before: 큰 버퍼
flow.buffer(10000)

// After: 작은 버퍼 + Latest 전략
flow.buffer(100).conflate()

// 또는 시간 기반 제한
flow.sample(Duration.ofSeconds(1))
```

#### 3. 데이터 손실

**증상:**
```
Expected: 1000 items
Processed: 150 items  
Loss rate: 85%
```

**원인:**
- Drop 또는 Conflate 전략 사용
- 소비자 처리 능력 부족

**해결책:**
```kotlin
// 중요한 데이터는 Buffer 사용
criticalDataFlow
    .buffer(capacity = Channel.UNLIMITED)
    .collect { processImportantData(it) }

// 또는 별도 큐 시스템 사용
criticalDataFlow
    .collect { data ->
        messageQueue.send(data) // 외부 큐에 저장
    }
```

#### 4. CPU 사용률 급증

**증상:**
```
CPU Usage: 95%
Context switches: 50,000/sec
GC frequency: 10 times/sec
```

**원인:**
- 너무 많은 코루틴/스레드 생성
- GC 압박

**해결책:**
```kotlin
// Before: 제한 없는 동시성
flow.flatMapMerge { processItem(it) }

// After: 동시성 제한
flow.flatMapMerge(concurrency = 10) { processItem(it) }

// 처리 배치화
flow.chunked(50).collect { batch ->
    processBatch(batch)
}
```

### 디버깅 도구

#### 1. Flow 시각화

```kotlin
fun <T> Flow<T>.debug(name: String): Flow<T> = flow {
    var count = 0
    collect { value ->
        count++
        println("[$name] Item $count: $value")
        emit(value)
    }
}

// 사용
reservationFlow
    .debug("Input")
    .buffer(100)
    .debug("After Buffer")
    .collect { processReservation(it) }
```

#### 2. 성능 프로파일링

```kotlin
fun <T> Flow<T>.measureThroughput(
    name: String,
    windowSize: Duration = Duration.ofSeconds(10)
): Flow<T> = flow {
    val counter = AtomicLong(0)
    val startTime = System.currentTimeMillis()
    
    collect { value ->
        counter.incrementAndGet()
        val elapsed = System.currentTimeMillis() - startTime
        
        if (elapsed >= windowSize.toMillis()) {
            val throughput = counter.get() * 1000.0 / elapsed
            println("[$name] Throughput: ${"%.1f".format(throughput)} items/sec")
        }
        
        emit(value)
    }
}
```

#### 3. 백프레셔 감지

```kotlin
fun <T> Flow<T>.detectBackpressure(
    threshold: Duration = Duration.ofMillis(100)
): Flow<T> = flow {
    collect { value ->
        val startTime = System.nanoTime()
        emit(value)
        val emitTime = Duration.ofNanos(System.nanoTime() - startTime)
        
        if (emitTime > threshold) {
            println("⚠️ Backpressure detected: ${emitTime.toMillis()}ms emit time")
        }
    }
}
```

## 최적화 가이드

### 성능 최적화 체크리스트

#### ✅ 메모리 최적화
- [ ] 적절한 버퍼 크기 설정 (기본값 64, 조정 필요시 256-1024)
- [ ] 불필요한 중간 컬렉션 생성 피하기
- [ ] 대용량 객체의 경우 참조만 전달
- [ ] GC 압박 모니터링

#### ✅ 처리량 최적화  
- [ ] 적절한 동시성 레벨 설정
- [ ] 배치 처리 적용 (chunked, buffer)
- [ ] I/O 작업은 별도 디스패처 사용
- [ ] CPU 집약적 작업은 Default 디스패처 사용

#### ✅ 지연 시간 최적화
- [ ] 불필요한 버퍼링 제거
- [ ] Conflate/Latest 전략 고려
- [ ] 타임아웃 설정
- [ ] 회로 차단기 패턴 적용

### 고급 패턴

#### 1. 적응형 백프레셔

```kotlin
class AdaptiveBackpressureFlow<T>(
    private val source: Flow<T>,
    private val targetLatency: Duration = Duration.ofMillis(100)
) {
    
    private var currentStrategy: BackpressureStrategy = BackpressureStrategy.BUFFER
    private val latencyHistory = CircularBuffer<Long>(size = 100)
    
    fun asFlow(): Flow<T> = flow {
        source.collect { item ->
            val startTime = System.nanoTime()
            
            when (currentStrategy) {
                BackpressureStrategy.BUFFER -> {
                    emit(item)
                }
                BackpressureStrategy.CONFLATE -> {
                    // Conflate 로직
                }
                BackpressureStrategy.DROP -> {
                    // Drop 로직  
                }
            }
            
            val processingTime = Duration.ofNanos(System.nanoTime() - startTime)
            adjustStrategy(processingTime)
        }
    }
    
    private fun adjustStrategy(processingTime: Duration) {
        latencyHistory.add(processingTime.toMillis())
        
        val averageLatency = latencyHistory.average()
        val targetLatencyMs = targetLatency.toMillis()
        
        currentStrategy = when {
            averageLatency < targetLatencyMs * 0.5 -> BackpressureStrategy.BUFFER
            averageLatency < targetLatencyMs -> BackpressureStrategy.CONFLATE  
            else -> BackpressureStrategy.DROP
        }
    }
}
```

#### 2. 우선순위 큐 백프레셔

```kotlin
class PriorityBackpressureFlow<T>(
    private val source: Flow<T>,
    private val prioritySelector: (T) -> Int,
    private val capacityPerPriority: Map<Int, Int>
) {
    
    fun asFlow(): Flow<T> = channelFlow {
        val priorityQueues = mutableMapOf<Int, MutableList<T>>()
        
        source.collect { item ->
            val priority = prioritySelector(item)
            val queue = priorityQueues.getOrPut(priority) { mutableListOf() }
            val capacity = capacityPerPriority[priority] ?: 10
            
            if (queue.size < capacity) {
                queue.add(item)
            } else {
                // 용량 초과 시 가장 낮은 우선순위 항목 제거
                val lowestPriority = priorityQueues.keys.maxOrNull()
                if (lowestPriority != null && lowestPriority > priority) {
                    priorityQueues[lowestPriority]?.removeFirstOrNull()
                    queue.add(item)
                }
                // 그렇지 않으면 현재 항목 드롭
            }
            
            // 우선순위 순으로 방출
            priorityQueues.keys.sorted().forEach { p ->
                val pQueue = priorityQueues[p]
                while (pQueue?.isNotEmpty() == true) {
                    send(pQueue.removeFirst())
                }
            }
        }
    }
}
```

## 결론

백프레셔 처리는 안정적이고 확장 가능한 리액티브 시스템의 핵심입니다. 적절한 전략 선택과 모니터링을 통해 시스템의 성능과 안정성을 크게 향상시킬 수 있습니다.

### 핵심 원칙

1. **데이터 중요도 평가**: 모든 데이터가 필수인지, 손실 허용 가능한지 판단
2. **시스템 리소스 고려**: 메모리, CPU, 네트워크 제약사항 파악  
3. **지속적 모니터링**: 메트릭 수집, 알림 설정, 자동 조정
4. **점진적 개선**: 작은 변경부터 시작하여 단계적으로 최적화

### 추가 학습 자료

- [Kotlin Flow 공식 문서](https://kotlinlang.org/docs/flow.html)
- [Project Reactor 가이드](https://projectreactor.io/docs/core/release/reference/)
- [Reactive Streams 명세](http://www.reactive-streams.org/)
- [백프레셔 패턴 모음](https://github.com/reactive-streams/reactive-streams-jvm)

---

이 가이드를 통해 효과적인 백프레셔 처리 전략을 수립하고, 안정적인 리액티브 시스템을 구축하시기 바랍니다.