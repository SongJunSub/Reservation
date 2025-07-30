# 🚀 JVM Tuning and Garbage Collection Optimization Guide

## 목차
1. [개요](#개요)
2. [JVM 아키텍처 이해](#jvm-아키텍처-이해)
3. [가비지 컬렉션 알고리즘](#가비지-컬렉션-알고리즘)
4. [힙 메모리 튜닝](#힙-메모리-튜닝)
5. [JVM 플래그 최적화](#jvm-플래그-최적화)
6. [메모리 누수 감지 및 해결](#메모리-누수-감지-및-해결)
7. [GC 로그 분석](#gc-로그-분석)
8. [성능 모니터링 및 프로파일링](#성능-모니터링-및-프로파일링)
9. [실무 최적화 전략](#실무-최적화-전략)
10. [트러블슈팅 가이드](#트러블슈팅-가이드)

---

## 개요

JVM 튜닝은 Java 애플리케이션의 성능을 최적화하는 핵심 기술입니다. 특히 Spring Boot 기반의 예약 시스템과 같은 엔터프라이즈 애플리케이션에서는 적절한 JVM 튜닝이 전체 시스템 성능에 결정적인 영향을 미칩니다.

### 주요 튜닝 영역

| 영역 | 목표 | 주요 메트릭 |
|------|------|------------|
| **가비지 컬렉션** | 짧은 일시정지, 높은 처리량 | 처리량 >95%, 일시정지 <100ms |
| **힙 메모리** | 효율적 메모리 사용 | 힙 사용률 70-80% |
| **JVM 플래그** | 전체적 성능 향상 | 응답시간, CPU 사용률 |
| **메모리 누수** | 안정적 메모리 관리 | 메모리 증가율 <1%/day |

### 성능 목표 설정

```kotlin
// 성능 목표 예시
data class PerformanceTargets(
    val maxPauseTimeMs: Long = 100,           // 최대 GC 일시정지 시간
    val minThroughputPercent: Double = 95.0,  // 최소 처리량
    val maxHeapUtilization: Double = 80.0,    // 최대 힙 사용률
    val maxResponseTimeMs: Long = 200,        // 최대 응답시간
    val maxMemoryLeakMBPerDay: Double = 10.0  // 일일 메모리 증가 허용치
)

// 성능 측정 기본 구조
class JVMPerformanceMonitor {
    fun measureGCPerformance(): GCMetrics {
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val memoryBean = ManagementFactory.getMemoryMXBean()
        
        return GCMetrics(
            totalCollections = gcBeans.sumOf { it.collectionCount },
            totalCollectionTime = gcBeans.sumOf { it.collectionTime },
            heapUsed = memoryBean.heapMemoryUsage.used,
            heapMax = memoryBean.heapMemoryUsage.max,
            throughput = calculateThroughput(),
            averagePauseTime = calculateAveragePauseTime()
        )
    }
}
```

---

## JVM 아키텍처 이해

### 1. JVM 메모리 구조

```
┌─────────────────────────────────────────────────────────┐
│                    JVM Memory Layout                    │
├─────────────────────────────────────────────────────────┤
│  Heap Memory                                            │
│  ┌─────────────────┬─────────────────────────────────┐  │
│  │   Young Gen     │         Old Gen                 │  │
│  │  ┌─────┬─────┐  │  ┌─────────────────────────────┐ │  │
│  │  │Eden │ S0  │  │  │      Tenured Space          │ │  │
│  │  │     │ S1  │  │  │                             │ │  │
│  │  └─────┴─────┘  │  └─────────────────────────────┘ │  │
│  └─────────────────┴─────────────────────────────────┘  │
├─────────────────────────────────────────────────────────┤
│  Non-Heap Memory                                        │
│  ┌─────────────────┬─────────────────┬───────────────┐  │
│  │   Metaspace     │   Code Cache    │  Compressed   │  │
│  │                 │                 │  Class Space  │  │
│  └─────────────────┴─────────────────┴───────────────┘  │
├─────────────────────────────────────────────────────────┤
│  Direct Memory (Off-Heap)                              │
│  ┌─────────────────────────────────────────────────────┐│
│  │              NIO Buffers, etc.                      ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

### 2. 메모리 영역별 특성

#### Heap Memory

```kotlin
// Young Generation 최적화
class YoungGenerationOptimizer {
    fun optimizeEdenSpace(workloadType: WorkloadType): EdenConfiguration {
        return when (workloadType) {
            WorkloadType.HIGH_ALLOCATION -> EdenConfiguration(
                size = "2g",
                ratio = "8:1:1", // Eden:S0:S1
                description = "높은 할당률을 위한 큰 Eden 공간"
            )
            
            WorkloadType.LONG_LIVED_OBJECTS -> EdenConfiguration(
                size = "1g",
                ratio = "6:2:2", // 더 큰 Survivor 공간
                description = "장수명 객체를 위한 Survivor 공간 확대"
            )
            
            WorkloadType.BALANCED -> EdenConfiguration(
                size = "1.5g",
                ratio = "8:1:1", // 표준 비율
                description = "균형잡힌 워크로드를 위한 기본 설정"
            )
        }
    }
}

// Old Generation 관리
class OldGenerationManager {
    fun analyzePromotionRate(): PromotionAnalysis {
        val youngGCCount = getYoungGCCount()
        val oldGCCount = getOldGCCount()
        val promotedBytes = getPromotedBytes()
        
        return PromotionAnalysis(
            promotionRate = promotedBytes / youngGCCount,
            oldGCFrequency = oldGCCount.toDouble() / youngGCCount,
            recommendation = if (promotionRate > PROMOTION_THRESHOLD) {
                "Young Generation 크기 증가 고려"
            } else {
                "현재 설정 유지"
            }
        )
    }
}
```

#### Non-Heap Memory

```kotlin
// Metaspace 모니터링
class MetaspaceMonitor {
    fun analyzeMetaspaceUsage(): MetaspaceAnalysis {
        val metaspaceBean = ManagementFactory.getMemoryPoolMXBeans()
            .find { it.name.contains("Metaspace") }
            
        return metaspaceBean?.let { bean ->
            MetaspaceAnalysis(
                used = bean.usage.used,
                committed = bean.usage.committed,
                max = bean.usage.max,
                utilizationPercent = (bean.usage.used.toDouble() / bean.usage.committed) * 100,
                recommendations = generateMetaspaceRecommendations(bean.usage)
            )
        } ?: MetaspaceAnalysis.empty()
    }
    
    private fun generateMetaspaceRecommendations(usage: MemoryUsage): List<String> {
        val utilization = (usage.used.toDouble() / usage.committed) * 100
        
        return when {
            utilization > 90 -> listOf(
                "Metaspace 사용률이 높습니다. -XX:MaxMetaspaceSize 증가 고려",
                "클래스 로딩 패턴을 검토하세요"
            )
            utilization < 30 -> listOf(
                "Metaspace 사용률이 낮습니다. -XX:MetaspaceSize 감소 고려",
                "메모리 효율성을 위한 설정 조정"
            )
            else -> listOf("현재 Metaspace 사용률이 적절합니다")
        }
    }
}
```

---

## 가비지 컬렉션 알고리즘

### 1. G1GC (Garbage First)

G1GC는 Java 11+ 환경에서 기본 GC 알고리즘으로, 낮은 지연시간과 높은 처리량을 모두 지원합니다.

```kotlin
// G1GC 최적화 설정
class G1GCOptimizer {
    fun generateOptimalFlags(heapSize: String, targetPauseMs: Int): List<String> {
        return listOf(
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=$targetPauseMs",
            "-XX:G1HeapRegionSize=${calculateOptimalRegionSize(heapSize)}m",
            "-XX:G1NewSizePercent=20",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1MixedGCCountTarget=8",
            "-XX:InitiatingHeapOccupancyPercent=45",
            "-XX:G1OldCSetRegionThreshold=10"
        )
    }
    
    private fun calculateOptimalRegionSize(heapSize: String): Int {
        val heapMB = parseHeapSize(heapSize)
        return when {
            heapMB >= 32 * 1024 -> 32  // 32GB+ → 32MB regions
            heapMB >= 16 * 1024 -> 16  // 16GB+ → 16MB regions
            heapMB >= 8 * 1024 -> 8    // 8GB+  → 8MB regions
            heapMB >= 4 * 1024 -> 4    // 4GB+  → 4MB regions
            else -> 1                   // <4GB  → 1MB regions
        }
    }
}

// G1GC 성능 분석
class G1GCAnalyzer {
    fun analyzeG1Performance(): G1PerformanceReport {
        val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
        val g1YoungGen = gcBeans.find { it.name.contains("G1 Young Generation") }
        val g1OldGen = gcBeans.find { it.name.contains("G1 Old Generation") }
        
        return G1PerformanceReport(
            youngGCCount = g1YoungGen?.collectionCount ?: 0,
            youngGCTime = g1YoungGen?.collectionTime ?: 0,
            mixedGCCount = g1OldGen?.collectionCount ?: 0,
            mixedGCTime = g1OldGen?.collectionTime ?: 0,
            averageYoungGCPause = calculateAverageYoungGCPause(),
            averageMixedGCPause = calculateAverageMixedGCPause(),
            regionUtilization = analyzeRegionUtilization(),
            recommendations = generateG1Recommendations()
        )
    }
}
```

### 2. Parallel GC

높은 처리량이 필요한 배치 작업이나 백그라운드 처리에 적합합니다.

```kotlin
// Parallel GC 설정
class ParallelGCOptimizer {
    fun optimizeForThroughput(availableCores: Int): ParallelGCConfig {
        val gcThreads = calculateOptimalGCThreads(availableCores)
        
        return ParallelGCConfig(
            flags = listOf(
                "-XX:+UseParallelGC",
                "-XX:+UseParallelOldGC",
                "-XX:ParallelGCThreads=$gcThreads",
                "-XX:MaxGCPauseMillis=200",
                "-XX:GCTimeRatio=19", // GC 시간을 전체의 5% 이하로 제한
                "-XX:+UseAdaptiveSizePolicy"
            ),
            description = "처리량 최적화를 위한 Parallel GC 설정"
        )
    }
    
    private fun calculateOptimalGCThreads(cores: Int): Int {
        return when {
            cores >= 16 -> cores / 2  // 많은 코어: 절반 사용
            cores >= 8 -> cores - 2   // 중간 코어: 2개 여유
            cores >= 4 -> cores - 1   // 적은 코어: 1개 여유
            else -> 1                  // 매우 적은 코어: 1개 사용
        }
    }
}
```

### 3. ZGC (Z Garbage Collector)

매우 낮은 지연시간이 요구되는 실시간 애플리케이션에 적합합니다.

```kotlin
// ZGC 설정 (Java 17+)
class ZGCOptimizer {
    fun configureForLowLatency(): ZGCConfig {
        return ZGCConfig(
            flags = listOf(
                "-XX:+UseZGC",
                "-XX:+UnlockExperimentalVMOptions", // Java 17 이전
                "-XX:ZCollectionInterval=1000",     // 1초마다 GC 체크
                "-XX:ZUncommitDelay=300",           // 5분 후 메모리 반환
                "-XX:+UseLargePages"                // 대용량 페이지 사용
            ),
            minimumHeapSize = "8g", // ZGC는 최소 8GB 권장
            maxPauseTarget = "10ms", // 목표 일시정지 시간
            description = "극저지연 애플리케이션을 위한 ZGC 설정"
        )
    }
    
    fun analyzeZGCPerformance(): ZGCPerformanceReport {
        // ZGC 전용 메트릭 수집
        return ZGCPerformanceReport(
            allocationRate = getAllocationRate(),
            pauseTimes = collectPauseTimes(),
            memoryUtilization = getMemoryUtilization(),
            concurrentCycles = getConcurrentCycles(),
            recommendations = if (maxPauseTime > Duration.ofMillis(10)) {
                listOf("ZGC 일시정지 시간이 목표를 초과합니다. 힙 크기 조정을 고려하세요.")
            } else {
                listOf("ZGC 성능이 목표 범위 내에 있습니다.")
            }
        )
    }
}
```

### 4. GC 알고리즘 선택 가이드

```kotlin
// GC 알고리즘 선택 도우미
class GCAlgorithmSelector {
    fun recommendGC(requirements: ApplicationRequirements): GCRecommendation {
        return when {
            requirements.maxLatencyMs < 10 -> GCRecommendation(
                algorithm = "ZGC",
                reason = "극저지연 요구사항",
                config = zgcOptimizer.configureForLowLatency()
            )
            
            requirements.maxLatencyMs < 100 && requirements.heapSizeGB >= 4 -> GCRecommendation(
                algorithm = "G1GC",
                reason = "낮은 지연시간과 큰 힙 크기",
                config = g1Optimizer.generateOptimalFlags("${requirements.heapSizeGB}g", requirements.maxLatencyMs)
            )
            
            requirements.throughputPriority && !requirements.latencySensitive -> GCRecommendation(
                algorithm = "Parallel GC",
                reason = "높은 처리량 우선",
                config = parallelOptimizer.optimizeForThroughput(requirements.availableCores)
            )
            
            requirements.heapSizeGB < 2 -> GCRecommendation(
                algorithm = "Serial GC",
                reason = "작은 힙 크기",
                config = listOf("-XX:+UseSerialGC")
            )
            
            else -> GCRecommendation(
                algorithm = "G1GC",
                reason = "범용적인 성능과 안정성",
                config = g1Optimizer.generateOptimalFlags("${requirements.heapSizeGB}g", 100)
            )
        }
    }
}
```

---

## 힙 메모리 튜닝

### 1. 힙 크기 결정

```kotlin
// 힙 크기 계산기
class HeapSizeCalculator {
    fun calculateOptimalHeapSize(requirements: HeapRequirements): HeapSizeRecommendation {
        val baseMemory = requirements.applicationMemoryMB
        val peakMemory = requirements.peakMemoryMB
        val safetyMargin = 1.3 // 30% 여유분
        
        val recommendedHeap = (peakMemory * safetyMargin).toInt()
        val maxHeap = minOf(recommendedHeap, requirements.availableMemoryMB * 0.8).toInt()
        val minHeap = maxOf(baseMemory, maxHeap / 2)
        
        return HeapSizeRecommendation(
            initialHeap = "${minHeap}m",
            maximumHeap = "${maxHeap}m",
            reasoning = buildString {
                appendLine("기본 메모리: ${baseMemory}MB")
                appendLine("피크 메모리: ${peakMemory}MB")
                appendLine("안전 여유분: 30%")
                appendLine("시스템 메모리의 80% 이하 권장")
            },
            flags = listOf(
                "-Xms${minHeap}m",
                "-Xmx${maxHeap}m"
            )
        )
    }
}

// 동적 힙 크기 조정
class DynamicHeapTuner {
    fun adjustHeapBasedOnUsage(currentUsage: HeapUsageMetrics): HeapAdjustment {
        val utilizationPercent = (currentUsage.used.toDouble() / currentUsage.max) * 100
        
        return when {
            utilizationPercent > 85 -> HeapAdjustment(
                action = "INCREASE",
                newSize = "${(currentUsage.max / 1024 / 1024 * 1.5).toInt()}m",
                reason = "힙 사용률이 85%를 초과했습니다"
            )
            
            utilizationPercent < 30 -> HeapAdjustment(
                action = "DECREASE",
                newSize = "${(currentUsage.max / 1024 / 1024 * 0.8).toInt()}m",
                reason = "힙 사용률이 30% 미만입니다"
            )
            
            else -> HeapAdjustment(
                action = "MAINTAIN",
                newSize = "${currentUsage.max / 1024 / 1024}m",
                reason = "현재 힙 사용률이 적절합니다"
            )
        }
    }
}
```

### 2. Young/Old Generation 비율 최적화

```kotlin
// Generation 비율 최적화
class GenerationRatioOptimizer {
    fun optimizeGenerationRatio(workloadAnalysis: WorkloadAnalysis): GenerationConfig {
        return when (workloadAnalysis.objectLifetime) {
            ObjectLifetime.VERY_SHORT -> GenerationConfig(
                newRatio = 2, // Young:Old = 1:2
                survivorRatio = 8, // Eden:Survivor = 8:1:1
                description = "매우 단수명 객체가 많은 워크로드",
                expectedBenefit = "Young GC 빈도 감소, Old GC 압박 완화"
            )
            
            ObjectLifetime.SHORT -> GenerationConfig(
                newRatio = 3, // Young:Old = 1:3 (기본)
                survivorRatio = 8,
                description = "일반적인 웹 애플리케이션 워크로드",
                expectedBenefit = "균형잡힌 GC 성능"
            )
            
            ObjectLifetime.MIXED -> GenerationConfig(
                newRatio = 4, // Young:Old = 1:4
                survivorRatio = 6, // 더 큰 Survivor 공간
                description = "다양한 수명의 객체가 혼재",
                expectedBenefit = "객체 승격 최적화"
            )
            
            ObjectLifetime.LONG -> GenerationConfig(
                newRatio = 6, // Young:Old = 1:6
                survivorRatio = 4,
                description = "장수명 객체가 많은 워크로드",
                expectedBenefit = "Old Generation 효율성 향상"
            )
        }
    }
    
    // 승격률 분석
    fun analyzePromotionRate(): PromotionRateAnalysis {
        val youngGCMetrics = getYoungGCMetrics()
        val promotedBytes = youngGCMetrics.sumOf { it.promotedBytes }
        val totalAllocated = youngGCMetrics.sumOf { it.allocatedBytes }
        
        val promotionRate = (promotedBytes.toDouble() / totalAllocated) * 100
        
        return PromotionRateAnalysis(
            promotionRatePercent = promotionRate,
            evaluation = when {
                promotionRate > 20 -> "높음 - Young Generation 크기 증가 필요"
                promotionRate > 10 -> "보통 - 모니터링 지속"
                else -> "낮음 - 현재 설정 적절"
            },
            recommendations = generatePromotionOptimizations(promotionRate)
        )
    }
}
```

### 3. 메모리 할당 패턴 분석

```kotlin
// 할당 패턴 분석기
class AllocationPatternAnalyzer {
    fun analyzeAllocationPatterns(): AllocationAnalysis {
        val allocationSampler = AllocationSampler()
        val samples = allocationSampler.collectSamples(Duration.ofMinutes(5))
        
        return AllocationAnalysis(
            totalAllocations = samples.size,
            allocationRate = calculateAllocationRate(samples),
            objectSizeDistribution = analyzeObjectSizes(samples),
            typeDistribution = analyzeObjectTypes(samples),
            hotspots = identifyAllocationHotspots(samples),
            recommendations = generateAllocationRecommendations(samples)
        )
    }
    
    private fun identifyAllocationHotspots(samples: List<AllocationSample>): List<AllocationHotspot> {
        return samples
            .groupBy { it.stackTrace.topFrame }
            .mapValues { (_, allocations) ->
                AllocationHotspot(
                    location = it.key,
                    count = allocations.size,
                    totalBytes = allocations.sumOf { it.size },
                    averageSize = allocations.map { it.size }.average(),
                    frequency = allocations.size.toDouble() / samples.size
                )
            }
            .values
            .sortedByDescending { it.totalBytes }
            .take(10)
    }
}

// TLAB (Thread Local Allocation Buffer) 최적화
class TLABOptimizer {
    fun optimizeTLAB(threadCount: Int, allocationRate: Long): TLABConfig {
        val tlabSize = calculateOptimalTLABSize(allocationRate)
        val wasteLimitPercent = calculateWasteLimit(threadCount)
        
        return TLABConfig(
            flags = listOf(
                "-XX:+UseTLAB",
                "-XX:TLABSize=${tlabSize}k",
                "-XX:TLABWasteTargetPercent=$wasteLimitPercent",
                "-XX:+ResizeTLAB"
            ),
            expectedBenefits = listOf(
                "스레드별 할당 성능 향상",
                "할당 경합 감소",
                "Young Generation 단편화 완화"
            )
        )
    }
}
```

---

## JVM 플래그 최적화

### 1. 성능 관련 플래그

```kotlin
// 성능 플래그 최적화기
class PerformanceFlagsOptimizer {
    fun generatePerformanceFlags(environment: Environment): List<String> {
        val baseFlags = mutableListOf<String>()
        
        // 컴파일러 최적화
        baseFlags.addAll(getCompilerOptimizations(environment))
        
        // 메모리 최적화
        baseFlags.addAll(getMemoryOptimizations(environment))
        
        // I/O 최적화
        baseFlags.addAll(getIOOptimizations(environment))
        
        // 프로파일링 및 모니터링
        if (environment.enableProfiling) {
            baseFlags.addAll(getProfilingFlags())
        }
        
        return baseFlags
    }
    
    private fun getCompilerOptimizations(env: Environment): List<String> {
        return listOf(
            "-server",                                    // 서버 모드 활성화
            "-XX:+TieredCompilation",                    // 계층적 컴파일
            "-XX:TieredStopAtLevel=4",                   // 최대 최적화 레벨
            "-XX:+UseStringDeduplication",               // 문자열 중복 제거 (G1GC only)
            "-XX:+OptimizeStringConcat",                 // 문자열 연결 최적화
            "-XX:+UseCompressedOops",                    // 압축된 OOP 사용
            "-XX:+UseCompressedClassPointers"            // 압축된 클래스 포인터
        ).filter { flag ->
            isCompatible(flag, env.gcAlgorithm)
        }
    }
    
    private fun getMemoryOptimizations(env: Environment): List<String> {
        return buildList {
            // 기본 메모리 최적화
            add("-XX:+UseG1GC")
            add("-XX:MaxGCPauseMillis=100")
            
            // 대용량 페이지 (가능한 경우)
            if (env.largePageSupport) {
                add("-XX:+UseLargePages")
                add("-XX:LargePageSizeInBytes=2m")
            }
            
            // NUMA 최적화 (다중 소켓 시스템)
            if (env.numaNodes > 1) {
                add("-XX:+UseNUMA")
            }
            
            // 메모리 관리 최적화
            add("-XX:+UnlockExperimentalVMOptions")
            add("-XX:+UseTransparentHugePages")
        }
    }
    
    private fun getIOOptimizations(env: Environment): List<String> {
        return listOf(
            "-Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.EPollSelectorProvider",
            "-Dio.netty.eventLoopThreads=${env.availableCores}",
            "-Dio.netty.allocator.type=pooled"
        )
    }
}

// 플래그 효과 검증
class FlagEffectValidator {
    fun validateFlagEffectiveness(
        baseline: PerformanceMetrics,
        optimized: PerformanceMetrics,
        appliedFlags: List<String>
    ): FlagValidationReport {
        
        val improvements = calculateImprovements(baseline, optimized)
        val regressions = identifyRegressions(baseline, optimized)
        
        return FlagValidationReport(
            appliedFlags = appliedFlags,
            improvements = improvements,
            regressions = regressions,
            overallScore = calculateOverallScore(improvements, regressions),
            recommendations = generateFlagRecommendations(improvements, regressions)
        )
    }
    
    private fun calculateImprovements(
        baseline: PerformanceMetrics,
        optimized: PerformanceMetrics
    ): Map<String, Double> {
        return mapOf(
            "throughput" to calculateImprovement(baseline.throughput, optimized.throughput),
            "latency" to calculateImprovement(optimized.averageLatency, baseline.averageLatency), // 낮을수록 좋음
            "gc_time" to calculateImprovement(optimized.gcTime, baseline.gcTime), // 낮을수록 좋음
            "memory_usage" to calculateImprovement(optimized.memoryUsage, baseline.memoryUsage) // 낮을수록 좋음
        )
    }
}
```

### 2. 환경별 최적화 설정

```kotlin
// 환경별 JVM 설정
class EnvironmentSpecificTuning {
    fun getProductionSettings(systemConfig: SystemConfiguration): JVMConfiguration {
        return JVMConfiguration(
            heapSettings = HeapSettings(
                initialHeap = "${systemConfig.memoryGB / 2}g",
                maximumHeap = "${(systemConfig.memoryGB * 0.8).toInt()}g",
                newRatio = 3
            ),
            
            gcSettings = GCSettings(
                collector = "G1GC",
                maxPauseMillis = 100,
                heapRegionSize = calculateOptimalRegionSize(systemConfig.memoryGB),
                additionalFlags = listOf(
                    "-XX:InitiatingHeapOccupancyPercent=45",
                    "-XX:G1MixedGCCountTarget=8",
                    "-XX:G1OldCSetRegionThreshold=10"
                )
            ),
            
            performanceFlags = listOf(
                "-server",
                "-XX:+TieredCompilation",
                "-XX:+UseStringDeduplication",
                "-XX:+UseCompressedOops"
            ),
            
            monitoringFlags = listOf(
                "-XX:+PrintGC",
                "-XX:+PrintGCDetails",
                "-XX:+PrintGCTimeStamps",
                "-Xloggc:logs/gc.log",
                "-XX:+UseGCLogFileRotation",
                "-XX:NumberOfGCLogFiles=5",
                "-XX:GCLogFileSize=100M"
            )
        )
    }
    
    fun getDevelopmentSettings(): JVMConfiguration {
        return JVMConfiguration(
            heapSettings = HeapSettings(
                initialHeap = "512m",
                maximumHeap = "2g",
                newRatio = 2 // 더 많은 Young Generation
            ),
            
            gcSettings = GCSettings(
                collector = "G1GC",
                maxPauseMillis = 200, // 개발 환경에서는 관대한 설정
                additionalFlags = listOf(
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:+UseEpsilonGC" // 테스트용 No-Op GC
                )
            ),
            
            debugFlags = listOf(
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=dumps/",
                "-XX:+PrintCompilation",
                "-XX:+TraceClassLoading"
            )
        )
    }
}
```

### 3. JVM 플래그 최적화 전략

```kotlin
// 최적화 전략 매니저
class OptimizationStrategyManager {
    fun createOptimizationPlan(
        currentMetrics: PerformanceMetrics,
        targetMetrics: PerformanceTargets
    ): OptimizationPlan {
        
        val strategies = mutableListOf<OptimizationStrategy>()
        
        // GC 성능 최적화
        if (currentMetrics.gcOverhead > targetMetrics.maxGCOverhead) {
            strategies.add(GCOptimizationStrategy(
                priority = Priority.HIGH,
                estimatedImpact = "GC 오버헤드 20-30% 감소",
                flags = optimizeGCPerformance(currentMetrics)
            ))
        }
        
        // 메모리 사용량 최적화
        if (currentMetrics.memoryUtilization > targetMetrics.maxMemoryUtilization) {
            strategies.add(MemoryOptimizationStrategy(
                priority = Priority.MEDIUM,
                estimatedImpact = "메모리 사용량 10-15% 감소",
                flags = optimizeMemoryUsage(currentMetrics)
            ))
        }
        
        // 처리량 최적화
        if (currentMetrics.throughput < targetMetrics.minThroughput) {
            strategies.add(ThroughputOptimizationStrategy(
                priority = Priority.HIGH,
                estimatedImpact = "처리량 15-25% 증가",
                flags = optimizeThroughput(currentMetrics)
            ))
        }
        
        return OptimizationPlan(
            strategies = strategies.sortedByDescending { it.priority },
            estimatedDuration = Duration.ofWeeks(2),
            rollbackPlan = createRollbackPlan(strategies)
        )
    }
}
```

---

## 메모리 누수 감지 및 해결

### 1. 메모리 누수 패턴 식별

```kotlin
// 메모리 누수 감지기
class MemoryLeakDetector {
    fun detectPotentialLeaks(): List<MemoryLeakSuspect> {
        val suspects = mutableListOf<MemoryLeakSuspect>()
        
        // 1. 컬렉션 누수 감지
        suspects.addAll(detectCollectionLeaks())
        
        // 2. 리스너 누수 감지
        suspects.addAll(detectListenerLeaks())
        
        // 3. ThreadLocal 누수 감지
        suspects.addAll(detectThreadLocalLeaks())
        
        // 4. 캐시 누수 감지
        suspects.addAll(detectCacheLeaks())
        
        // 5. 클래스로더 누수 감지
        suspects.addAll(detectClassLoaderLeaks())
        
        return suspects.sortedByDescending { it.riskLevel }
    }
    
    private fun detectCollectionLeaks(): List<MemoryLeakSuspect> {
        val gcBefore = getGCCount()
        val memoryBefore = getHeapUsed()
        
        // GC 강제 실행
        System.gc()
        Thread.sleep(1000)
        
        val gcAfter = getGCCount()
        val memoryAfter = getHeapUsed()
        
        val memoryReduction = memoryBefore - memoryAfter
        val reductionPercent = (memoryReduction.toDouble() / memoryBefore) * 100
        
        return if (reductionPercent < 10) {
            listOf(MemoryLeakSuspect(
                type = LeakType.COLLECTION_LEAK,
                description = "GC 후에도 메모리가 충분히 회수되지 않음",
                riskLevel = RiskLevel.HIGH,
                suspectedCauses = listOf(
                    "Static 컬렉션에 계속 객체 추가",
                    "컬렉션 clear() 누락",
                    "장수명 객체가 단수명 객체 참조"
                ),
                recommendations = listOf(
                    "Static 컬렉션 사용 검토",
                    "명시적 clear() 호출 추가",
                    "WeakReference 사용 고려"
                )
            ))
        } else {
            emptyList()
        }
    }
    
    private fun detectThreadLocalLeaks(): List<MemoryLeakSuspect> {
        val threadLocalFields = findThreadLocalFields()
        val suspects = mutableListOf<MemoryLeakSuspect>()
        
        threadLocalFields.forEach { field ->
            val accessPattern = analyzeThreadLocalAccess(field)
            if (accessPattern.hasLeakPotential) {
                suspects.add(MemoryLeakSuspect(
                    type = LeakType.THREAD_LOCAL_LEAK,
                    description = "ThreadLocal 변수의 잠재적 누수: ${field.name}",
                    riskLevel = RiskLevel.MEDIUM,
                    suspectedCauses = listOf(
                        "ThreadLocal.remove() 호출 누락",
                        "긴 수명의 스레드에서 ThreadLocal 사용",
                        "ThreadLocalMap 정리 안됨"
                    )
                ))
            }
        }
        
        return suspects
    }
}

// 힙 덤프 분석기
class HeapDumpAnalyzer {
    fun analyzeHeapDump(dumpFile: File): HeapAnalysisReport {
        val analysis = performHeapAnalysis(dumpFile)
        
        return HeapAnalysisReport(
            totalObjects = analysis.objectCount,
            totalSize = analysis.totalSize,
            largestObjects = analysis.largestObjects.take(20),
            duplicateStrings = analysis.duplicateStrings,
            potentialLeaks = identifyLeaksFromDump(analysis),
            recommendations = generateHeapRecommendations(analysis)
        )
    }
    
    private fun identifyLeaksFromDump(analysis: HeapAnalysis): List<PotentialLeak> {
        val leaks = mutableListOf<PotentialLeak>()
        
        // 1. 큰 컬렉션 식별
        analysis.largeCollections.forEach { collection ->
            if (collection.size > LARGE_COLLECTION_THRESHOLD) {
                leaks.add(PotentialLeak(
                    type = "Large Collection",
                    className = collection.className,
                    instanceCount = collection.instanceCount,
                    totalSize = collection.totalSize,
                    suspicion = "컬렉션이 비정상적으로 큼"
                ))
            }
        }
        
        // 2. 중복 객체 식별
        analysis.duplicateObjects.forEach { duplicate ->
            if (duplicate.count > DUPLICATE_THRESHOLD) {
                leaks.add(PotentialLeak(
                    type = "Duplicate Objects",
                    className = duplicate.className,
                    instanceCount = duplicate.count,
                    totalSize = duplicate.totalSize,
                    suspicion = "동일한 객체가 과도하게 중복됨"
                ))
            }
        }
        
        return leaks
    }
}
```

### 2. 메모리 누수 방지 패턴

```kotlin
// 안전한 컬렉션 관리
class SafeCollectionManager {
    // WeakHashMap 사용 예제
    fun createSafeCache<K, V>(): MutableMap<K, V> {
        return Collections.synchronizedMap(WeakHashMap<K, V>())
    }
    
    // 자동 정리 기능이 있는 캐시
    fun createSelfCleaningCache<K, V>(
        maxSize: Int,
        ttlMinutes: Long
    ): Cache<K, V> {
        return Caffeine.newBuilder()
            .maximumSize(maxSize.toLong())
            .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
            .removalListener<K, V> { key, value, cause ->
                println("캐시 항목 제거: $key (원인: $cause)")
            }
            .build()
    }
}

// 안전한 리스너 관리
class SafeListenerManager {
    private val listeners = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<EventListener, Boolean>())
    )
    
    fun addListener(listener: EventListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: EventListener) {
        listeners.remove(listener)
    }
    
    // 자동 정리 메서드
    fun cleanupStaleListeners() {
        // WeakHashMap이 자동으로 정리하지만, 명시적 정리도 가능
        listeners.removeAll { !isListenerValid(it) }
    }
}

// ThreadLocal 안전 사용 패턴
class SafeThreadLocalUsage {
    companion object {
        private val threadLocalContext = ThreadLocal<UserContext>()
        
        fun setContext(context: UserContext) {
            threadLocalContext.set(context)
        }
        
        fun getContext(): UserContext? {
            return threadLocalContext.get()
        }
        
        fun clearContext() {
            threadLocalContext.remove() // 중요: 반드시 remove() 호출
        }
    }
    
    // try-with-resources 패턴 적용
    class ThreadLocalScope(private val context: UserContext) : AutoCloseable {
        init {
            setContext(context)
        }
        
        override fun close() {
            clearContext()
        }
    }
    
    // 사용 예제
    fun processWithContext(context: UserContext) {
        ThreadLocalScope(context).use {
            // 비즈니스 로직 수행
            performBusinessLogic()
            // scope가 끝나면 자동으로 ThreadLocal 정리
        }
    }
}
```

### 3. 메모리 누수 모니터링

```kotlin
// 메모리 누수 모니터링 시스템
class MemoryLeakMonitor {
    private val memoryMetrics = mutableListOf<MemorySnapshot>()
    private val alertThresholds = MemoryAlertThresholds()
    
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    fun captureMemorySnapshot() {
        val snapshot = MemorySnapshot(
            timestamp = LocalDateTime.now(),
            heapUsed = getHeapUsed(),
            heapMax = getHeapMax(),
            nonHeapUsed = getNonHeapUsed(),
            youngGenUsed = getYoungGenUsed(),
            oldGenUsed = getOldGenUsed(),
            metaspaceUsed = getMetaspaceUsed()
        )
        
        memoryMetrics.add(snapshot)
        analyzeMemoryTrend(snapshot)
        
        // 오래된 데이터 정리 (24시간 이상)
        val cutoff = LocalDateTime.now().minusHours(24)
        memoryMetrics.removeAll { it.timestamp.isBefore(cutoff) }
    }
    
    private fun analyzeMemoryTrend(currentSnapshot: MemorySnapshot) {
        if (memoryMetrics.size < 10) return // 충분한 데이터가 있을 때만 분석
        
        val recentSnapshots = memoryMetrics.takeLast(10)
        val memoryGrowthRate = calculateGrowthRate(recentSnapshots)
        
        // 메모리 누수 의심 조건
        when {
            memoryGrowthRate > alertThresholds.highGrowthRate -> {
                sendAlert(MemoryAlert(
                    level = AlertLevel.HIGH,
                    message = "메모리 사용량이 빠르게 증가하고 있습니다: ${memoryGrowthRate}%/hour",
                    recommendations = listOf(
                        "힙 덤프 생성 고려",
                        "메모리 프로파일링 실행",
                        "최근 코드 변경 사항 검토"
                    )
                ))
            }
            
            memoryGrowthRate > alertThresholds.mediumGrowthRate -> {
                sendAlert(MemoryAlert(
                    level = AlertLevel.MEDIUM,
                    message = "메모리 사용량 증가 감지: ${memoryGrowthRate}%/hour",
                    recommendations = listOf("지속적인 모니터링 필요")
                ))
            }
        }
        
        // GC 효율성 확인
        val gcEfficiency = calculateGCEfficiency()
        if (gcEfficiency < alertThresholds.lowGCEfficiency) {
            sendAlert(MemoryAlert(
                level = AlertLevel.MEDIUM,
                message = "GC 효율성이 낮습니다: ${gcEfficiency}%",
                recommendations = listOf(
                    "GC 로그 분석",
                    "힙 크기 조정 고려",
                    "GC 알고리즘 재검토"
                )
            ))
        }
    }
}
```

---

## GC 로그 분석

### 1. GC 로그 설정

```kotlin
// GC 로깅 설정 생성기
class GCLoggingConfigurator {
    fun generateGCLoggingFlags(javaVersion: Int, logLevel: LogLevel): List<String> {
        return when {
            javaVersion >= 11 -> generateJava11PlusFlags(logLevel)
            javaVersion >= 9 -> generateJava9Flags(logLevel)
            else -> generateJava8Flags(logLevel)
        }
    }
    
    private fun generateJava11PlusFlags(logLevel: LogLevel): List<String> {
        val baseFlags = mutableListOf(
            "-Xlog:gc*:logs/gc.log:time,pid,tid,level,tags",
            "-Xlog:gc+heap=info",
            "-Xlog:gc+ergo=info"
        )
        
        when (logLevel) {
            LogLevel.BASIC -> {
                baseFlags.add("-Xlog:gc")
            }
            
            LogLevel.DETAILED -> {
                baseFlags.addAll(listOf(
                    "-Xlog:gc+phases=debug",
                    "-Xlog:gc+regions=debug",
                    "-Xlog:gc+refine=debug"
                ))
            }
            
            LogLevel.VERBOSE -> {
                baseFlags.addAll(listOf(
                    "-Xlog:gc*:gc-verbose.log:time,pid,tid,level,tags",
                    "-Xlog:gc+heap=debug",
                    "-Xlog:gc+ergo=debug",
                    "-Xlog:gc+phases=trace"
                ))
            }
        }
        
        // 로그 파일 회전 설정
        baseFlags.addAll(listOf(
            "-XX:+UseGCLogFileRotation",
            "-XX:NumberOfGCLogFiles=10",
            "-XX:GCLogFileSize=100M"
        ))
        
        return baseFlags
    }
    
    private fun generateJava8Flags(logLevel: LogLevel): List<String> {
        return when (logLevel) {
            LogLevel.BASIC -> listOf(
                "-XX:+PrintGC",
                "-XX:+PrintGCTimeStamps",
                "-Xloggc:logs/gc.log"
            )
            
            LogLevel.DETAILED -> listOf(
                "-XX:+PrintGC",
                "-XX:+PrintGCDetails",
                "-XX:+PrintGCTimeStamps",
                "-XX:+PrintGCApplicationStoppedTime",
                "-Xloggc:logs/gc.log"
            )
            
            LogLevel.VERBOSE -> listOf(
                "-XX:+PrintGC",
                "-XX:+PrintGCDetails",
                "-XX:+PrintGCTimeStamps",
                "-XX:+PrintGCApplicationStoppedTime",
                "-XX:+PrintGCApplicationConcurrentTime",
                "-XX:+PrintStringDeduplicationStatistics",
                "-Xloggc:logs/gc-verbose.log"
            )
        } + listOf(
            "-XX:+UseGCLogFileRotation",
            "-XX:NumberOfGCLogFiles=10",
            "-XX:GCLogFileSize=100M"
        )
    }
}
```

### 2. GC 로그 파싱 및 분석

```kotlin
// GC 로그 파서
class GCLogParser {
    fun parseGCLog(logFile: File): GCLogAnalysis {
        val gcEvents = mutableListOf<GCEvent>()
        val lines = logFile.readLines()
        
        var currentEvent: GCEvent? = null
        
        lines.forEach { line ->
            when {
                line.contains("GC(") -> {
                    currentEvent?.let { gcEvents.add(it) }
                    currentEvent = parseGCEventStart(line)
                }
                
                line.contains("Pause") && currentEvent != null -> {
                    currentEvent = parseGCPause(line, currentEvent!!)
                }
                
                line.contains("Eden:") || line.contains("Survivor:") -> {
                    currentEvent = parseMemoryRegions(line, currentEvent)
                }
                
                line.contains("Times:") -> {
                    currentEvent = parseGCTimes(line, currentEvent)
                }
            }
        }
        
        currentEvent?.let { gcEvents.add(it) }
        
        return analyzeGCEvents(gcEvents)
    }
    
    private fun analyzeGCEvents(events: List<GCEvent>): GCLogAnalysis {
        val youngGCEvents = events.filter { it.type == GCType.YOUNG }
        val mixedGCEvents = events.filter { it.type == GCType.MIXED }
        val fullGCEvents = events.filter { it.type == GCType.FULL }
        
        return GCLogAnalysis(
            totalEvents = events.size,
            youngGCStats = calculateGCStats(youngGCEvents),
            mixedGCStats = calculateGCStats(mixedGCEvents),
            fullGCStats = calculateGCStats(fullGCEvents),
            overallThroughput = calculateOverallThroughput(events),
            memoryUtilizationTrend = analyzeMemoryTrend(events),
            recommendations = generateGCRecommendations(events)
        )
    }
    
    private fun calculateGCStats(events: List<GCEvent>): GCStats {
        if (events.isEmpty()) return GCStats.empty()
        
        val pauseTimes = events.map { it.pauseTimeMs }
        val memoryReclaimed = events.map { it.memoryBefore - it.memoryAfter }
        
        return GCStats(
            count = events.size,
            totalPauseTime = pauseTimes.sum(),
            averagePauseTime = pauseTimes.average(),
            minPauseTime = pauseTimes.minOrNull() ?: 0.0,
            maxPauseTime = pauseTimes.maxOrNull() ?: 0.0,
            p95PauseTime = pauseTimes.sorted().let { 
                it[((it.size - 1) * 0.95).toInt()] 
            },
            totalMemoryReclaimed = memoryReclaimed.sum(),
            averageMemoryReclaimed = memoryReclaimed.average(),
            frequency = calculateGCFrequency(events)
        )
    }
}

// GC 성능 평가기
class GCPerformanceEvaluator {
    fun evaluateGCPerformance(analysis: GCLogAnalysis): GCPerformanceReport {
        val scores = mutableMapOf<String, Double>()
        
        // 처리량 점수 (0-100)
        scores["throughput"] = when {
            analysis.overallThroughput >= 99.0 -> 100.0
            analysis.overallThroughput >= 95.0 -> 85.0
            analysis.overallThroughput >= 90.0 -> 70.0
            analysis.overallThroughput >= 85.0 -> 50.0
            else -> 25.0
        }
        
        // 일시정지 시간 점수
        val avgPause = analysis.youngGCStats.averagePauseTime
        scores["latency"] = when {
            avgPause <= 10 -> 100.0
            avgPause <= 50 -> 85.0
            avgPause <= 100 -> 70.0
            avgPause <= 200 -> 50.0
            else -> 25.0
        }
        
        // 일관성 점수 (표준편차 기반)
        val pauseVariability = calculatePauseVariability(analysis)
        scores["consistency"] = when {
            pauseVariability <= 0.2 -> 100.0
            pauseVariability <= 0.4 -> 80.0
            pauseVariability <= 0.6 -> 60.0
            else -> 40.0
        }
        
        val overallScore = scores.values.average()
        val grade = calculateGrade(overallScore)
        
        return GCPerformanceReport(
            overallScore = overallScore,
            grade = grade,
            individualScores = scores,
            strengths = identifyStrengths(scores),
            weaknesses = identifyWeaknesses(scores),
            recommendations = generateDetailedRecommendations(analysis, scores)
        )
    }
}
```

### 3. GC 로그 시각화

```kotlin
// GC 로그 시각화 생성기
class GCLogVisualizer {
    fun generateVisualization(analysis: GCLogAnalysis): GCVisualization {
        return GCVisualization(
            pauseTimeChart = generatePauseTimeChart(analysis),
            throughputChart = generateThroughputChart(analysis),
            memoryUtilizationChart = generateMemoryChart(analysis),
            gcFrequencyChart = generateFrequencyChart(analysis)
        )
    }
    
    private fun generatePauseTimeChart(analysis: GCLogAnalysis): ChartData {
        // ASCII 차트 생성 (실제로는 더 복잡한 차트 라이브러리 사용)
        val chart = StringBuilder()
        chart.appendLine("GC Pause Time Distribution")
        chart.appendLine("=" * 40)
        
        val pauseTimes = analysis.getAllPauseTimes()
        val histogram = createHistogram(pauseTimes, 10)
        
        histogram.forEach { (range, count) ->
            val bar = "*".repeat(count / maxOf(1, histogram.values.maxOrNull()!! / 40))
            chart.appendLine("${range.format()}: $bar ($count)")
        }
        
        return ChartData(
            title = "GC Pause Time Distribution",
            data = chart.toString(),
            insights = analyzePauseTimeDistribution(histogram)
        )
    }
    
    private fun generateMemoryChart(analysis: GCLogAnalysis): ChartData {
        val chart = StringBuilder()
        chart.appendLine("Memory Utilization Over Time")
        chart.appendLine("=" * 40)
        
        // 메모리 사용률 시계열 데이터 시각화
        val memoryTrend = analysis.memoryUtilizationTrend
        memoryTrend.forEachIndexed { index, utilization ->
            val time = String.format("%02d:00", index)
            val bar = "█".repeat((utilization * 50).toInt())
            chart.appendLine("$time |$bar| ${String.format("%.1f", utilization * 100)}%")
        }
        
        return ChartData(
            title = "Memory Utilization Trend",
            data = chart.toString(),
            insights = analyzeMemoryTrend(memoryTrend)
        )
    }
}
```

---

## 성능 모니터링 및 프로파일링

### 1. 실시간 성능 모니터링

```kotlin
// JVM 성능 모니터링 시스템
class JVMPerformanceMonitor {
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val gcMonitor = GCMetricsMonitor()
    private val memoryMonitor = MemoryMetricsMonitor()
    private val threadMonitor = ThreadMetricsMonitor()
    
    fun startMonitoring() {
        // Micrometer 메트릭 등록
        registerJVMMetrics()
        
        // 커스텀 메트릭 수집 시작
        startCustomMetricsCollection()
        
        // 성능 알림 설정
        setupPerformanceAlerts()
    }
    
    private fun registerJVMMetrics() {
        JvmGcMetrics().bindTo(meterRegistry)
        JvmMemoryMetrics().bindTo(meterRegistry)
        JvmThreadMetrics().bindTo(meterRegistry)
        ProcessorMetrics().bindTo(meterRegistry)
        ClassLoaderMetrics().bindTo(meterRegistry)
    }
    
    private fun startCustomMetricsCollection() {
        // GC 상세 메트릭
        gcMonitor.startMonitoring { gcMetrics ->
            Gauge.builder("jvm.gc.efficiency")
                .description("GC efficiency percentage")
                .register(meterRegistry) { gcMetrics.efficiency }
                
            Timer.builder("jvm.gc.pause.time")
                .description("GC pause time distribution")
                .register(meterRegistry)
                .record(gcMetrics.lastPauseTime, TimeUnit.MILLISECONDS)
        }
        
        // 메모리 할당률 추적
        memoryMonitor.trackAllocationRate { allocationRate ->
            Gauge.builder("jvm.memory.allocation.rate")
                .description("Memory allocation rate MB/sec")
                .register(meterRegistry) { allocationRate }
        }
    }
    
    @EventListener
    fun handlePerformanceEvent(event: PerformanceEvent) {
        when (event.type) {
            PerformanceEventType.HIGH_GC_OVERHEAD -> {
                Counter.builder("jvm.performance.alerts")
                    .tag("type", "gc_overhead")
                    .register(meterRegistry)
                    .increment()
                    
                sendAlert("GC 오버헤드가 임계값을 초과했습니다: ${event.value}%")
            }
            
            PerformanceEventType.MEMORY_LEAK_SUSPECTED -> {
                Counter.builder("jvm.performance.alerts")
                    .tag("type", "memory_leak")
                    .register(meterRegistry)
                    .increment()
                    
                triggerHeapDumpGeneration()
            }
        }
    }
}

// 애플리케이션별 성능 메트릭
class ApplicationPerformanceCollector {
    @Timed(name = "reservation.creation.time", description = "Reservation creation time")
    fun createReservation(reservationData: ReservationData): Reservation {
        return Timer.Sample.start(meterRegistry).use { sample ->
            val reservation = reservationService.create(reservationData)
            
            // 메모리 할당 추적
            trackMemoryAllocation("reservation.creation")
            
            // 비즈니스 메트릭 기록
            Counter.builder("reservation.created")
                .tag("type", reservationData.type.name)
                .register(meterRegistry)
                .increment()
                
            reservation
        }
    }
    
    private fun trackMemoryAllocation(operation: String) {
        val threadBean = ManagementFactory.getThreadMXBean()
        if (threadBean.isThreadAllocatedMemorySupported) {
            val allocatedBytes = threadBean.getThreadAllocatedBytes(Thread.currentThread().id)
            
            Gauge.builder("app.memory.allocated")
                .tag("operation", operation)
                .register(meterRegistry) { allocatedBytes.toDouble() }
        }
    }
}
```

### 2. 프로파일링 도구 통합

```kotlin
// 프로파일링 매니저
class ProfilingManager {
    fun startCPUProfiling(duration: Duration): CPUProfilingResult {
        val profiler = createCPUProfiler()
        
        profiler.startProfiling()
        Thread.sleep(duration.toMillis())
        val result = profiler.stopProfiling()
        
        return analyzeCPUProfile(result)
    }
    
    fun startMemoryProfiling(duration: Duration): MemoryProfilingResult {
        val profiler = createMemoryProfiler()
        
        profiler.startMemoryTracking()
        Thread.sleep(duration.toMillis())
        val result = profiler.stopMemoryTracking()
        
        return analyzeMemoryProfile(result)
    }
    
    private fun createCPUProfiler(): CPUProfiler {
        return CPUProfiler.builder()
            .samplingInterval(Duration.ofMillis(10))
            .includeNativeFrames(true)
            .filterByPackage("com.example.reservation")
            .build()
    }
    
    private fun analyzeCPUProfile(result: CPUProfileResult): CPUProfilingResult {
        val hotMethods = result.getMostCPUIntensiveMethods(20)
        val hotspots = identifyCPUHotspots(hotMethods)
        
        return CPUProfilingResult(
            totalSamples = result.totalSamples,
            samplingDuration = result.duration,
            hotMethods = hotMethods,
            hotspots = hotspots,
            optimizationSuggestions = generateCPUOptimizationSuggestions(hotspots)
        )
    }
}

// 자동 프로파일링 트리거
class AutomaticProfilingTrigger {
    @EventListener
    fun onHighCPUUsage(event: HighCPUUsageEvent) {
        if (event.cpuUsagePercent > 85) {
            CompletableFuture.runAsync {
                val profilingResult = profilingManager.startCPUProfiling(Duration.ofMinutes(2))
                generatePerformanceReport(profilingResult)
            }
        }
    }
    
    @EventListener
    fun onMemoryPressure(event: MemoryPressureEvent) {
        if (event.heapUtilization > 90) {
            CompletableFuture.runAsync {
                val heapDump = generateHeapDump()
                val analysis = analyzeHeapDump(heapDump)
                sendMemoryAnalysisReport(analysis)
            }
        }
    }
}
```

### 3. 성능 대시보드

```kotlin
// 성능 대시보드 컨트롤러
@RestController
@RequestMapping("/api/performance")
class PerformanceDashboardController {
    
    @GetMapping("/jvm/metrics")
    fun getJVMMetrics(): JVMMetricsResponse {
        return JVMMetricsResponse(
            heapUsage = getHeapUsageMetrics(),
            gcMetrics = getGCMetrics(),
            threadMetrics = getThreadMetrics(),
            classLoadingMetrics = getClassLoadingMetrics(),
            cpuMetrics = getCPUMetrics()
        )
    }
    
    @GetMapping("/jvm/gc/analysis")
    fun getGCAnalysis(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime
    ): GCAnalysisResponse {
        
        val gcEvents = gcEventRepository.findByTimestampBetween(from, to)
        val analysis = gcAnalyzer.analyze(gcEvents)
        
        return GCAnalysisResponse(
            period = Period(from, to),
            totalEvents = gcEvents.size,
            youngGCStats = analysis.youngGCStats,
            oldGCStats = analysis.oldGCStats,
            performance = analysis.performanceScore,
            recommendations = analysis.recommendations
        )
    }
    
    @GetMapping("/memory/leaks/suspects")
    fun getMemoryLeakSuspects(): List<MemoryLeakSuspect> {
        return memoryLeakDetector.detectPotentialLeaks()
    }
    
    @PostMapping("/profiling/start")
    fun startProfiling(@RequestBody request: ProfilingRequest): ProfilingSession {
        return profilingManager.startProfiling(request)
    }
    
    @GetMapping("/profiling/{sessionId}/result")
    fun getProfilingResult(@PathVariable sessionId: String): ProfilingResult {
        return profilingManager.getResult(sessionId)
    }
}

// 실시간 성능 WebSocket
@Component
class PerformanceWebSocketHandler : TextWebSocketHandler() {
    
    private val sessions = mutableSetOf<WebSocketSession>()
    
    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        sendCurrentMetrics(session)
    }
    
    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }
    
    @Scheduled(fixedRate = 5000) // 5초마다 업데이트
    fun broadcastMetrics() {
        val metrics = collectRealTimeMetrics()
        val message = objectMapper.writeValueAsString(metrics)
        
        sessions.removeAll { session ->
            try {
                session.sendMessage(TextMessage(message))
                false
            } catch (e: Exception) {
                true // 연결 끊긴 세션 제거
            }
        }
    }
}
```

---

## 실무 최적화 전략

### 1. 단계별 최적화 접근법

```kotlin
// 최적화 단계 매니저
class OptimizationPhaseManager {
    fun executeOptimizationPhases(): OptimizationResult {
        val phases = listOf(
            Phase1_Measurement(),
            Phase2_Analysis(),
            Phase3_Optimization(),
            Phase4_Validation(),
            Phase5_Monitoring()
        )
        
        val results = mutableMapOf<String, PhaseResult>()
        
        phases.forEach { phase ->
            println("실행 중: ${phase.name}")
            val result = phase.execute()
            results[phase.name] = result
            
            if (!result.success) {
                println("단계 실패: ${phase.name} - ${result.error}")
                return OptimizationResult.failure(phase.name, result.error)
            }
        }
        
        return OptimizationResult.success(results)
    }
}

// Phase 1: 현재 성능 측정
class Phase1_Measurement : OptimizationPhase {
    override val name = "성능 측정"
    
    override fun execute(): PhaseResult {
        val baseline = captureBaselineMetrics()
        val bottlenecks = identifyPerformanceBottlenecks()
        
        return PhaseResult.success(
            data = mapOf(
                "baseline" to baseline,
                "bottlenecks" to bottlenecks
            ),
            summary = "기준 성능 측정 완료: ${bottlenecks.size}개 병목 지점 식별"
        )
    }
    
    private fun captureBaselineMetrics(): BaselineMetrics {
        return BaselineMetrics(
            throughput = measureThroughput(),
            latency = measureLatency(),
            gcOverhead = measureGCOverhead(),
            memoryUtilization = measureMemoryUtilization(),
            cpuUtilization = measureCPUUtilization()
        )
    }
}

// Phase 2: 분석 및 진단
class Phase2_Analysis : OptimizationPhase {
    override val name = "성능 분석"
    
    override fun execute(): PhaseResult {
        val gcAnalysis = analyzeGCPerformance()
        val memoryAnalysis = analyzeMemoryUsage()
        val allocationAnalysis = analyzeAllocationPatterns()
        val threadAnalysis = analyzeThreadUsage()
        
        val prioritizedIssues = prioritizePerformanceIssues(
            gcAnalysis.issues + 
            memoryAnalysis.issues + 
            allocationAnalysis.issues +
            threadAnalysis.issues
        )
        
        return PhaseResult.success(
            data = mapOf(
                "gc_analysis" to gcAnalysis,
                "memory_analysis" to memoryAnalysis,
                "allocation_analysis" to allocationAnalysis,
                "thread_analysis" to threadAnalysis,
                "prioritized_issues" to prioritizedIssues
            ),
            summary = "${prioritizedIssues.size}개 성능 이슈 분석 완료"
        )
    }
}

// Phase 3: 최적화 적용
class Phase3_Optimization : OptimizationPhase {
    override val name = "최적화 적용"
    
    override fun execute(): PhaseResult {
        val optimizations = mutableListOf<AppliedOptimization>()
        
        // GC 최적화
        val gcOptimization = applyGCOptimizations()
        optimizations.add(gcOptimization)
        
        // 힙 크기 최적화
        val heapOptimization = applyHeapOptimizations()
        optimizations.add(heapOptimization)
        
        // JVM 플래그 최적화
        val flagOptimization = applyJVMFlagOptimizations()
        optimizations.add(flagOptimization)
        
        return PhaseResult.success(
            data = mapOf("optimizations" to optimizations),
            summary = "${optimizations.size}개 최적화 적용 완료"
        )
    }
}
```

### 2. 환경별 최적화 전략

```kotlin
// 환경별 최적화 전략
class EnvironmentOptimizationStrategy {
    fun getOptimizationStrategy(environment: Environment): OptimizationStrategy {
        return when (environment.type) {
            EnvironmentType.DEVELOPMENT -> DevelopmentOptimizationStrategy()
            EnvironmentType.TESTING -> TestingOptimizationStrategy()
            EnvironmentType.STAGING -> StagingOptimizationStrategy()
            EnvironmentType.PRODUCTION -> ProductionOptimizationStrategy()
        }
    }
}

// 프로덕션 최적화 전략
class ProductionOptimizationStrategy : OptimizationStrategy {
    override fun generateJVMFlags(requirements: Requirements): List<String> {
        return buildList {
            // 기본 힙 설정
            add("-Xms${requirements.heapSize}")
            add("-Xmx${requirements.heapSize}")
            
            // 프로덕션용 GC 설정
            add("-XX:+UseG1GC")
            add("-XX:MaxGCPauseMillis=100")
            add("-XX:G1HeapRegionSize=16m")
            add("-XX:InitiatingHeapOccupancyPercent=45")
            
            // 성능 최적화
            add("-server")
            add("-XX:+TieredCompilation")
            add("-XX:+UseStringDeduplication")
            add("-XX:+UseCompressedOops")
            
            // 안정성 향상
            add("-XX:+ExitOnOutOfMemoryError")
            add("-XX:+HeapDumpOnOutOfMemoryError")
            add("-XX:HeapDumpPath=/app/dumps/")
            
            // 모니터링
            add("-XX:+PrintGC")
            add("-XX:+PrintGCDetails")
            add("-XX:+PrintGCTimeStamps")
            add("-Xloggc:/app/logs/gc.log")
            add("-XX:+UseGCLogFileRotation")
            add("-XX:NumberOfGCLogFiles=10")
            add("-XX:GCLogFileSize=100M")
            
            // 보안
            add("-Djava.security.egd=file:/dev/./urandom")
            
            if (requirements.enableJFR) {
                // Java Flight Recorder (프로덕션 프로파일링)
                add("-XX:+FlightRecorder")
                add("-XX:StartFlightRecording=duration=1h,filename=/app/profiling/app.jfr")
            }
        }
    }
    
    override fun getMonitoringConfiguration(): MonitoringConfiguration {
        return MonitoringConfiguration(
            metricsExportInterval = Duration.ofSeconds(30),
            alertThresholds = ProductionAlertThresholds(
                maxGCOverhead = 5.0,
                maxHeapUtilization = 85.0,
                maxLatencyP99 = Duration.ofMillis(500)
            ),
            profilingEnabled = true,
            automaticHeapDump = true
        )
    }
}

// 개발 환경 최적화 전략
class DevelopmentOptimizationStrategy : OptimizationStrategy {
    override fun generateJVMFlags(requirements: Requirements): List<String> {
        return buildList {
            // 작은 힙 크기
            add("-Xms512m")
            add("-Xmx2g")
            
            // 빠른 시작을 위한 설정
            add("-XX:+UseG1GC")
            add("-XX:MaxGCPauseMillis=200")
            add("-XX:+UnlockExperimentalVMOptions")
            
            // 디버깅 지원
            add("-XX:+HeapDumpOnOutOfMemoryError")
            add("-XX:HeapDumpPath=./dumps/")
            add("-XX:+PrintCompilation")
            add("-XX:+TraceClassLoading")
            
            // 개발 편의성
            add("-XX:+PrintGCDetails")
            add("-Xloggc:gc-dev.log")
        }
    }
}
```

### 3. 지속적인 최적화 프로세스

```kotlin
// 지속적 최적화 매니저
class ContinuousOptimizationManager {
    
    @Scheduled(cron = "0 0 2 * * SUN") // 매주 일요일 새벽 2시
    fun performWeeklyOptimizationReview() {
        val weeklyReport = generateWeeklyPerformanceReport()
        val optimizationPlan = createOptimizationPlan(weeklyReport)
        
        if (optimizationPlan.hasActionableItems()) {
            scheduleOptimizationTasks(optimizationPlan)
            notifyOptimizationTeam(optimizationPlan)
        }
    }
    
    private fun generateWeeklyPerformanceReport(): WeeklyPerformanceReport {
        val endTime = LocalDateTime.now()
        val startTime = endTime.minusWeeks(1)
        
        return WeeklyPerformanceReport(
            period = Period(startTime, endTime),
            averageMetrics = calculateAverageMetrics(startTime, endTime),
            performanceTrends = analyzePerformanceTrends(startTime, endTime),
            regressions = identifyPerformanceRegressions(startTime, endTime),
            improvements = identifyPerformanceImprovements(startTime, endTime)
        )
    }
    
    private fun createOptimizationPlan(report: WeeklyPerformanceReport): OptimizationPlan {
        val tasks = mutableListOf<OptimizationTask>()
        
        // 성능 회귀 해결
        report.regressions.forEach { regression ->
            tasks.add(OptimizationTask(
                type = TaskType.REGRESSION_FIX,
                priority = Priority.HIGH,
                description = "성능 회귀 해결: ${regression.description}",
                estimatedEffort = regression.estimatedFixEffort,
                expectedBenefit = regression.impactLevel
            ))
        }
        
        // 지속적 개선
        if (report.averageMetrics.gcOverhead > 3.0) {
            tasks.add(OptimizationTask(
                type = TaskType.GC_OPTIMIZATION,
                priority = Priority.MEDIUM,
                description = "GC 오버헤드 개선 (현재: ${report.averageMetrics.gcOverhead}%)",
                estimatedEffort = Duration.ofHours(4),
                expectedBenefit = ImpactLevel.MEDIUM
            ))
        }
        
        return OptimizationPlan(
            tasks = tasks.sortedByDescending { it.priority },
            estimatedDuration = tasks.sumOf { it.estimatedEffort.toHours() },
            expectedROI = calculateExpectedROI(tasks)
        )
    }
}

// 성능 회귀 감지
class PerformanceRegressionDetector {
    fun detectRegressions(baseline: PerformanceBaseline): List<PerformanceRegression> {
        val current = captureCurrentMetrics()
        val regressions = mutableListOf<PerformanceRegression>()
        
        // 처리량 회귀 검사
        val throughputRegression = (baseline.throughput - current.throughput) / baseline.throughput
        if (throughputRegression > 0.05) { // 5% 이상 감소
            regressions.add(PerformanceRegression(
                metric = "throughput",
                baselineValue = baseline.throughput,
                currentValue = current.throughput,
                regressionPercent = throughputRegression * 100,
                severity = when {
                    throughputRegression > 0.20 -> Severity.CRITICAL
                    throughputRegression > 0.10 -> Severity.HIGH
                    else -> Severity.MEDIUM
                }
            ))
        }
        
        // 지연시간 회귀 검사
        val latencyRegression = (current.p99Latency - baseline.p99Latency) / baseline.p99Latency
        if (latencyRegression > 0.10) { // 10% 이상 증가
            regressions.add(PerformanceRegression(
                metric = "p99_latency",
                baselineValue = baseline.p99Latency.toDouble(),
                currentValue = current.p99Latency.toDouble(),
                regressionPercent = latencyRegression * 100,
                severity = when {
                    latencyRegression > 0.50 -> Severity.CRITICAL
                    latencyRegression > 0.25 -> Severity.HIGH
                    else -> Severity.MEDIUM
                }
            ))
        }
        
        return regressions
    }
}
```

---

## 트러블슈팅 가이드

### 1. 일반적인 성능 문제와 해결책

```kotlin
// 성능 문제 진단기
class PerformanceTroubleshooter {
    fun diagnosePerformanceIssue(symptoms: List<PerformanceSymptom>): DiagnosisResult {
        val possibleCauses = mutableListOf<PossibleCause>()
        
        symptoms.forEach { symptom ->
            possibleCauses.addAll(diagnoseSingleSymptom(symptom))
        }
        
        // 가능성이 높은 원인부터 정렬
        val rankedCauses = possibleCauses
            .groupBy { it.cause }
            .mapValues { (_, causes) -> causes.sumOf { it.confidence } }
            .toList()
            .sortedByDescending { it.second }
            .map { it.first }
        
        return DiagnosisResult(
            primaryCause = rankedCauses.firstOrNull(),
            allPossibleCauses = rankedCauses,
            recommendedActions = generateRecommendedActions(rankedCauses.take(3)),
            urgencyLevel = calculateUrgencyLevel(symptoms)
        )
    }
    
    private fun diagnoseSingleSymptom(symptom: PerformanceSymptom): List<PossibleCause> {
        return when (symptom.type) {
            SymptomType.HIGH_GC_OVERHEAD -> listOf(
                PossibleCause("힙 크기 부족", 0.8, "힙 크기를 늘리거나 메모리 사용량을 줄이세요"),
                PossibleCause("메모리 누수", 0.6, "힙 덤프를 분석하여 메모리 누수를 확인하세요"),
                PossibleCause("부적절한 GC 알고리즘", 0.4, "워크로드에 맞는 GC 알고리즘을 선택하세요")
            )
            
            SymptomType.HIGH_MEMORY_USAGE -> listOf(
                PossibleCause("메모리 누수", 0.9, "메모리 누수 감지 도구를 실행하세요"),
                PossibleCause("과도한 캐싱", 0.7, "캐시 크기와 TTL 설정을 검토하세요"),
                PossibleCause("대용량 객체 생성", 0.5, "할당 패턴을 분석하세요")
            )
            
            SymptomType.LONG_GC_PAUSES -> listOf(
                PossibleCause("큰 힙 크기", 0.8, "G1GC 사용 또는 힙 크기 조정을 고려하세요"),
                PossibleCause("Old Generation 압박", 0.7, "Young Generation 크기를 늘리세요"),
                PossibleCause("부적절한 GC 설정", 0.6, "GC 파라미터를 조정하세요")
            )
            
            SymptomType.HIGH_CPU_USAGE -> listOf(
                PossibleCause("과도한 GC", 0.7, "GC 로그를 분석하세요"),
                PossibleCause("비효율적 알고리즘", 0.6, "CPU 프로파일링을 실행하세요"),
                PossibleCause("스레드 경합", 0.5, "스레드 덤프를 분석하세요")
            )
            
            else -> emptyList()
        }
    }
}

// 응급 대응 가이드
class EmergencyResponseGuide {
    fun handleCriticalPerformanceIssue(issue: CriticalPerformanceIssue): EmergencyResponse {
        return when (issue.type) {
            CriticalIssueType.OUT_OF_MEMORY -> handleOutOfMemoryError(issue)
            CriticalIssueType.EXCESSIVE_GC -> handleExcessiveGC(issue)
            CriticalIssueType.APPLICATION_HANG -> handleApplicationHang(issue)
            CriticalIssueType.MEMORY_LEAK -> handleMemoryLeak(issue)
        }
    }
    
    private fun handleOutOfMemoryError(issue: CriticalPerformanceIssue): EmergencyResponse {
        val immediateActions = listOf(
            EmergencyAction(
                priority = 1,
                action = "힙 덤프 생성",
                command = "jcmd <pid> GC.run_finalization && jcmd <pid> VM.gc && jcmd <pid> GC.class_histogram",
                description = "메모리 상태 분석을 위한 힙 덤프 생성"
            ),
            EmergencyAction(
                priority = 2,
                action = "임시 힙 크기 증가",
                command = "애플리케이션 재시작 시 -Xmx 값을 2배로 증가",
                description = "즉시 메모리 부족 문제 완화"
            ),
            EmergencyAction(
                priority = 3,
                action = "메모리 사용량 분석",
                command = "jstat -gc <pid> 5s",
                description = "실시간 GC 상태 모니터링"
            )
        )
        
        val followUpActions = listOf(
            "힙 덤프 분석을 통한 메모리 누수 확인",
            "메모리 사용 패턴 최적화",
            "적절한 힙 크기 설정"
        )
        
        return EmergencyResponse(
            severity = Severity.CRITICAL,
            immediateActions = immediateActions,
            followUpActions = followUpActions,
            estimatedRecoveryTime = Duration.ofMinutes(30)
        )
    }
    
    private fun handleExcessiveGC(issue: CriticalPerformanceIssue): EmergencyResponse {
        val immediateActions = listOf(
            EmergencyAction(
                priority = 1,
                action = "GC 로그 수집",
                command = "tail -f gc.log | grep -E '(GC|Total time)'",
                description = "현재 GC 상태 확인"
            ),
            EmergencyAction(
                priority = 2,
                action = "메모리 사용률 확인",
                command = "jstat -gccapacity <pid>",
                description = "힙 공간 구성 확인"
            ),
            EmergencyAction(
                priority = 3,
                action = "임시 GC 설정 조정",
                command = "G1GC 사용 시 -XX:MaxGCPauseMillis 값 증가",
                description = "일시정지 시간보다 처리량 우선"
            )
        )
        
        return EmergencyResponse(
            severity = Severity.HIGH,
            immediateActions = immediateActions,
            followUpActions = listOf(
                "GC 알고리즘 재검토",
                "힙 크기 최적화",
                "할당 패턴 분석"
            ),
            estimatedRecoveryTime = Duration.ofMinutes(15)
        )
    }
}
```

### 2. 디버깅 도구 활용

```kotlin
// JVM 디버깅 도구 래퍼
class JVMDebuggingTools {
    fun generateHeapDump(pid: Int, outputPath: String): HeapDumpResult {
        val command = "jcmd $pid GC.run_finalization && jcmd $pid VM.gc && jcmd $pid GC.class_histogram > $outputPath"
        
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                HeapDumpResult.success(outputPath, File(outputPath).length())
            } else {
                HeapDumpResult.failure("힙 덤프 생성 실패: exit code $exitCode")
            }
        } catch (e: Exception) {
            HeapDumpResult.failure("힙 덤프 생성 중 오류: ${e.message}")
        }
    }
    
    fun generateThreadDump(pid: Int): ThreadDumpResult {
        val command = "jstack $pid"
        
        return try {
            val process = ProcessBuilder("sh", "-c", command).start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                val analysis = analyzeThreadDump(output)
                ThreadDumpResult.success(output, analysis)
            } else {
                ThreadDumpResult.failure("스레드 덤프 생성 실패")
            }
        } catch (e: Exception) {
            ThreadDumpResult.failure("스레드 덤프 생성 중 오류: ${e.message}")
        }
    }
    
    private fun analyzeThreadDump(dump: String): ThreadDumpAnalysis {
        val threads = parseThreads(dump)
        val blockedThreads = threads.filter { it.state == ThreadState.BLOCKED }
        val waitingThreads = threads.filter { it.state == ThreadState.WAITING }
        val deadlocks = detectDeadlocks(threads)
        
        return ThreadDumpAnalysis(
            totalThreads = threads.size,
            blockedThreads = blockedThreads.size,
            waitingThreads = waitingThreads.size,
            deadlocks = deadlocks,
            hotspots = identifyThreadHotspots(threads),
            recommendations = generateThreadRecommendations(threads)
        )
    }
}

// 성능 분석 리포트 생성기
class PerformanceReportGenerator {
    fun generateComprehensiveReport(
        gcAnalysis: GCLogAnalysis,
        heapAnalysis: HeapAnalysis,
        threadAnalysis: ThreadDumpAnalysis,
        profilingResults: ProfilingResults
    ): ComprehensivePerformanceReport {
        
        return ComprehensivePerformanceReport(
            executiveSummary = generateExecutiveSummary(gcAnalysis, heapAnalysis),
            gcPerformance = generateGCPerformanceSection(gcAnalysis),
            memoryAnalysis = generateMemoryAnalysisSection(heapAnalysis),
            threadAnalysis = generateThreadAnalysisSection(threadAnalysis),
            profilingInsights = generateProfilingSection(profilingResults),
            recommendations = generatePrioritizedRecommendations(
                gcAnalysis, heapAnalysis, threadAnalysis, profilingResults
            ),
            actionPlan = generateActionPlan(gcAnalysis, heapAnalysis, threadAnalysis)
        )
    }
    
    private fun generateExecutiveSummary(
        gc: GCLogAnalysis, 
        heap: HeapAnalysis
    ): ExecutiveSummary {
        val overallScore = calculateOverallPerformanceScore(gc, heap)
        val criticalIssues = identifyCriticalIssues(gc, heap)
        
        return ExecutiveSummary(
            overallScore = overallScore,
            grade = scoreToGrade(overallScore),
            criticalIssuesCount = criticalIssues.size,
            primaryRecommendation = criticalIssues.firstOrNull()?.recommendation ?: "현재 성능이 양호합니다",
            estimatedImprovementPotential = calculateImprovementPotential(criticalIssues)
        )
    }
}
```

### 3. 성능 문제 체크리스트

```kotlin
// 성능 문제 체크리스트
class PerformanceChecklistValidator {
    fun validatePerformanceConfiguration(): ChecklistResult {
        val checks = listOf(
            PerformanceCheck("JVM 힙 크기 적절성") { validateHeapSize() },
            PerformanceCheck("GC 알고리즘 선택") { validateGCAlgorithm() },
            PerformanceCheck("GC 로깅 활성화") { validateGCLogging() },
            PerformanceCheck("메모리 누수 모니터링") { validateMemoryLeakMonitoring() },
            PerformanceCheck("성능 메트릭 수집") { validateMetricsCollection() },
            PerformanceCheck("프로파일링 도구 설정") { validateProfilingTools() },
            PerformanceCheck("모니터링 알림 설정") { validateAlertConfiguration() },
            PerformanceCheck("백업 및 복구 절차") { validateBackupProcedures() }
        )
        
        val results = checks.map { check ->
            CheckResult(
                checkName = check.name,
                status = check.validator(),
                recommendations = getRecommendationsForCheck(check.name)
            )
        }
        
        return ChecklistResult(
            totalChecks = checks.size,
            passedChecks = results.count { it.status == CheckStatus.PASS },
            failedChecks = results.count { it.status == CheckStatus.FAIL },
            warningChecks = results.count { it.status == CheckStatus.WARNING },
            checkResults = results,
            overallStatus = calculateOverallStatus(results)
        )
    }
    
    private fun validateHeapSize(): CheckStatus {
        val heapMax = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max
        val systemMemory = getSystemMemory()
        val heapRatio = heapMax.toDouble() / systemMemory
        
        return when {
            heapRatio > 0.9 -> CheckStatus.FAIL  // 시스템 메모리의 90% 이상
            heapRatio > 0.8 -> CheckStatus.WARNING  // 80% 이상
            heapRatio < 0.3 -> CheckStatus.WARNING  // 30% 미만 (너무 작음)
            else -> CheckStatus.PASS
        }
    }
    
    private fun validateGCAlgorithm(): CheckStatus {
        val gcAlgorithm = detectCurrentGCAlgorithm()
        val heapSize = ManagementFactory.getMemoryMXBean().heapMemoryUsage.max / 1024 / 1024 / 1024 // GB
        
        return when {
            heapSize < 4 && gcAlgorithm != "G1GC" && gcAlgorithm != "Parallel GC" -> CheckStatus.WARNING
            heapSize >= 4 && gcAlgorithm != "G1GC" && gcAlgorithm != "ZGC" -> CheckStatus.WARNING
            gcAlgorithm == "Serial GC" && heapSize > 1 -> CheckStatus.FAIL
            else -> CheckStatus.PASS
        }
    }
}
```

---

## 결론

JVM 튜닝은 Java 애플리케이션의 성능을 극대화하는 핵심 기술입니다. 본 가이드에서 제시한 방법론과 도구들을 활용하여:

1. **체계적인 성능 분석**: GC 로그 분석부터 메모리 프로파일링까지
2. **단계적 최적화**: 측정 → 분석 → 최적화 → 검증의 순환 과정
3. **지속적인 모니터링**: 실시간 성능 추적 및 회귀 방지
4. **환경별 최적화**: 개발/테스트/프로덕션 환경에 맞는 설정
5. **응급 상황 대응**: 성능 장애 발생 시 신속한 문제 해결

이를 통해 안정적이고 고성능의 Java 애플리케이션을 구축하고 운영할 수 있습니다.

### 추가 리소스

- [OpenJDK GC Tuning Guide](https://docs.oracle.com/en/java/javase/11/gctuning/)
- [G1GC Tuning Guide](https://www.oracle.com/technical-resources/articles/java/g1gc.html)
- [JVM Performance Monitoring Tools](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/monitoring.html)
- [Memory Analysis Tools (MAT)](https://www.eclipse.org/mat/)

---

*본 가이드는 실제 성능 테스트 결과를 바탕으로 작성되었으며, 환경에 따라 결과가 달라질 수 있습니다. 프로덕션 환경에 적용하기 전에 충분한 테스트를 수행하시기 바랍니다.*