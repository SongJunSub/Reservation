# API 응답 시간 분석 가이드

## 개요

이 문서는 Reservation 시스템의 API 응답 시간을 체계적으로 분석하고 비교하는 방법을 설명합니다. MVC와 WebFlux, Java와 Kotlin 간의 성능 차이를 정량적으로 측정하고 최적화 방향을 제시합니다.

## 분석 도구

### 1. ApiResponseTimeComparator.kt
**목적**: 다양한 부하 조건에서 API 응답 시간을 정밀 측정

**주요 기능**:
- 4가지 부하 레벨 테스트 (단일, 저부하, 중부하, 고부하)
- 실시간 응답 시간 분포 분석
- 백분위수 계산 (P50, P95, P99)
- 에러 타입별 분류 및 분석

**실행 방법**:
```bash
./gradlew bootRun --args="--response-time-comparison"
```

### 2. RealTimePerformanceMonitor.kt
**목적**: 지속적인 실시간 성능 모니터링

**주요 기능**:
- 실시간 성능 지표 수집
- ASCII 차트를 통한 시각화
- 시스템 리소스 모니터링
- 성능 등급 자동 평가

**실행 방법**:
```bash
./gradlew bootRun --args="--real-time-monitor"
```

### 3. response-time-test.sh
**목적**: 포괄적인 응답 시간 테스트 자동화

**실행 방법**:
```bash
# 전체 비교 테스트
./scripts/response-time-test.sh comparison

# 실시간 모니터링
./scripts/response-time-test.sh monitor

# 빠른 테스트
./scripts/response-time-test.sh quick
```

## 테스트 시나리오

### 부하 레벨별 테스트

#### 1. 단일 요청 테스트
- **요청 수**: 100회
- **동시성**: 1
- **목적**: 기본 성능 기준선 측정

#### 2. 저부하 테스트
- **요청 수**: 200회
- **동시성**: 10
- **요청 간격**: 100ms
- **목적**: 일반적인 운영 환경 시뮬레이션

#### 3. 중부하 테스트
- **요청 수**: 500회
- **동시성**: 25
- **요청 간격**: 50ms
- **목적**: 피크 타임 트래픽 시뮬레이션

#### 4. 고부하 테스트
- **요청 수**: 1,000회
- **동시성**: 50
- **요청 간격**: 20ms
- **목적**: 최대 성능 한계 측정

## 성능 메트릭 해석

### 핵심 지표

#### 1. 응답 시간 통계
```
평균 응답시간: 45.23ms
최소 응답시간: 12ms
최대 응답시간: 234ms
P50 (중간값): 42ms
P95: 89ms
P99: 156ms
```

**해석 기준**:
- **우수 (A+)**: 평균 < 50ms, P95 < 100ms
- **양호 (A)**: 평균 < 100ms, P95 < 200ms
- **보통 (B)**: 평균 < 200ms, P95 < 500ms
- **개선 필요 (C)**: 평균 > 200ms

#### 2. 응답 시간 분포
```
응답시간 분포:
  0ms ~ 9ms: 5회 (5.0%)
  10ms ~ 19ms: 15회 (15.0%)
  20ms ~ 29ms: 25회 (25.0%)
  30ms ~ 39ms: 30회 (30.0%)
  40ms ~ 49ms: 20회 (20.0%)
  50ms ~ 59ms: 5회 (5.0%)
```

**분석 포인트**:
- 정규분포에 가까울수록 안정적
- 긴 꼬리(Long tail) 분포는 성능 튜닝 필요
- 이상치(Outlier) 비율 확인

#### 3. 성공률 및 에러 분석
```
성공률: 98.5% (985/1000)
에러 분석:
  SocketTimeoutException: 10회
  ConnectException: 5회
```

**해석**:
- 성공률 > 99%: 우수한 안정성
- 성공률 < 95%: 시스템 안정성 문제

### 기술별 비교 분석

#### MVC vs WebFlux 성능 특성

```
📈 MVC vs WebFlux 비교:
  평균 응답시간 - MVC: 65.42ms, WebFlux: 48.73ms
  성능 차이: WebFlux가 25.5% 빠름
  성공률 - MVC: 98.2%, WebFlux: 99.1%
```

**예상 패턴**:
1. **저부하**: MVC와 WebFlux 성능 유사
2. **중부하**: WebFlux 우위 시작
3. **고부하**: WebFlux 명확한 우위

**원인 분석**:
- **MVC**: Thread-per-request 모델로 인한 컨텍스트 스위칭 오버헤드
- **WebFlux**: Event-loop 기반 비동기 처리로 높은 동시성 지원

#### Java vs Kotlin 성능 특성

```
🔤 Kotlin vs Java 비교:
  평균 응답시간 - Kotlin: 57.18ms, Java: 59.34ms
  성능 차이: Kotlin이 3.6% 빠름
```

**일반적 패턴**:
- 런타임 성능은 대부분 유사 (±5% 내)
- JVM 최적화에 의해 차이가 최소화
- 코드 복잡도에 따라 미세한 차이 발생

## 실시간 모니터링 분석

### 모니터링 화면 해석

```
🔄 실시간 성능 모니터링 - 2024-07-25 14:30:15
================================================================================
엔드포인트         요청수   성공률   평균응답   최소응답   최대응답   메모리(MB)   CPU%
--------------------------------------------------------------------------------
reservations       145     99.3%    42ms      8ms       234ms     256         12.3%
webflux            142     99.6%    38ms      6ms       189ms     248         10.8%
java               139     98.6%    48ms      12ms      267ms     264         14.1%
webflux-java       144     99.2%    41ms      9ms       198ms     252         11.9%
--------------------------------------------------------------------------------

📈 응답시간 추이 (최근 20회)
reservations:
  ▁▃▁▃▅▃▁▅▃▁▅▃▁▃▅▃▁▅▃▁ (45ms)
webflux:
  ▁▁▃▁▃▁▁▃▁▃▁▁▃▁▃▁▁▃▁▁ (35ms)

🖥️ 시스템 리소스:
메모리: 512MB / 2048MB (사용률: 25.0%)
JVM 가동 시간: 3600초
활성 스레드: 25
```

