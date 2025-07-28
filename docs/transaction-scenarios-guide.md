# ⚡ 트랜잭션 시나리오 성능 가이드

## 📋 개요

이 가이드는 JPA와 R2DBC를 사용한 다양한 트랜잭션 시나리오에서의 성능 비교와 최적화 전략을 다룹니다. 실무 환경에서 발생하는 복잡한 트랜잭션 처리 요구사항에 대한 기술적 선택과 구현 방법을 제시합니다.

## 🎯 주요 학습 목표

- **트랜잭션 처리 패턴**: 단순 CRUD부터 복잡한 비즈니스 로직까지
- **중첩 트랜잭션**: PROPAGATION 전략과 성능 영향
- **롤백 메커니즘**: 실패 시나리오에서의 복구 전략
- **동시성 제어**: 데드락 방지와 성능 최적화
- **격리 수준**: ACID 속성과 성능 트레이드오프

## 🏗️ 아키텍처 개요

### 테스트 환경 구성

```kotlin
@Component
class TransactionScenarioComparator(
    private val jpaRepository: ReservationRepository,
    private val r2dbcRepository: ReservationRepositoryReactive,
    private val reservationService: ReservationService,
    private val transactionTemplate: TransactionTemplate
) : CommandLineRunner
```

### 주요 구성 요소

1. **TransactionScenarioComparator**: 종합적인 트랜잭션 성능 비교 도구
2. **transaction-scenarios-test.sh**: 자동화된 트랜잭션 테스트 스크립트
3. **시스템 모니터링**: 실시간 리소스 추적 및 성능 분석

## 📊 트랜잭션 시나리오 분석

### 1. 단순 CRUD 트랜잭션

#### JPA 단순 CRUD 구현
```kotlin
private fun testJpaSimpleCrud(count: Int): TransactionTestResult {
    val executionTime = measureTimeMillis {
        repeat(count) { i ->
            transactionTemplate.execute { _ ->
                // Create
                val reservation = createTransactionTestReservation(i)
                val saved = jpaRepository.save(reservation)
                
                // Read
                val found = jpaRepository.findById(saved.id).orElse(null)
                
                // Update
                if (found != null) {
                    val updated = found.copy(
                        guestName = "Updated ${found.guestName}",
                        totalAmount = found.totalAmount + 10.0
                    )
                    jpaRepository.save(updated)
                }
                
                true
            }
        }
    }
    // 메트릭 수집 및 반환
}
```

#### R2DBC 단순 CRUD 구현
```kotlin
private fun testR2dbcSimpleCrud(count: Int): TransactionTestResult {
    val executionTime = measureTimeMillis {
        runBlocking {
            repeat(count) { i ->
                // 리액티브 트랜잭션 처리
                val reservation = createTransactionTestReservation(i)
                
                // Create
                val saved = r2dbcRepository.save(reservation).awaitSingle()
                
                // Read
                val found = r2dbcRepository.findById(saved.id).awaitSingleOrNull()
                
                // Update
                if (found != null) {
                    val updated = found.copy(
                        guestName = "Updated ${found.guestName}",
                        totalAmount = found.totalAmount + 10.0
                    )
                    r2dbcRepository.save(updated).awaitSingle()
                }
            }
        }
    }
}
```

**성능 특성:**
- **JPA**: 트랜잭션 경계 명확, ACID 보장
- **R2DBC**: 높은 처리량, 메모리 효율성

### 2. 복잡한 비즈니스 로직 트랜잭션

