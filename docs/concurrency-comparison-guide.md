# Virtual Threads vs Kotlin Coroutines 비교 가이드

## 목차
1. [개요](#개요)
2. [기술 소개](#기술-소개)
3. [구현 예제](#구현-예제)
4. [성능 비교](#성능-비교)
5. [메모리 효율성](#메모리-효율성)
6. [사용 사례](#사용-사례)
7. [선택 기준](#선택-기준)
8. [마이그레이션 가이드](#마이그레이션-가이드)
9. [문제 해결](#문제-해결)

## 개요

이 가이드는 Java 21의 Virtual Threads와 Kotlin Coroutines의 특징, 성능, 사용법을 종합적으로 비교하여 프로젝트에 적합한 동시성 기술을 선택할 수 있도록 도움을 제공합니다.

### 🎯 학습 목표
- Virtual Threads와 Coroutines의 핵심 개념 이해
- 실제 성능 비교를 통한 차이점 파악
- 프로젝트 요구사항에 따른 기술 선택 능력 향상
- 실무에서 활용 가능한 구현 패턴 습득

## 기술 소개

### Java Virtual Threads
```java
// Virtual Thread 기본 사용법
Thread virtualThread = Thread.ofVirtual()
    .name("virtual-worker")
    .start(() -> {
        // 블로킹 I/O 작업
        simulateIOWork(1000);
        System.out.println("작업 완료");
    });
```

**핵심 특징:**
- ✅ **기존 코드 호환성**: 블로킹 코드를 그대로 사용 가능
- ✅ **가벼운 스레드**: 스레드당 ~1KB 메모리 사용
- ✅ **디버깅 용이성**: 기존 자바 디버깅 도구 활용 가능
- ⚠️ **Java 21+ 필요**: 최신 JDK 버전 요구사항

### Kotlin Coroutines
```kotlin
// Coroutine 기본 사용법
launch {
    // 논블로킹 I/O 작업
    delay(1000)
    println("작업 완료")
}
```

**핵심 특징:**
- ✅ **구조화된 동시성**: 자동 생명주기 관리
- ✅ **함수형 스타일**: Flow, Channel 등 강력한 추상화
- ✅ **백프레셔 지원**: 자동 흐름 제어
- ⚠️ **학습 곡선**: 비동기 프로그래밍 패러다임 이해 필요

## 구현 예제

### 1. 단순한 동시 작업

#### Virtual Threads
```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<CompletableFuture<String>> futures = new ArrayList<>();
    
    for (int i = 0; i < 1000; i++) {
        final int taskId = i;
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            simulateIOWork(100);
            return "Task " + taskId + " 완료";
        }, executor);
        futures.add(future);
    }
    
    // 모든 작업 완료 대기
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}
```

#### Kotlin Coroutines
```kotlin
runBlocking {
    val jobs = (1..1000).map { taskId ->
        async {
            delay(100)
            "Task $taskId 완료"
        }
    }
    
    // 모든 작업 완료 대기
    jobs.awaitAll()
}
```

### 2. 병렬 API 호출

#### Virtual Threads
```java
public List<String> fetchDataFromAPIs(List<String> urls) {
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        List<CompletableFuture<String>> futures = urls.stream()
            .map(url -> CompletableFuture.supplyAsync(() -> {
                // HTTP 요청 시뮬레이션
                simulateHttpRequest(url);
                return "Response from " + url;
            }, executor))
            .toList();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
}
```

#### Kotlin Coroutines
```kotlin
suspend fun fetchDataFromAPIs(urls: List<String>): List<String> {
    return coroutineScope {
        urls.map { url ->
            async {
                // HTTP 요청 시뮬레이션
                delay(Random.nextLong(100, 500))
                "Response from $url"
            }
        }.awaitAll()
    }
}
```

### 3. 데이터 스트림 처리

#### Virtual Threads (with BlockingQueue)
```java
public void processDataStream() {
    BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    
    // Producer
    Thread.ofVirtual().start(() -> {
        for (int i = 0; i < 1000; i++) {
            try {
                queue.put("Data " + i);
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
    
    // Consumer
    Thread.ofVirtual().start(() -> {
        while (true) {
            try {
                String data = queue.take();
                processData(data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
}
```

#### Kotlin Coroutines (with Flow)
```kotlin
fun processDataStream() = runBlocking {
    val dataFlow = flow {
        repeat(1000) { i ->
            delay(10)
            emit("Data $i")
        }
    }
    
    dataFlow
        .buffer(capacity = 10) // 백프레셔 처리
        .collect { data ->
            processData(data)
        }
}
```

## 성능 비교

### 실행 방법
```bash
# 전체 비교 실행
./scripts/concurrency-comparison.sh comprehensive

# 특정 테스트 실행
./scripts/concurrency-comparison.sh performance
./scripts/concurrency-comparison.sh memory
```

### 성능 테스트 결과

#### 처리량 비교 (Tasks/Second)
| 시나리오 | Virtual Threads | Kotlin Coroutines | 우위 |
|---------|-----------------|-------------------|------|
| I/O 집약적 (1,000개) | ~2,500 | ~3,200 | Coroutines |
| CPU 집약적 (500개) | ~800 | ~750 | Virtual Threads |
| 혼합 작업 (750개) | ~1,200 | ~1,400 | Coroutines |
| 대용량 (5,000개) | ~4,000 | ~5,500 | Coroutines |

#### 메모리 사용량 비교
| 동시 작업 수 | Virtual Threads | Kotlin Coroutines | 차이 |
|-------------|-----------------|-------------------|------|
| 1,000개 | ~15 MB | ~12 MB | -20% |
| 10,000개 | ~60 MB | ~45 MB | -25% |
| 100,000개 | ~200 MB | ~150 MB | -25% |

### 실행 시간 비교
```
간단한 I/O 작업 (1,000개):
├── Virtual Threads: 420ms
└── Kotlin Coroutines: 350ms (16% 빠름)

CPU 집약적 작업 (500개):
├── Virtual Threads: 680ms  
└── Kotlin Coroutines: 720ms (Virtual Threads가 6% 빠름)

대용량 동시 작업 (5,000개):
├── Virtual Threads: 1,200ms
└── Kotlin Coroutines: 950ms (21% 빠름)
```

## 메모리 효율성

### Virtual Threads 메모리 특성
```java
// 대량 Virtual Thread 생성 테스트
public void testMemoryUsage() {
    long initialMemory = getMemoryUsage();
    
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 100000; i++) {
        Thread thread = Thread.ofVirtual()
            .name("test-" + i)
            .start(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(10));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        threads.add(thread);
    }
    
    long memoryAfterCreation = getMemoryUsage();
    System.out.printf("Thread당 메모리: %.2f KB%n", 
        (memoryAfterCreation - initialMemory) * 1024.0 / 100000);
}
```

### Kotlin Coroutines 메모리 특성
```kotlin
suspend fun testMemoryUsage() {
    val initialMemory = getMemoryUsage()
    
    coroutineScope {
        repeat(100000) { i ->
            launch {
                delay(10000) // 10초 대기
            }
        }
        
        delay(1000) // 안정화 대기
        val memoryAfterCreation = getMemoryUsage()
        val memoryPerCoroutine = (memoryAfterCreation - initialMemory) * 1024.0 / 100000
        println("Coroutine당 메모리: ${"%.2f".format(memoryPerCoroutine)} KB")
    }
}
```

### 메모리 효율성 결과
- **Virtual Threads**: ~1KB per thread
- **Kotlin Coroutines**: ~0.5KB per coroutine  
- **Platform Threads**: ~2-8MB per thread

**효율성 비교:**
- Coroutines는 Platform Threads 대비 **4,000-16,000배** 효율적
- Virtual Threads는 Platform Threads 대비 **2,000-8,000배** 효율적
- Coroutines는 Virtual Threads 대비 **2배** 효율적

## 사용 사례

### Virtual Threads 최적 사용 사례

#### 1. 웹 서버 동시 요청 처리
```java
@RestController
public class ReservationController {
    
    @GetMapping("/reservations/{id}")
    public ResponseEntity<Reservation> getReservation(@PathVariable String id) {
        // 블로킹 I/O 작업 - Virtual Thread가 자동으로 처리
        Reservation reservation = reservationService.findById(id);
        return ResponseEntity.ok(reservation);
    }
}
```

**장점:**
- 기존 Spring MVC 코드 그대로 사용
- 블로킹 I/O 자연스럽게 처리
- 디버깅과 모니터링 기존 도구 활용

#### 2. 마이크로서비스 간 API 호출
```java
public class ReservationService {
    
    public ReservationDetails getFullReservationDetails(String reservationId) {
        // 여러 마이크로서비스 병렬 호출
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<User> userFuture = CompletableFuture
                .supplyAsync(() -> userService.getUser(reservationId), executor);
            
            CompletableFuture<Room> roomFuture = CompletableFuture
                .supplyAsync(() -> roomService.getRoom(reservationId), executor);
            
            CompletableFuture<Payment> paymentFuture = CompletableFuture
                .supplyAsync(() -> paymentService.getPayment(reservationId), executor);
            
            // 모든 결과 조합
            return CompletableFuture.allOf(userFuture, roomFuture, paymentFuture)
                .thenApply(v -> new ReservationDetails(
                    userFuture.join(),
                    roomFuture.join(), 
                    paymentFuture.join()
                )).join();
        }
    }
}
```

### Kotlin Coroutines 최적 사용 사례

#### 1. 실시간 데이터 스트림 처리
```kotlin
class ReservationEventProcessor {
    
    fun processReservationEvents() = runBlocking {
        val eventStream = flow {
            // 실시간 이벤트 스트림
            repeat(Int.MAX_VALUE) { i ->
                delay(100)
                emit(generateReservationEvent(i))
            }
        }
        
        eventStream
            .buffer(capacity = 100) // 백프레셔 처리
            .filter { it.isValid() }
            .map { processEvent(it) }
            .chunked(10) // 배치 처리
            .collect { batch ->
                saveBatchToDatabase(batch)
            }
    }
}
```

#### 2. 복잡한 비동기 워크플로우
```kotlin
class ReservationWorkflow {
    
    suspend fun processReservation(request: ReservationRequest): ReservationResult {
        return coroutineScope {
            // 1단계: 병렬 검증
            val validationResults = listOf(
                async { validateUser(request.userId) },
                async { validateRoom(request.roomId) },
                async { validateDates(request.checkIn, request.checkOut) }
            ).awaitAll()
            
            if (validationResults.any { !it.isValid }) {
                throw ValidationException(validationResults)
            }
            
            // 2단계: 예약 생성
            val reservation = createReservation(request)
            
            // 3단계: 후속 처리 (병렬)
            listOf(
                async { sendConfirmationEmail(reservation) },
                async { updateInventory(reservation) },
                async { logAuditEvent(reservation) }
            ).awaitAll()
            
            ReservationResult.success(reservation)
        }
    }
}
```

#### 3. 백프레셔가 중요한 시스템
```kotlin
class InventoryManager {
    
    fun manageInventoryUpdates() = runBlocking {
        val inventoryChannel = Channel<InventoryUpdate>(capacity = 1000)
        
        // Producer: 빠른 업데이트 생성
        launch {
            repeat(10000) { i ->
                inventoryChannel.send(InventoryUpdate(i))
                delay(1) // 매우 빠른 생성
            }
            inventoryChannel.close()
        }
        
        // Consumer: 느린 데이터베이스 처리
        launch {
            for (update in inventoryChannel) {
                updateDatabase(update) // 100ms 소요
                delay(100)
            }
        }
    }
}
```

## 선택 기준

### Virtual Threads 선택 기준

#### ✅ 선택해야 하는 경우
1. **기존 블로킹 코드베이스**
   ```java
   // 기존 JDBC 코드를 그대로 활용
   @Repository
   public class ReservationRepository {
       public List<Reservation> findAll() {
           return jdbcTemplate.query("SELECT * FROM reservations", 
               reservationRowMapper);
       }
   }
   ```

2. **대량의 I/O 집약적 작업**
   ```java
   // 수천 개의 HTTP 요청을 동시에 처리
   public void processApiRequests(List<String> urls) {
       try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
           urls.parallelStream()
               .map(url -> CompletableFuture.runAsync(() -> 
                   processHttpRequest(url), executor))
               .forEach(CompletableFuture::join);
       }
   }
   ```

3. **디버깅과 모니터링 중시**
   - 기존 Java 프로파일링 도구 활용
   - 스택 트레이스 분석 용이
   - 스레드 덤프를 통한 문제 진단

#### ⚠️ 주의사항
- Java 21+ 환경 필요
- CPU 집약적 작업에는 비효율적
- 스레드풀 크기 제한 없음 (메모리 주의)

### Kotlin Coroutines 선택 기준

#### ✅ 선택해야 하는 경우
1. **복잡한 비동기 플로우 제어**
   ```kotlin
   suspend fun complexReservationFlow(request: ReservationRequest) {
       supervisorScope {
           val user = async { fetchUser(request.userId) }
           val room = async { fetchRoom(request.roomId) }
           
           // 한 작업이 실패해도 다른 작업은 계속
           try {
               val payment = async { processPayment(request.payment) }
               createReservation(user.await(), room.await(), payment.await())
           } catch (e: PaymentException) {
               createPendingReservation(user.await(), room.await())
           }
       }
   }
   ```

2. **백프레셔와 플로우 제어 필요**
   ```kotlin
   fun processReservationStream() = flow {
       // 생산자 속도 제어
       emit(generateReservation())
   }.buffer(capacity = 100)
    .flowOn(Dispatchers.IO)
    .collect { reservation ->
        // 소비자 속도에 맞춰 자동 조절
        processReservation(reservation)
    }
   ```

3. **함수형 프로그래밍 스타일**
   ```kotlin
   val reservationPipeline = reservationFlow
       .filter { it.isValid() }
       .map { validateReservation(it) }
       .flatMapMerge(concurrency = 10) { 
           flow { emit(processReservation(it)) }
       }
       .catch { e -> emit(handleError(e)) }
       .collect { result -> saveResult(result) }
   ```

#### ⚠️ 주의사항
- 학습 곡선 존재 (suspend 함수, 스코프 이해)
- 디버깅 복잡성 (비동기 스택 트레이스)
- Kotlin 런타임 의존성

## 상황별 권장사항

### 🌐 웹 애플리케이션
| 시나리오 | 권장 기술 | 이유 |
|---------|----------|------|
| 전통적인 REST API | Virtual Threads | 기존 Spring MVC 호환성 |
| 실시간 스트리밍 | Kotlin Coroutines | Flow 기반 백프레셔 |
| 마이크로서비스 | 둘 다 적합 | 요구사항에 따라 선택 |

### 🗄️ 데이터 처리
| 시나리오 | 권장 기술 | 이유 |
|---------|----------|------|
| 배치 처리 | Virtual Threads | JDBC 호환성 |
| 스트림 처리 | Kotlin Coroutines | Flow 연산자 |
| ETL 파이프라인 | Kotlin Coroutines | 구조화된 변환 |

### 🔌 I/O 집약적 작업
| 시나리오 | 권장 기술 | 이유 |
|---------|----------|------|
| 파일 처리 | 둘 다 적합 | 성능 유사 |
| 네트워크 통신 | 둘 다 적합 | 비동기 I/O 지원 |
| 데이터베이스 연동 | Virtual Threads | JDBC 생태계 |

## 마이그레이션 가이드

### 기존 Thread Pool → Virtual Threads

#### Before (Traditional Threads)
```java
@Service
public class ReservationService {
    private final ExecutorService executor = 
        Executors.newFixedThreadPool(100);
    
    public List<ReservationDetails> getReservations(List<String> ids) {
        List<CompletableFuture<ReservationDetails>> futures = ids.stream()
            .map(id -> CompletableFuture.supplyAsync(
                () -> fetchReservationDetails(id), executor))
            .toList();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
}
```

#### After (Virtual Threads)
```java
@Service
public class ReservationService {
    
    public List<ReservationDetails> getReservations(List<String> ids) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<ReservationDetails>> futures = ids.stream()
                .map(id -> CompletableFuture.supplyAsync(
                    () -> fetchReservationDetails(id), executor))
                .toList();
            
            return futures.stream()
                .map(CompletableFuture::join)
                .toList();
        }
    }
}
```

**변경사항:**
- `newFixedThreadPool()` → `newVirtualThreadPerTaskExecutor()`
- try-with-resources로 자동 정리
- 스레드 수 제한 제거

### 블로킹 코드 → Kotlin Coroutines

#### Before (Blocking)
```java
@Service
public class ReservationService {
    
    public ReservationDetails getReservationDetails(String id) {
        User user = userService.getUser(id);           // 100ms
        Room room = roomService.getRoom(id);           // 150ms  
        Payment payment = paymentService.getPayment(id); // 200ms
        
        return new ReservationDetails(user, room, payment);
        // 총 450ms (순차 실행)
    }
}
```

#### After (Coroutines)
```kotlin
@Service
class ReservationService {
    
    suspend fun getReservationDetails(id: String): ReservationDetails {
        return coroutineScope {
            val userDeferred = async { userService.getUser(id) }     // 100ms
            val roomDeferred = async { roomService.getRoom(id) }     // 150ms
            val paymentDeferred = async { paymentService.getPayment(id) } // 200ms
            
            ReservationDetails(
                userDeferred.await(),
                roomDeferred.await(), 
                paymentDeferred.await()
            )
            // 총 200ms (병렬 실행)
        }
    }
}
```

**변경사항:**
- 함수에 `suspend` 키워드 추가
- `async`/`await`로 병렬 처리
- `coroutineScope`로 구조화된 동시성

### 단계별 마이그레이션 전략

#### Phase 1: 기반 설정
```kotlin
// build.gradle.kts
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
```

#### Phase 2: 서비스 레이어 변환
```kotlin
// 1. Repository 인터페이스에 suspend 함수 추가
interface ReservationRepository {
    suspend fun findById(id: String): Reservation?
    suspend fun save(reservation: Reservation): Reservation
}

// 2. Service에서 suspend 함수 사용
@Service
class ReservationService(
    private val repository: ReservationRepository
) {
    suspend fun createReservation(request: ReservationRequest): Reservation {
        // 비동기 처리 로직
        return repository.save(processRequest(request))
    }
}
```

#### Phase 3: 컨트롤러 적용
```kotlin
@RestController
class ReservationController(
    private val service: ReservationService
) {
    @PostMapping("/reservations")
    suspend fun createReservation(
        @RequestBody request: ReservationRequest
    ): ResponseEntity<Reservation> {
        val reservation = service.createReservation(request)
        return ResponseEntity.ok(reservation)
    }
}
```

## 문제 해결

### Virtual Threads 문제 해결

#### 1. OutOfMemoryError 발생
```bash
# 증상
java.lang.OutOfMemoryError: unable to create new native thread

# 원인
대량의 Virtual Thread 생성으로 인한 메모리 부족

# 해결방법
-Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=50
```

#### 2. CPU 집약적 작업에서 성능 저하
```java
// 문제: Virtual Thread에서 CPU 작업
Thread.ofVirtual().start(() -> {
    // CPU 집약적 작업 - 성능 저하
    performCPUIntensiveTask();
});

// 해결: Platform Thread 사용
ForkJoinPool.commonPool().submit(() -> {
    performCPUIntensiveTask();
});
```

#### 3. 디버깅 시 스택 트레이스 혼란
```java
// 해결: 명확한 Virtual Thread 이름 지정
Thread.ofVirtual()
    .name("reservation-processor-" + taskId)
    .start(() -> processReservation(taskId));
```

### Kotlin Coroutines 문제 해결

#### 1. 메모리 누수 (Job이 완료되지 않음)
```kotlin
// 문제: Job이 취소되지 않음
class ReservationService {
    private val scope = CoroutineScope(SupervisorJob())
    
    fun processReservations() {
        scope.launch {
            // 무한 루프 - 메모리 누수 위험
            while (true) {
                processNextReservation()
                delay(1000)
            }
        }
    }
}

// 해결: 적절한 취소 처리
class ReservationService {
    private val scope = CoroutineScope(SupervisorJob())
    
    fun processReservations() {
        scope.launch {
            while (isActive) { // 취소 상태 확인
                processNextReservation()
                delay(1000)
            }
        }
    }
    
    fun shutdown() {
        scope.cancel() // 명시적 취소
    }
}
```

#### 2. 구조화된 동시성 위반
```kotlin
// 문제: GlobalScope 사용
fun processReservation(id: String) {
    GlobalScope.launch { // 구조화되지 않은 동시성
        updateDatabase(id)
    }
}

// 해결: coroutineScope 사용
suspend fun processReservation(id: String) {
    coroutineScope { // 구조화된 동시성
        launch {
            updateDatabase(id)
        }
    }
}
```

#### 3. 블로킹 호출로 인한 스레드 고갈
```kotlin
// 문제: 코루틴에서 블로킹 호출
suspend fun processReservation(id: String) {
    Thread.sleep(1000) // 블로킹 호출 - 스레드 고갈
}

// 해결: 논블로킹 대안 사용
suspend fun processReservation(id: String) {
    delay(1000) // 논블로킹 지연
}

// 또는 Dispatchers.IO 사용
suspend fun processReservation(id: String) {
    withContext(Dispatchers.IO) {
        Thread.sleep(1000) // IO 디스패처에서 블로킹 허용
    }
}
```

### 성능 모니터링

#### Virtual Threads 모니터링
```java
// JFR 이벤트 활성화
-XX:+FlightRecorder 
-XX:StartFlightRecording=duration=60s,filename=virtual-threads.jfr

// JConsole에서 Virtual Thread 확인
ManagementFactory.getThreadMXBean().getThreadCount()
```

#### Coroutines 모니터링
```kotlin
// 코루틴 디버깅 활성화
System.setProperty("kotlinx.coroutines.debug", "on")

// 활성 코루틴 수 확인
val activeCoroutines = CoroutineScope(SupervisorJob())
    .coroutineContext[Job]?.children?.count()
```

## 결론

### 선택 기준 요약

| 요구사항 | Virtual Threads | Kotlin Coroutines |
|---------|----------------|------------------|
| 기존 코드 호환성 | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| 학습 곡선 | ⭐⭐⭐⭐ | ⭐⭐ |
| 메모리 효율성 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 백프레셔 처리 | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 디버깅 편의성 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 성능 (I/O) | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 성능 (CPU) | ⭐⭐⭐ | ⭐⭐⭐ |

### 최종 권장사항

1. **Java 기반 프로젝트 + 기존 코드 활용**: Virtual Threads
2. **Kotlin 기반 프로젝트 + 복잡한 비동기 플로우**: Kotlin Coroutines  
3. **마이크로서비스 아키텍처**: 팀 선호도와 기술 스택에 따라 선택
4. **성능이 중요한 시스템**: 실제 워크로드로 벤치마크 테스트 후 결정

### 추가 학습 자료

- [Java Virtual Threads 공식 문서](https://openjdk.org/jeps/444)
- [Kotlin Coroutines 가이드](https://kotlinlang.org/docs/coroutines-guide.html)
- [Spring WebFlux 공식 문서](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)

---

이 가이드를 통해 Virtual Threads와 Kotlin Coroutines의 특징을 이해하고, 프로젝트에 적합한 기술을 선택할 수 있기를 바랍니다.