### 패턴 분석

#### 1. 정상 패턴
- 일정한 응답 시간 (변동폭 < 50%)
- 높은 성공률 (> 99%)
- 안정적인 메모리 사용량

#### 2. 성능 저하 패턴
- 응답 시간 급증 (스파이크)
- 성공률 하락
- 메모리 사용량 급증

#### 3. 부하 증가 패턴
- 평균 응답 시간 점진적 증가
- CPU 사용률 상승
- 스레드 수 증가

## 성능 문제 진단

### 일반적인 성능 문제

#### 1. 높은 응답 시간 (> 200ms)
**원인**:
- 데이터베이스 쿼리 최적화 부족
- N+1 쿼리 문제
- 네트워크 지연
- GC 압박

**해결 방법**:
```kotlin
// 데이터베이스 인덱스 추가
@Table(name = "reservations", indexes = [
    Index(name = "idx_status_date", columnList = "status, checkInDate")
])

// 쿼리 최적화
@Query("SELECT r FROM Reservation r JOIN FETCH r.guest WHERE r.status = :status")
fun findByStatusWithGuest(@Param("status") status: ReservationStatus): List<Reservation>

// 캐시 적용
@Cacheable("reservations")
fun findById(id: Long): Reservation?
```

#### 2. 낮은 처리량 (< 100 RPS)
**원인**:
- 스레드 풀 크기 부족
- 커넥션 풀 포화
- 동기 블로킹 처리

**해결 방법**:
```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20000
    
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
```

#### 3. 높은 에러율 (> 5%)
**원인**:
- 타임아웃 설정 부적절
- 리소스 부족
- 동시성 제어 문제

**해결 방법**:
```kotlin
// 타임아웃 설정
@RestController
class ReservationController {
    
    @GetMapping("/reservations/{id}")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun getReservation(@PathVariable id: Long): ResponseEntity<Reservation> {
        // 구현
    }
}

// 서킷 브레이커 적용
@CircuitBreaker(name = "reservation-service")
fun getReservation(id: Long): Reservation {
    // 구현
}
```

## 최적화 전략

### MVC 최적화

#### 1. 스레드 풀 튜닝
```yaml
server:
  tomcat:
    threads:
      max: 200              # CPU 코어 수 × 2-4
      min-spare: 10         # 기본 스레드 수
    accept-count: 100       # 대기열 크기
    connection-timeout: 20000
```

#### 2. 커넥션 풀 최적화
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### WebFlux 최적화

#### 1. 이벤트 루프 튜닝
```kotlin
@Configuration
class WebFluxConfig : WebFluxConfigurer {
    
    @Bean
    fun reactorResourceFactory(): ReactorResourceFactory {
        return ReactorResourceFactory().apply {
            isUseGlobalResources = false
            connectionProvider = ConnectionProvider.builder("custom")
                .maxConnections(500)
                .pendingAcquireMaxCount(1000)
                .build()
        }
    }
}
```

#### 2. 백프레셔 처리
```kotlin
@GetMapping("/stream")
fun getReservationsStream(): Flux<Reservation> {
    return reservationService.findAllReactive()
        .onBackpressureBuffer(1000)  // 버퍼 크기 설정
        .subscribeOn(Schedulers.boundedElastic())
}
```

### 언어별 최적화

#### Kotlin 최적화
```kotlin
// 인라인 함수 활용
inline fun <T> measureTime(block: () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = block()
    val time = System.currentTimeMillis() - start
    return result to time
}

// 데이터 클래스 최적화
@JvmInline
value class ReservationId(val value: Long)

// 컬렉션 최적화
fun processReservations(reservations: List<Reservation>): List<ReservationDto> {
    return reservations.asSequence()
        .filter { it.isActive() }
        .map { it.toDto() }
        .toList()
}
```

#### Java 최적화
```java
// 스트림 병렬 처리
public List<ReservationDto> processReservations(List<Reservation> reservations) {
    return reservations.parallelStream()
        .filter(Reservation::isActive)
        .map(this::toDto)
        .collect(Collectors.toList());
}

// StringBuilder 사용
public String createMessage(List<String> items) {
    StringBuilder sb = new StringBuilder();
    for (String item : items) {
        sb.append(item).append(", ");
    }
    return sb.toString();
}
```

## 지속적인 성능 관리

### 1. 성능 테스트 자동화
```bash
# CI/CD 파이프라인에 포함
./scripts/response-time-test.sh quick
if [ $? -ne 0 ]; then
    echo "성능 테스트 실패"
    exit 1
fi
```

### 2. 알림 설정
```yaml
# application.yml
management:
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
        
# 알림 규칙 (Prometheus)
- alert: HighResponseTime
  expr: http_request_duration_seconds{quantile="0.95"} > 0.2
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "높은 응답 시간 감지"
```

### 3. 성능 추세 분석
- 주간/월간 성능 리포트 생성
- 성능 회귀 탐지
- 용량 계획 수립

## 결론

API 응답 시간 분석을 통해:

1. **기술 선택의 근거** 제공
2. **성능 병목점** 식별
3. **최적화 우선순위** 결정
4. **용량 계획** 수립

지속적인 모니터링과 분석을 통해 시스템의 성능을 최적 상태로 유지할 수 있습니다.