#### JPA 복잡 비즈니스 로직
```kotlin
private fun testJpaComplexBusiness(count: Int): TransactionTestResult {
    repeat(count) { i ->
        transactionTemplate.execute { status ->
            try {
                // 1. 기존 예약 조회 및 검증
                val existingReservations = jpaRepository.findByGuestEmailAndStatusIn(
                    "business$i@test.com",
                    listOf(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN)
                )
                
                // 2. 비즈니스 규칙 검증
                val checkInDate = LocalDate.now().plusDays(Random.nextLong(1, 30))
                val hasConflict = existingReservations.any { 
                    it.checkInDate == checkInDate 
                }
                
                if (hasConflict && Random.nextDouble() > 0.8) {
                    throw RuntimeException("중복 예약 불가")
                }
                
                // 3. 새 예약 생성
                val newReservation = createTransactionTestReservation(i).copy(
                    guestEmail = "business$i@test.com",
                    checkInDate = checkInDate
                )
                val savedReservation = jpaRepository.save(newReservation)
                
                // 4. 관련 데이터 업데이트
                existingReservations.forEach { existing ->
                    if (existing.status == ReservationStatus.PENDING) {
                        val updated = existing.copy(
                            status = ReservationStatus.CONFIRMED,
                            totalAmount = existing.totalAmount * 0.95 // 5% 할인
                        )
                        jpaRepository.save(updated)
                    }
                }
                
                // 5. 통계 업데이트
                val totalReservations = jpaRepository.count()
                if (totalReservations % 100 == 0L) {
                    Thread.sleep(1) // I/O 지연 시뮬레이션
                }
                
                true
            } catch (e: Exception) {
                status.setRollbackOnly()
                throw e
            }
        }
    }
}
```

#### R2DBC 복잡 비즈니스 로직
```kotlin
private fun testR2dbcComplexBusiness(count: Int): TransactionTestResult {
    runBlocking {
        repeat(count) { i ->
            // 1. 기존 예약 조회
            val existingReservations = r2dbcRepository.findAll()
                .filter { it.guestEmail == "business$i@test.com" }
                .filter { it.status in listOf(ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN) }
                .collectList()
                .awaitSingle()
            
            // 2. 비즈니스 규칙 검증
            val checkInDate = LocalDate.now().plusDays(Random.nextLong(1, 30))
            val hasConflict = existingReservations.any { it.checkInDate == checkInDate }
            
            if (hasConflict && Random.nextDouble() > 0.8) {
                throw RuntimeException("중복 예약 불가")
            }
            
            // 3. 새 예약 생성
            val newReservation = createTransactionTestReservation(i).copy(
                guestEmail = "business$i@test.com",
                checkInDate = checkInDate
            )
            r2dbcRepository.save(newReservation).awaitSingle()
            
            // 4. 병렬 업데이트
            val updateTasks = existingReservations
                .filter { it.status == ReservationStatus.PENDING }
                .map { existing ->
                    async {
                        val updated = existing.copy(
                            status = ReservationStatus.CONFIRMED,
                            totalAmount = existing.totalAmount * 0.95
                        )
                        r2dbcRepository.save(updated).awaitSingle()
                    }
                }
            updateTasks.awaitAll()
            
            // 5. 비동기 통계 업데이트
            val totalCount = r2dbcRepository.count().awaitSingle()
            if (totalCount % 100 == 0L) {
                delay(1) // 비동기 지연
            }
        }
    }
}
```

**성능 특성:**
- **JPA**: 강력한 트랜잭션 관리, 복잡한 롤백 처리
- **R2DBC**: 병렬 처리, 비동기 I/O 최적화

### 3. 중첩 트랜잭션

#### JPA 중첩 트랜잭션 (PROPAGATION_REQUIRES_NEW)
```kotlin
@Transactional(propagation = Propagation.REQUIRED)
private fun testJpaNestedTransactions(): TransactionTestResult {
    repeat(count) { i ->
        transactionTemplate.execute { _ ->
            // 외부 트랜잭션
            val outerReservation = createTransactionTestReservation(i)
            jpaRepository.save(outerReservation)
            
            try {
                // 내부 트랜잭션 (REQUIRES_NEW)
                executeNestedTransaction(i)
                
                // 30% 확률로 외부 트랜잭션 롤백
                if (Random.nextDouble() > 0.7) {
                    throw RuntimeException("외부 트랜잭션 롤백")
                }
            } catch (e: Exception) {
                // 외부 트랜잭션만 롤백, 내부 트랜잭션은 독립적으로 커밋됨
                throw e
            }
        }
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
private fun executeNestedTransaction(index: Int) {
    val innerReservation = createTransactionTestReservation(index).copy(
        guestName = "Nested Guest $index"
    )
    jpaRepository.save(innerReservation)
    
    // 20% 확률로 내부 트랜잭션 롤백
    if (Random.nextDouble() > 0.8) {
        throw RuntimeException("내부 트랜잭션 롤백")
    }
}
```

