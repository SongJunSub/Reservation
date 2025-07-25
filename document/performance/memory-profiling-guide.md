# 메모리 프로파일링 가이드

## 개요

이 가이드는 Reservation 시스템의 메모리 사용 패턴을 분석하고 최적화하는 방법을 설명합니다. JVM 메모리 구조부터 실제 프로파일링 도구 사용법까지 포괄적으로 다룹니다.

## JVM 메모리 구조 이해

### 1. 힙 메모리 (Heap Memory)
```
┌─────────────────────────────────────────┐
│                Heap Memory              │
├─────────────────┬───────────────────────┤
│   Young Gen     │      Old Gen          │
├─────┬─────┬─────┼───────────────────────┤
│Eden │ S0  │ S1  │    Tenured Space      │
└─────┴─────┴─────┴───────────────────────┘
```

**Young Generation**:
- **Eden Space**: 새로운 객체가 생성되는 공간
- **Survivor Spaces (S0, S1)**: Minor GC에서 살아남은 객체들

**Old Generation**:
- **Tenured Space**: 오래 살아남은 객체들이 이동하는 공간

### 2. 비힙 메모리 (Non-Heap Memory)
- **Metaspace**: 클래스 메타데이터 저장 (Java 8+)
- **Code Cache**: JIT 컴파일된 네이티브 코드
- **Direct Memory**: ByteBuffer 등 직접 메모리

## 프로파일링 도구

### 1. MemoryProfiler.kt
**목적**: 다양한 시나리오에서 메모리 사용 패턴 분석

**주요 기능**:
- 5가지 시나리오별 메모리 사용량 측정
- GC 효율성 분석
- 메모리 사용 패턴 분석
- 최적화 권장사항 제공

**실행 방법**:
```bash
./gradlew bootRun --args="--memory-profiling"
```

**테스트 시나리오**:
1. **기본 메모리 베이스라인**: 시스템 안정 상태 메모리
2. **단일 요청 처리**: 개별 요청의 메모리 사용량
3. **동시 요청 처리**: 동시성 환경에서의 메모리 사용
4. **대용량 데이터 처리**: 배치 작업 시 메모리 패턴
5. **장시간 실행**: 메모리 누수 감지

### 2. MemoryLeakDetector.kt
**목적**: 잠재적인 메모리 누수 자동 감지

**주요 기능**:
- 5가지 누수 패턴 감지
- 위험도별 분류 (HIGH/MEDIUM/LOW)
- 누수 방지 권장사항 제공
- WeakReference 기반 객체 추적

**실행 방법**:
```bash
./gradlew bootRun --args="--memory-leak-detection"
```

**감지 패턴**:
1. **컬렉션 누수**: 계속 증가하는 컬렉션
2. **리스너 누수**: 해제되지 않는 이벤트 리스너
3. **ThreadLocal 누수**: 정리되지 않는 ThreadLocal 변수
4. **캐시 누수**: 무한 증가하는 캐시
5. **클로저 누수**: 외부 변수를 참조하는 클로저

### 3. memory-analysis.sh
**목적**: 종합적인 메모리 분석 자동화

**실행 방법**:
```bash
# 종합 분석 (권장)
./scripts/memory-analysis.sh comprehensive

# 개별 분석
./scripts/memory-analysis.sh profile     # 프로파일링
./scripts/memory-analysis.sh leak        # 누수 감지
./scripts/memory-analysis.sh monitor     # 실시간 모니터링
./scripts/memory-analysis.sh heapdump    # 힙 덤프 생성
./scripts/memory-analysis.sh gc          # GC 분석
```

## 메모리 분석 결과 해석

### 프로파일링 결과 예시
```
📊 메모리 프로파일링 분석 결과
================================================================================
분석 시간: 2024-07-25 15:30:00

🎯 단일 요청 처리
------------------------------------------------------------
메모리 사용량:
  시작 힙 메모리: 128MB
  종료 힙 메모리: 142MB
  최대 힙 메모리: 165MB
  평균 힙 메모리: 148MB
  힙 사용률: 28.4%

GC 통계:
  GC 실행 횟수: 15
  GC 소요 시간: 145ms
  GC 효율성: 94.2%

스레드 정보:
  현재 스레드 수: 25
  최대 스레드 수: 28

메모리 패턴 분석:
  ✅ 안정적인 메모리 사용 패턴
  ➡️ 안정적인 메모리 사용 추세
```

