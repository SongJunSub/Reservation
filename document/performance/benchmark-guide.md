# 성능 벤치마크 가이드

## 개요

이 가이드는 Reservation 시스템의 성능 벤치마크를 실행하고 결과를 분석하는 방법을 설명합니다.

## 벤치마크 도구

### 1. PerformanceBenchmark.kt
내부 성능 측정 도구로, 애플리케이션 내에서 직접 서비스 호출 성능을 측정합니다.

**특징:**
- 단일 요청 성능 측정
- 동시 요청 성능 측정
- MVC vs WebFlux 비교
- Java vs Kotlin 비교
- 상세한 통계 분석 (평균, 최소/최대, 백분위수)

**실행 방법:**
```bash
./gradlew bootRun --args="--benchmark"
```

### 2. LoadTestRunner.kt
실제 HTTP 요청을 통한 부하 테스트 도구입니다.

**특징:**
- 실제 HTTP 엔드포인트 테스트
- 사용자 시뮬레이션 (램프업, 동시성)
- 실시간 모니터링
- 에러 타입별 분석

**실행 방법:**
```bash
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun
```

### 3. benchmark.sh
포괄적인 벤치마크 실행 스크립트입니다.

**실행 방법:**
```bash
# 전체 벤치마크
./scripts/benchmark.sh

# 특정 테스트만 실행
./scripts/benchmark.sh internal  # 내부 벤치마크
./scripts/benchmark.sh load      # 부하 테스트
./scripts/benchmark.sh curl      # cURL 벤치마크
```

## 테스트 시나리오

### 내부 벤치마크 시나리오

#### 1. 단일 요청 성능 테스트
- **목적**: 기본적인 CRUD 성능 측정
- **요청 수**: 1,000회
- **측정 항목**: 응답 시간, 처리량

#### 2. 동시 요청 성능 테스트
- **목적**: 동시성 처리 능력 측정
- **요청 수**: 1,000회
- **동시성**: 50개 스레드
- **측정 항목**: 확장성 지수, 응답 시간 분포

#### 3. MVC vs WebFlux 비교
- **목적**: 동기 vs 비동기 처리 성능 비교
- **요청 수**: 각 500회
- **측정 항목**: 처리량, 응답 시간, 리소스 사용량

#### 4. Java vs Kotlin 비교
- **목적**: 언어별 성능 특성 분석
- **요청 수**: 1,000회
- **측정 항목**: 실행 시간, 메모리 사용량

### 부하 테스트 시나리오

#### 엔드포인트별 테스트
1. **Kotlin MVC API** (`/api/reservations`)
2. **Kotlin WebFlux API** (`/api/webflux/reservations`)
3. **Java MVC API** (`/api/java/reservations`)
4. **Java WebFlux API** (`/api/webflux-java/reservations`)

#### 테스트 설정
- **총 요청 수**: 1,000회
- **동시 사용자**: 50명
- **램프업 시간**: 10초
- **테스트 지속 시간**: 60초

## 성능 메트릭 해석

### 핵심 지표

#### 1. 응답 시간 (Response Time)
- **평균**: 전체 요청의 평균 응답 시간
- **최소/최대**: 가장 빠른/느린 응답 시간
- **P95/P99**: 95%/99% 요청이 완료된 시간

**해석:**
- 평균 < 100ms: 우수
- 평균 < 200ms: 양호
- 평균 < 500ms: 보통
- 평균 > 500ms: 개선 필요

#### 2. 처리량 (Throughput)
- **RPS (Requests Per Second)**: 초당 처리 가능한 요청 수

**해석:**
- RPS > 1000: 고성능
- RPS > 500: 양호
- RPS > 100: 보통
- RPS < 100: 개선 필요

#### 3. 성공률 (Success Rate)
- **정의**: 성공한 요청의 비율

**해석:**
- > 99%: 우수
- > 95%: 양호
- > 90%: 보통
- < 90%: 문제 있음

#### 4. 확장성 지수 (Scalability Index)
- **정의**: 동시 요청 RPS / 단일 요청 RPS
- **해석**:
  - > 0.8: 우수한 확장성
  - > 0.5: 보통 확장성
  - < 0.5: 확장성 문제

### 비교 분석

#### MVC vs WebFlux
```
예상 결과:
- MVC: 낮은 동시성, 높은 지연시간
- WebFlux: 높은 동시성, 낮은 지연시간

WebFlux가 유리한 상황:
- 높은 동시 요청
- I/O 집약적 작업
- 마이크로서비스 통신

MVC가 유리한 상황:
- 단순한 CRUD
- CPU 집약적 작업
- 기존 블로킹 라이브러리 사용
```

#### Java vs Kotlin
```
예상 결과:
- Java: 안정적 성능, 예측 가능
- Kotlin: 비슷한 성능, 간결한 코드

성능 차이:
- 런타임: 거의 동일 (같은 JVM)
- 컴파일 시간: Kotlin이 약간 느림
- 메모리 사용: 거의 동일
```

## 성능 최적화 가이드