#### R2DBC 중첩 트랜잭션 시뮬레이션
```kotlin
private fun testR2dbcNestedTransactions(): TransactionTestResult {
    runBlocking {
        repeat(count) { i ->
            // R2DBC에서 중첩 트랜잭션 시뮬레이션
            val outerReservation = createTransactionTestReservation(i)
            r2dbcRepository.save(outerReservation).awaitSingle()
            
            try {
                // 논리적 중첩 처리
                val innerReservation = createTransactionTestReservation(i).copy(
                    guestName = "Nested Guest $i"
                )
                r2dbcRepository.save(innerReservation).awaitSingle()
                
                // 실패 시뮬레이션
                if (Random.nextDouble() > 0.8) {
                    throw RuntimeException("내부 작업 실패")
                }
                
                if (Random.nextDouble() > 0.7) {
                    throw RuntimeException("외부 작업 실패")
                }
            } catch (e: Exception) {
                // R2DBC는 실제 중첩 트랜잭션 지원이 제한적
                throw e
            }
        }
    }
}
```

**중첩 트랜잭션 특성:**
- **JPA**: 완전한 PROPAGATION 지원, 독립적 커밋/롤백
- **R2DBC**: 제한적 지원, 논리적 중첩으로 구현

### 4. 롤백 시나리오

#### 높은 롤백 확률 시나리오
```kotlin
private fun testHighRollbackScenario(technology: String, count: Int): TransactionTestResult {
    if (technology == "JPA") {
        repeat(count) { i ->
            transactionTemplate.execute { status ->
                try {
                    // 여러 작업 수행
                    val reservation1 = createTransactionTestReservation(i)
                    jpaRepository.save(reservation1)
                    
                    val reservation2 = createTransactionTestReservation(i + 1000)
                    jpaRepository.save(reservation2)
                    
                    // 50% 확률로 의도적 롤백
                    if (Random.nextDouble() > 0.5) {
                        throw RuntimeException("의도적 롤백")
                    }
                    
                    true
                } catch (e: Exception) {
                    status.setRollbackOnly()
                    throw e
                }
            }
        }
    } else {
        // R2DBC 롤백 시뮬레이션
        runBlocking {
            repeat(count) { i ->
                try {
                    val reservation1 = createTransactionTestReservation(i)
                    r2dbcRepository.save(reservation1).awaitSingle()
                    
                    val reservation2 = createTransactionTestReservation(i + 1000)
                    r2dbcRepository.save(reservation2).awaitSingle()
                    
                    // 50% 확률로 롤백
                    if (Random.nextDouble() > 0.5) {
                        throw RuntimeException("의도적 롤백")
                    }
                } catch (e: Exception) {
                    // R2DBC는 명시적 롤백 처리 필요
                    throw e
                }
            }
        }
    }
}
```

**롤백 처리 특성:**
- **JPA**: 완전한 롤백 메커니즘, 자동 상태 복원
- **R2DBC**: 수동 롤백 처리, 상태 관리 복잡

### 5. 동시 접근 트랜잭션

#### JPA 동시 접근 처리
```kotlin
private fun testConcurrentJpaTransactions(concurrency: Int): TransactionTestResult {
    runBlocking {
        val jobs = (1..concurrency).map { threadId ->
            async(Dispatchers.IO) {
                repeat(transactionsPerThread) { i ->
                    transactionTemplate.execute { _ ->
                        // 공유 리소스 경합 시뮬레이션
                        val sharedResourceId = (i % 10) + 1
                        
                        // 기존 예약 조회 (공유 리소스)
                        val existing = jpaRepository.findAll()
                            .firstOrNull { it.roomNumber == "SharedRoom$sharedResourceId" }
                        
                        if (existing != null) {
                            // 업데이트 (경합 상황)
                            val updated = existing.copy(
                                totalAmount = existing.totalAmount + threadId
                            )
                            jpaRepository.save(updated)
                        } else {
                            // 새 예약 생성
                            val newReservation = createTransactionTestReservation(threadId * 1000 + i)
                                .copy(roomNumber = "SharedRoom$sharedResourceId")
                            jpaRepository.save(newReservation)
                        }
                        
                        // 경합 상황 유도
                        Thread.sleep(Random.nextLong(1, 5))
                        
                        true
                    }
                }
            }
        }
        jobs.awaitAll()
    }
}
```