### 메모리 누수 감지 결과 예시
```
🕵️ 메모리 누수 감지 결과 보고서
================================================================================
분석 시간: 2024-07-25 15:35:00
테스트 지속시간: 180000ms

📊 누수 의심 항목 요약:
  총 의심 항목: 3개
  높은 위험도: 1개
  중간 위험도: 1개
  낮은 위험도: 1개

📈 메모리 사용 분석:
  메모리 증가율: 2.34KB/초
  GC 압박도: 0.15회/초
  전체 위험도: 중간

🚨 누수 의심 항목 상세:
------------------------------------------------------------
🔴 cache_leak_test (Cache)
    감지 횟수: 1250
    위험도: HIGH
    첫 감지: 15:32:15
    최근 감지: 15:35:45

🟡 collection_leak_test (MutableList)
    감지 횟수: 450
    위험도: MEDIUM
    첫 감지: 15:32:05
    최근 감지: 15:35:30
```

## 성능 메트릭 해석

### 1. 메모리 사용률
```
메모리 사용률 = (사용 중인 힙 메모리 / 최대 힙 메모리) × 100
```

**평가 기준**:
- **< 70%**: 정상
- **70-85%**: 주의 필요
- **85-95%**: 경고
- **> 95%**: 위험

### 2. GC 효율성
```
GC 효율성 = ((전체 실행 시간 - GC 시간) / 전체 실행 시간) × 100
```

**평가 기준**:
- **> 95%**: A+ (우수)
- **90-95%**: A (양호)
- **85-90%**: B (보통)
- **80-85%**: C (개선 필요)
- **< 80%**: D (심각)

### 3. 메모리 증가율
```
메모리 증가율 = (최종 메모리 - 초기 메모리) / 실행 시간
```

**평가 기준**:
- **< 1KB/초**: 정상
- **1-5KB/초**: 주의
- **5-10KB/초**: 경고
- **> 10KB/초**: 누수 의심

## 메모리 최적화 전략

### 1. 힙 메모리 최적화

#### JVM 옵션 튜닝
```bash
# 힙 크기 설정
-Xms512m -Xmx2g

# G1GC 사용 (권장)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m

# GC 로깅
-Xloggc:gc.log
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
```

#### 객체 생성 최적화
```kotlin
// ❌ 비효율적
fun processData(items: List<String>): List<String> {
    val result = mutableListOf<String>()
    for (item in items) {
        result.add(item.uppercase()) // 매번 새 문자열 생성
    }
    return result
}

// ✅ 효율적
fun processData(items: List<String>): List<String> {
    return items.map { it.uppercase() } // 함수형 스타일, 최적화된 구현
}

// ✅ 더 효율적 (대용량 데이터)
fun processDataSequence(items: List<String>): Sequence<String> {
    return items.asSequence().map { it.uppercase() } // 지연 평가
}
```

### 2. 컬렉션 최적화

#### 적절한 초기 크기 설정
```kotlin
// ❌ 비효율적 (기본 크기로 시작, 여러 번 확장)
val list = mutableListOf<String>()
repeat(1000) { list.add("item$it") }

// ✅ 효율적 (예상 크기로 초기화)
val list = ArrayList<String>(1000)
repeat(1000) { list.add("item$it") }
```

#### 불변 컬렉션 사용
```kotlin
// ❌ 가변 컬렉션 (메모리 오버헤드)
val mutableList = mutableListOf("a", "b", "c")

// ✅ 불변 컬렉션 (메모리 효율적)
val immutableList = listOf("a", "b", "c")
```

### 3. 메모리 누수 방지

#### WeakReference 사용
```kotlin
// ❌ 강한 참조로 인한 누수
class EventManager {
    private val listeners = mutableListOf<EventListener>()
    
    fun addListener(listener: EventListener) {
        listeners.add(listener)
    }
}

// ✅ 약한 참조로 누수 방지
class EventManager {
    private val listeners = mutableListOf<WeakReference<EventListener>>()
    
    fun addListener(listener: EventListener) {
        listeners.add(WeakReference(listener))
    }
    
    private fun cleanupListeners() {
        listeners.removeAll { it.get() == null }
    }
}
```