### 1. 응답 시간 최적화
```kotlin
// 데이터베이스 쿼리 최적화
@Query("SELECT r FROM Reservation r WHERE r.status = :status")
fun findByStatus(@Param("status") status: ReservationStatus): List<Reservation>

// 인덱스 추가
@Table(name = "reservations", indexes = [
    Index(name = "idx_status", columnList = "status"),
    Index(name = "idx_check_in_date", columnList = "checkInDate")
])
```

### 2. 동시성 향상
```kotlin
// 리액티브 스트림 활용
@GetMapping("/stream")
fun getReservationsStream(): Flux<Reservation> {
    return reservationService.findAllReactive()
        .subscribeOn(Schedulers.boundedElastic())
}

// 캐시 적용
@Cacheable("reservations")
fun findById(id: Long): Reservation? {
    return reservationRepository.findById(id)
}
```

### 3. 메모리 최적화
```kotlin
// 페이징 처리
fun findAll(pageable: Pageable): Page<Reservation> {
    return reservationRepository.findAll(pageable)
}

// 지연 로딩 설정
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "guest_id")
val guest: Guest
```

## 벤치마크 결과 예시

### 내부 벤치마크 결과
```
================================================================================
🏆 성능 벤치마크 결과 보고서
================================================================================
실행 시간: 2024-07-25T10:30:00

📊 단일 요청 성능
--------------------------------------------------
총 요청 수: 1000
성공 요청: 1000
실패 요청: 0
성공률: 100.00%
총 실행 시간: 5420ms
평균 응답 시간: 5.42ms
초당 요청 수 (RPS): 184.50
최소 응답 시간: 1ms
최대 응답 시간: 45ms
95% 응답 시간: 12ms
99% 응답 시간: 28ms

📊 동시 요청 성능 (동시성: 50)
--------------------------------------------------
총 요청 수: 1000
성공 요청: 995
실패 요청: 5
성공률: 99.50%
총 실행 시간: 8240ms
평균 응답 시간: 8.28ms
초당 요청 수 (RPS): 120.73
최소 응답 시간: 2ms
최대 응답 시간: 156ms
95% 응답 시간: 34ms
99% 응답 시간: 89ms

📈 성능 분석 및 권장사항
--------------------------------------------------
확장성 지수: 0.65
⚠️ 보통 확장성: 동시 처리 최적화가 필요할 수 있습니다.

💡 최적화 제안:
- 동시 요청 성능: 응답 시간 편차 개선 필요
```

### cURL 벤치마크 결과
```
🔄 각 엔드포인트별 성능 테스트

1️⃣ Kotlin MVC API 테스트:
응답시간: 0.045초 | HTTP 상태: 201 | 크기: 298바이트

2️⃣ Kotlin WebFlux API 테스트:
응답시간: 0.038초 | HTTP 상태: 201 | 크기: 298바이트

3️⃣ Java MVC API 테스트:
응답시간: 0.052초 | HTTP 상태: 201 | 크기: 298바이트

4️⃣ Java WebFlux API 테스트:
응답시간: 0.041초 | HTTP 상태: 201 | 크기: 298바이트

🔥 연속 요청 테스트 (100회)

📊 Kotlin-MVC 연속 요청 테스트:
....................
  성공률: 100.00% (100/100)
  총 시간: 4.23초
  초당 요청: 23.64 RPS

📊 Kotlin-WebFlux 연속 요청 테스트:
....................
  성공률: 100.00% (100/100)
  총 시간: 3.87초
  초당 요청: 25.84 RPS

📊 Java-MVC 연속 요청 테스트:
....................
  성공률: 100.00% (100/100)
  총 시간: 4.45초
  초당 요청: 22.47 RPS

📊 Java-WebFlux 연속 요청 테스트:
....................
  성공률: 100.00% (100/100)
  총 시간: 3.92초
  초당 요청: 25.51 RPS
```

## 문제 해결

### 일반적인 성능 문제

#### 1. 높은 응답 시간
**원인:**
- 데이터베이스 쿼리 최적화 부족
- N+1 쿼리 문제
- 캐시 미적용

**해결:**
- 쿼리 최적화 및 인덱스 추가
- 즉시/지연 로딩 설정 최적화
- 적절한 캐시 전략 적용

#### 2. 낮은 처리량
**원인:**
- 동기 블로킹 처리
- 스레드 풀 크기 부족
- GC 압박

**해결:**
- 비동기 처리 도입
- 스레드 풀 튜닝
- JVM 메모리 설정 최적화

#### 3. 높은 에러율
**원인:**
- 타임아웃 설정 문제
- 리소스 부족
- 동시성 제어 문제

**해결:**
- 타임아웃 값 조정
- 하드웨어 리소스 증설
- 동시성 제어 로직 개선

## 지속적인 성능 모니터링

### 1. 정기적 벤치마크
```bash
# 주간 성능 테스트
crontab -e
0 2 * * 1 /path/to/scripts/benchmark.sh > /var/log/benchmark.log 2>&1
```

### 2. 성능 회귀 탐지
- CI/CD 파이프라인에 성능 테스트 통합
- 성능 임계값 설정 및 알림
- 성능 추이 모니터링

### 3. 프로덕션 모니터링
- APM 도구 연동 (예: New Relic, DataDog)
- 메트릭 수집 및 대시보드 구성
- 알림 규칙 설정