#### R2DBC 동시 접근 처리
```kotlin
private fun testConcurrentR2dbcTransactions(concurrency: Int): TransactionTestResult {
    runBlocking {
        val jobs = (1..concurrency).map { threadId ->
            async {
                repeat(transactionsPerThread) { i ->
                    // 비관적 락 시뮬레이션을 위한 원자적 조회-수정
                    val sharedResourceId = (i % 10) + 1
                    
                    val existing = r2dbcRepository.findAll()
                        .filter { it.roomNumber == "SharedRoom$sharedResourceId" }
                        .take(1)
                        .singleOrDefault(null)
                        .awaitSingleOrNull()
                    
                    if (existing != null) {
                        val updated = existing.copy(
                            totalAmount = existing.totalAmount + threadId
                        )
                        r2dbcRepository.save(updated).awaitSingle()
                    } else {
                        val newReservation = createTransactionTestReservation(threadId * 1000 + i)
                            .copy(roomNumber = "SharedRoom$sharedResourceId")
                        r2dbcRepository.save(newReservation).awaitSingle()
                    }
                    
                    // 비동기 지연
                    delay(Random.nextLong(1, 5))
                }
            }
        }
        jobs.awaitAll()
    }
}
```

**동시성 처리 특성:**
- **JPA**: 데드락 위험성, 명시적 락 관리 필요
- **R2DBC**: 비동기 처리로 경합 감소, 논블로킹 I/O

### 6. 격리 수준별 성능

#### 격리 수준 테스트
```kotlin
private fun testJpaIsolationLevel(isolation: Isolation): TransactionTestResult {
    repeat(count) { i ->
        // 격리 수준별 트랜잭션 실행
        transactionTemplate.execute { _ ->
            val reservation = createTransactionTestReservation(i)
            jpaRepository.save(reservation)
            
            // 읽기 작업으로 격리 수준 효과 시뮬레이션
            val readReservations = jpaRepository.findAll().take(10)
            readReservations.forEach { r ->
                val _ = r.guestName.length // 데이터 접근
            }
            
            // 격리 수준에 따른 지연 시뮬레이션
            val delay = when (isolation) {
                Isolation.READ_COMMITTED -> 1L
                Isolation.REPEATABLE_READ -> 2L
                Isolation.SERIALIZABLE -> 5L
                else -> 1L
            }
            Thread.sleep(delay)
            
            true
        }
    }
}
```

**격리 수준별 특성:**

| 격리 수준 | 팬텀 리드 | 반복 불가능 읽기 | 더티 리드 | 성능 오버헤드 |
|-----------|-----------|------------------|-----------|---------------|
| READ_UNCOMMITTED | 발생 | 발생 | 발생 | 매우 낮음 |
| READ_COMMITTED | 발생 | 발생 | 방지 | 낮음 |
| REPEATABLE_READ | 발생 | 방지 | 방지 | 중간 |
| SERIALIZABLE | 방지 | 방지 | 방지 | 높음 |

### 7. 배치 처리 트랜잭션

#### JPA 배치 처리
```kotlin
@Transactional
private fun testJpaBatchProcessing(batchSize: Int): TransactionTestResult {
    repeat(batchCount) { batchIndex ->
        transactionTemplate.execute { _ ->
            // 배치 생성
            val batch = (1..batchSize).map { i ->
                createTransactionTestReservation(batchIndex * batchSize + i)
            }
            
            // JPA 배치 저장
            jpaRepository.saveAll(batch)
            
            // 배치 업데이트
            val savedBatch = jpaRepository.findAll().takeLast(batchSize)
            val updatedBatch = savedBatch.map { reservation ->
                reservation.copy(
                    guestName = "Batch ${reservation.guestName}",
                    totalAmount = reservation.totalAmount + 5.0
                )
            }
            jpaRepository.saveAll(updatedBatch)
            
            true
        }
    }
}
```