#### ThreadLocal 정리
```kotlin
// ❌ ThreadLocal 누수
class UserContext {
    companion object {
        private val userInfo = ThreadLocal<UserInfo>()
        
        fun setUser(user: UserInfo) {
            userInfo.set(user)
        }
    }
}

// ✅ ThreadLocal 정리
class UserContext {
    companion object {
        private val userInfo = ThreadLocal<UserInfo>()
        
        fun setUser(user: UserInfo) {
            userInfo.set(user)
        }
        
        fun cleanup() {
            userInfo.remove() // 중요: 스레드 종료 시 호출
        }
    }
}
```

### 4. 캐시 최적화

#### LRU 캐시 구현
```kotlin
// ✅ 크기 제한이 있는 LRU 캐시
class LRUCache<K, V>(private val maxSize: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxSize
    }
}

// 사용 예시
val cache = LRUCache<String, Reservation>(100)
```

#### WeakHashMap 사용
```kotlin
// ✅ 자동으로 정리되는 캐시
private val cache = WeakHashMap<String, Reservation>()
```

## 모니터링 및 알림

### 1. 프로덕션 모니터링
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: metrics,health,info
  metrics:
    export:
      prometheus:
        enabled: true

# 메모리 관련 메트릭 수집
spring:
  jmx:
    enabled: true
```

### 2. 알림 설정 (Prometheus)
```yaml
# 메모리 사용률 알림
- alert: HighMemoryUsage
  expr: (jvm_memory_used_bytes / jvm_memory_max_bytes) * 100 > 80
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "높은 메모리 사용률"
    description: "메모리 사용률이 {{ $value }}%입니다"

# GC 시간 알림
- alert: HighGCTime
  expr: rate(jvm_gc_collection_seconds_sum[5m]) > 0.1
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "높은 GC 시간"
    description: "GC 시간이 {{ $value }}초입니다"
```

## 문제 해결 가이드

### 1. OutOfMemoryError
**증상**: `java.lang.OutOfMemoryError: Java heap space`

**원인**:
- 힙 크기 부족
- 메모리 누수
- 대용량 객체 생성

**해결 방법**:
```bash
# 힙 크기 증가
-Xmx4g

# 힙 덤프 생성으로 원인 분석
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dumps/

# 메모리 누수 감지 도구 실행
./scripts/memory-analysis.sh leak
```

### 2. 빈번한 GC
**증상**: 높은 GC 빈도, 성능 저하

**원인**:
- Young Generation 크기 부족
- 짧은 생명주기 객체 과다 생성
- GC 알고리즘 부적합

**해결 방법**:
```bash
# G1GC 사용
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200

# Young Generation 크기 조정
-XX:NewRatio=2
-XX:SurvivorRatio=8

# GC 분석
./scripts/memory-analysis.sh gc
```

### 3. 메타스페이스 부족
**증상**: `java.lang.OutOfMemoryError: Metaspace`

**원인**:
- 동적 클래스 생성 과다
- 클래스로더 누수

**해결 방법**:
```bash
# 메타스페이스 크기 증가
-XX:MetaspaceSize=256m
-XX:MaxMetaspaceSize=512m

# 클래스 언로딩 활성화
-XX:+CMSClassUnloadingEnabled
```

## 성능 테스트 자동화

### CI/CD 통합
```yaml
# .github/workflows/memory-test.yml
name: Memory Performance Test

on:
  pull_request:
    branches: [ main ]

jobs:
  memory-test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Set up JDK 21
      uses: actions/setup-java@v2
      with:
        java-version: '21'
    - name: Run memory profiling
      run: |
        ./scripts/memory-analysis.sh profile
        ./scripts/memory-analysis.sh leak
    - name: Upload reports
      uses: actions/upload-artifact@v2
      with:
        name: memory-reports
        path: |
          gc.log
          heap-dumps/
```

## 결론

메모리 프로파일링은 애플리케이션 성능 최적화의 핵심입니다:

1. **정기적인 프로파일링**으로 성능 회귀 탐지
2. **자동화된 누수 감지**로 문제 조기 발견
3. **적절한 JVM 튜닝**으로 최적 성능 달성
4. **지속적인 모니터링**으로 안정성 확보

이 가이드의 도구와 방법론을 활용하여 메모리 효율적인 애플리케이션을 구축하고 유지관리하시기 바랍니다.