#### R2DBC 배치 처리
```kotlin
private fun testR2dbcBatchProcessing(batchSize: Int): TransactionTestResult {
    runBlocking {
        repeat(batchCount) { batchIndex ->
            val batch = (1..batchSize).map { i ->
                createTransactionTestReservation(batchIndex * batchSize + i)
            }
            
            // R2DBC 배치 저장
            Flux.fromIterable(batch)
                .flatMap { r2dbcRepository.save(it) }
                .collectList()
                .awaitSingle()
            
            // 배치 업데이트
            val savedBatch = r2dbcRepository.findAll()
                .takeLast(batchSize)
                .collectList()
                .awaitSingle()
            
            val updatedBatch = savedBatch.map { reservation ->
                reservation.copy(
                    guestName = "Batch ${reservation.guestName}",
                    totalAmount = reservation.totalAmount + 5.0
                )
            }
            
            Flux.fromIterable(updatedBatch)
                .flatMap { r2dbcRepository.save(it) }
                .collectList()
                .awaitSingle()
        }
    }
}
```

**배치 처리 특성:**
- **JPA**: 배치 크기 최적화, 메모리 관리 중요
- **R2DBC**: 스트리밍 배치, 백프레셔 활용

## 🚀 사용 방법

### 1. 명령행 도구 실행

```bash
# 전체 트랜잭션 시나리오 테스트
./scripts/transaction-scenarios-test.sh full --build --report

# 복잡한 비즈니스 로직만 테스트
./scripts/transaction-scenarios-test.sh complex --monitor

# 동시 접근 트랜잭션 테스트
./scripts/transaction-scenarios-test.sh concurrent --clean
```

### 2. 프로그래매틱 실행

```kotlin
@Autowired
private lateinit var transactionComparator: TransactionScenarioComparator

fun runTransactionTest() {
    transactionComparator.runTransactionScenarioComparison()
}
```

### 3. Spring Boot 애플리케이션 실행

```bash
./gradlew bootRun --args="--transaction-scenarios --mode=complex"
```

## 📊 성능 메트릭

### 주요 측정 지표

```kotlin
data class TransactionTestResult(
    val scenarioName: String,
    val technology: String,
    val transactionCount: Int,
    val executionTimeMs: Long,
    val successfulTransactions: Int,
    val failedTransactions: Int,
    val rollbackCount: Int,
    val averageTransactionTimeMs: Double,
    val transactionsPerSecond: Double,
    val memoryUsedMB: Long,
    val isolationLevel: String,
    val propagationBehavior: String,
    val concurrencyLevel: Int = 1,
    val deadlockCount: Int = 0
) {
    fun getSuccessRate(): Double = 
        if (transactionCount > 0) (successfulTransactions.toDouble() / transactionCount) * 100 else 0.0
    
    fun getPerformanceScore(): Double {
        val successScore = getSuccessRate() * 0.4  // 40% 가중치
        val speedScore = minOf(transactionsPerSecond / 100.0, 1.0) * 100 * 0.3  // 30% 가중치
        val stabilityScore = maxOf(0.0, 1.0 - (failedTransactions.toDouble() / transactionCount)) * 100 * 0.3  // 30% 가중치
        return successScore + speedScore + stabilityScore
    }
}
```

### 성능 분석 예시

```
📊 JPA:
  트랜잭션 수: 1000개
  실행 시간: 5500ms
  성공률: 95.0% (950/1000)
  처리량: 172.7 tx/sec
  평균 트랜잭션 시간: 5.79ms
  롤백 수: 25개
  성능 점수: 87.5/100

📊 R2DBC:
  트랜잭션 수: 1000개
  실행 시간: 3200ms
  성공률: 98.5% (985/1000)
  처리량: 307.8 tx/sec
  평균 트랜잭션 시간: 3.25ms
  롤백 수: 15개
  성능 점수: 94.2/100
```

## 🎯 트랜잭션 최적화 전략

### JPA 최적화

#### 1. 트랜잭션 전파 설정
```kotlin
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
class ReservationService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processIndependentOperation() {
        // 독립적인 트랜잭션 필요한 작업
    }
    
    @Transactional(readOnly = true)
    fun getReservationStatistics() {
        // 읽기 전용 트랜잭션
    }
}
```

#### 2. 배치 처리 최적화
```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 100
        order_inserts: true
        order_updates: true
        batch_versioned_data: true
```

#### 3. 연결 풀 최적화
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
```

### R2DBC 최적화

#### 1. 백프레셔 전략
```kotlin
fun processTransactionsWithBackpressure(): Flux<TransactionResult> {
    return r2dbcRepository.findAll()
        .buffer(100) // 100개씩 묶어서 처리
        .flatMap { batch ->
            processBatch(batch)
                .onErrorResume { error ->
                    logger.error("Batch processing failed", error)
                    Mono.empty()
                }
        }
        .onBackpressureBuffer(1000) // 백프레셔 버퍼 설정
}
```

#### 2. 연결 풀 설정
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 30
      max-idle-time: 30m
      validation-query: SELECT 1
```

#### 3. 리액티브 트랜잭션
```kotlin
@Service
class ReactiveReservationService {
    
    @Transactional
    fun processReservation(request: ReservationRequest): Mono<Reservation> {
        return validateRequest(request)
            .flatMap { validRequest -> 
                r2dbcRepository.save(createReservation(validRequest))
            }
            .flatMap { savedReservation ->
                updateRelatedData(savedReservation)
                    .thenReturn(savedReservation)
            }
    }
}
```

## 📈 시나리오별 선택 가이드

### 단순 CRUD 트랜잭션

**사용 사례:**
- 기본적인 데이터 입력/수정/삭제
- 단순한 비즈니스 규칙
- 높은 처리량 요구

**권장 기술:**
- **높은 처리량 우선**: R2DBC
- **안정성 우선**: JPA
- **기존 시스템 호환**: JPA

```kotlin
// 성능 우선 (R2DBC)
suspend fun createSimpleReservation(request: CreateReservationRequest): Reservation {
    return r2dbcRepository.save(request.toEntity()).awaitSingle()
}

// 안정성 우선 (JPA)
@Transactional
fun createReservationWithValidation(request: CreateReservationRequest): Reservation {
    validateBusinessRules(request)
    return jpaRepository.save(request.toEntity())
}
```

### 복잡한 비즈니스 로직

**사용 사례:**
- 여러 엔티티 간 복잡한 상호작용
- 복잡한 검증 규칙
- 다단계 승인 프로세스

**권장 기술:**
- **복잡한 트랜잭션**: JPA (우선)
- **이벤트 기반 처리**: R2DBC

```kotlin
// 복잡한 비즈니스 로직 (JPA)
@Transactional
fun processComplexReservation(request: ComplexReservationRequest): ReservationResult {
    return transactionTemplate.execute { status ->
        try {
            val validation = validateComplexRules(request)
            if (!validation.isValid) {
                status.setRollbackOnly()
                return@execute ReservationResult.failed(validation.errors)
            }
            
            val reservation = createReservation(request)
            updateInventory(reservation)
            sendNotifications(reservation)
            updateStatistics(reservation)
            
            ReservationResult.success(reservation)
        } catch (e: Exception) {
            status.setRollbackOnly()
            throw e
        }
    }
}
```

### 중첩 트랜잭션

**사용 사례:**
- 독립적인 하위 작업
- 감사 로그 기록
- 외부 시스템 연동

**권장 기술:**
- **중첩 트랜잭션 필수**: JPA
- **논리적 분리**: R2DBC

```kotlin
// JPA 중첩 트랜잭션
@Service
class ReservationService {
    
    @Transactional
    fun processReservationWithAudit(request: ReservationRequest): Reservation {
        val reservation = processReservation(request)
        
        // 독립적인 감사 로그 (실패해도 메인 트랜잭션에 영향 없음)
        auditService.logReservationCreated(reservation)
        
        return reservation
    }
}

@Service
class AuditService {
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logReservationCreated(reservation: Reservation) {
        val auditLog = AuditLog(
            action = "RESERVATION_CREATED",
            entityId = reservation.id,
            timestamp = LocalDateTime.now()
        )
        auditRepository.save(auditLog)
    }
}
```

### 높은 동시성 처리

**사용 사례:**
- 대용량 동시 접근
- 실시간 예약 시스템
- 이벤트 티켓 예매

**권장 기술:**
- **높은 동시성**: R2DBC (우선)
- **데이터 일관성 중시**: JPA + 낙관적 락

```kotlin
// R2DBC 높은 동시성 처리
@Service
class HighConcurrencyReservationService {
    
    fun processReservationsConcurrently(requests: List<ReservationRequest>): Flux<ReservationResult> {
        return Flux.fromIterable(requests)
            .flatMap { request ->
                processReservation(request)
                    .onErrorResume { error ->
                        Mono.just(ReservationResult.failed(error.message))
                    }
            }
            .parallel(10) // 10개 병렬 처리
            .runOn(Schedulers.parallel())
            .sequential()
    }
}

// JPA 낙관적 락 활용
@Entity
class Reservation {
    @Version
    private val version: Long = 0
    
    // 다른 필드들
}

@Transactional
fun updateReservationWithOptimisticLock(id: Long, updateRequest: UpdateRequest): Reservation {
    val reservation = jpaRepository.findById(id)
        .orElseThrow { EntityNotFoundException("Reservation not found") }
    
    // 낙관적 락으로 동시성 제어
    return jpaRepository.save(reservation.update(updateRequest))
}
```

## 💾 메모리 사용 패턴

### JPA 메모리 최적화

```kotlin
// 스트림 처리로 메모리 사용량 제한
@Transactional(readOnly = true)
fun processLargeDataset() {
    entityManager.createQuery("FROM Reservation", Reservation::class.java)
        .setHint(QueryHints.HINT_FETCH_SIZE, 1000)
        .resultStream
        .use { stream ->
            stream.forEach { reservation ->
                processReservation(reservation)
                
                // 주기적으로 영속성 컨텍스트 클리어
                if (processedCount % 1000 == 0) {
                    entityManager.clear()
                }
            }
        }
}
```

### R2DBC 메모리 최적화

```kotlin
// 백프레셔와 버퍼링으로 메모리 제어
fun processReservationsWithBackpressure(): Flux<ProcessedReservation> {
    return r2dbcRepository.findAll()
        .buffer(500) // 500개씩 버퍼링
        .concatMap { batch ->
            Flux.fromIterable(batch)
                .flatMap { reservation ->
                    processReservation(reservation)
                        .subscribeOn(Schedulers.boundedElastic())
                }
                .collectList()
                .flatMapMany { Flux.fromIterable(it) }
        }
        .onBackpressureBuffer(2000, { dropped ->
            logger.warn("Dropped reservation: ${dropped.id}")
        }, BufferOverflowStrategy.DROP_OLDEST)
}
```

## 🔍 모니터링 및 진단

### 트랜잭션 모니터링

```kotlin
@Component
class TransactionMonitor {
    
    @EventListener
    fun onTransactionStart(event: TransactionStartEvent) {
        val startTime = System.currentTimeMillis()
        TransactionContext.setStartTime(startTime)
        
        logger.info("Transaction started: {}", event.transactionName)
    }
    
    @EventListener
    fun onTransactionEnd(event: TransactionEndEvent) {
        val startTime = TransactionContext.getStartTime()
        val duration = System.currentTimeMillis() - startTime
        
        Metrics.timer("transaction.duration", 
            Tags.of("type", event.transactionType))
            .record(duration, TimeUnit.MILLISECONDS)
        
        if (duration > 5000) { // 5초 이상 소요 시 경고
            logger.warn("Long running transaction: {} took {}ms", 
                event.transactionName, duration)
        }
    }
}
```

### 성능 메트릭 수집

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: reservation-system
```

```kotlin
@RestController
class TransactionMetricsController {
    
    @GetMapping("/api/metrics/transactions")
    fun getTransactionMetrics(): TransactionMetrics {
        return TransactionMetrics(
            totalTransactions = meterRegistry.counter("transactions.total").count(),
            successfulTransactions = meterRegistry.counter("transactions.successful").count(),
            failedTransactions = meterRegistry.counter("transactions.failed").count(),
            averageTransactionTime = meterRegistry.timer("transactions.duration").mean(TimeUnit.MILLISECONDS),
            rollbackRate = calculateRollbackRate()
        )
    }
}
```

## 🎯 실무 권장사항

### 기술 선택 결정 트리

```
1. 트랜잭션 복잡도가 높은가?
   ├─ Yes → JPA 고려
   └─ No → 2번으로

2. 높은 동시성이 필요한가?
   ├─ Yes → R2DBC 고려
   └─ No → 3번으로

3. 중첩 트랜잭션이 필요한가?
   ├─ Yes → JPA 선택
   └─ No → 4번으로

4. 기존 시스템과의 호환성이 중요한가?
   ├─ Yes → JPA 선택
   └─ No → R2DBC 선택
```

### 하이브리드 접근법

```kotlin
@Service
class HybridReservationService(
    private val jpaService: JpaReservationService,
    private val r2dbcService: R2dbcReservationService
) {
    
    // 복잡한 비즈니스 로직은 JPA
    @Transactional
    fun createComplexReservation(request: ComplexReservationRequest): Reservation {
        return jpaService.processComplexReservation(request)
    }
    
    // 단순한 조회는 R2DBC
    suspend fun findReservations(criteria: SearchCriteria): List<Reservation> {
        return r2dbcService.findReservations(criteria)
    }
    
    // 실시간 업데이트는 R2DBC
    fun subscribeToReservationUpdates(): Flux<ReservationUpdate> {
        return r2dbcService.subscribeToUpdates()
    }
}
```

### 성능 튜닝 체크리스트

#### JPA 성능 튜닝
- [ ] 배치 크기 최적화 (`hibernate.jdbc.batch_size`)
- [ ] 2차 캐시 활용 (`@Cacheable`)
- [ ] N+1 문제 해결 (`@EntityGraph`, Fetch Join)
- [ ] 읽기 전용 트랜잭션 사용 (`@Transactional(readOnly = true)`)
- [ ] 지연 로딩 전략 적용
- [ ] 연결 풀 크기 조정

#### R2DBC 성능 튜닝
- [ ] 백프레셔 전략 구현
- [ ] 적절한 버퍼 크기 설정
- [ ] 커넥션 풀 최적화
- [ ] 논블로킹 I/O 활용
- [ ] 스트리밍 처리 적용
- [ ] 에러 처리 및 재시도 로직

## 🚨 트러블슈팅

### 일반적인 문제와 해결책

#### 1. 트랜잭션 타임아웃
**증상:** `TransactionTimedOutException`

**해결책:**
```yaml
spring:
  transaction:
    default-timeout: 30 # 30초
  jpa:
    properties:
      javax.persistence.query.timeout: 10000 # 10초
```

#### 2. 데드락 발생
**증상:** `DeadlockLoserDataAccessException`

**해결책:**
```kotlin
@Retryable(value = [DeadlockLoserDataAccessException::class], maxAttempts = 3)
@Transactional
fun processWithRetry(request: ProcessRequest): ProcessResult {
    // 트랜잭션 로직
}
```

#### 3. 메모리 부족
**증상:** `OutOfMemoryError`

**해결책:**
```kotlin
// 스트림 처리로 메모리 사용량 제한
@Transactional(readOnly = true)
fun processLargeData() {
    val pageSize = 1000
    var page = 0
    
    do {
        val pageable = PageRequest.of(page, pageSize)
        val reservations = repository.findAll(pageable)
        
        reservations.content.forEach { reservation ->
            processReservation(reservation)
        }
        
        entityManager.clear() // 영속성 컨텍스트 클리어
        page++
    } while (reservations.hasNext())
}
```

## 📚 추가 자료

### 관련 문서
- [Large Data Processing Guide](large-data-processing-guide.md)
- [Database Performance Guide](database-performance-guide.md)
- [Technology Selection Guide](technology-selection-guide.md)

### 외부 리소스
- [Spring Transaction Management](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)
- [JPA Performance Tuning](https://vladmihalcea.com/tutorials/hibernate/)
- [R2DBC Documentation](https://r2dbc.io/spec/0.9.1.RELEASE/spec/html/)

### 성능 테스트 도구
- [TransactionScenarioComparator](../src/main/kotlin/com/example/reservation/benchmark/TransactionScenarioComparator.kt)
- [transaction-scenarios-test.sh](../scripts/transaction-scenarios-test.sh)

---

*이 가이드는 실제 트랜잭션 처리 경험을 바탕으로 작성되었으며, 지속적으로 업데이트됩니다.*