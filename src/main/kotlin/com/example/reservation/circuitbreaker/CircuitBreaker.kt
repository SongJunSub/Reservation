package com.example.reservation.circuitbreaker

import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * Kotlin 기반 Circuit Breaker 구현
 * 마이크로서비스 간 통신에서 장애 전파를 방지하는 핵심 패턴
 */
class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    
    // Circuit Breaker 상태
    private val state = AtomicReference(CircuitBreakerState.CLOSED)
    
    // 메트릭 추적
    private val metrics = CircuitBreakerMetrics()
    
    // Half-Open 상태에서의 테스트 호출 수
    private val halfOpenTestCalls = AtomicInteger(0)
    
    // 마지막 상태 변경 시간
    private val lastStateChangeTime = AtomicLong(System.currentTimeMillis())
    
    /**
     * Circuit Breaker를 통해 함수 실행
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        val currentState = getCurrentState()
        
        return when (currentState) {
            CircuitBreakerState.CLOSED -> executeInClosedState(operation)
            CircuitBreakerState.OPEN -> handleOpenState()
            CircuitBreakerState.HALF_OPEN -> executeInHalfOpenState(operation)
        }
    }
    
    /**
     * CLOSED 상태에서의 실행
     */
    private suspend fun <T> executeInClosedState(operation: suspend () -> T): T {
        return try {
            val result = withTimeout(config.callTimeout.toMillis()) {
                operation()
            }
            
            // 성공 시 메트릭 업데이트
            metrics.recordSuccess()
            result
            
        } catch (e: TimeoutCancellationException) {
            // 타임아웃 처리
            metrics.recordTimeout()
            checkIfShouldOpenCircuit()
            throw CircuitBreakerException("Operation timed out", e)
            
        } catch (e: Exception) {
            // 실패 처리
            metrics.recordFailure()
            checkIfShouldOpenCircuit()
            throw CircuitBreakerException("Operation failed", e)
        }
    }
    
    /**
     * OPEN 상태에서의 처리
     */
    private fun <T> handleOpenState(): T {
        metrics.recordRejected()
        throw CircuitBreakerOpenException("Circuit breaker is OPEN for '$name'")
    }
    
    /**
     * HALF_OPEN 상태에서의 실행
     */
    private suspend fun <T> executeInHalfOpenState(operation: suspend () -> T): T {
        // Half-Open 상태에서는 제한된 수의 테스트 호출만 허용
        val currentTestCalls = halfOpenTestCalls.incrementAndGet()
        
        if (currentTestCalls > config.halfOpenMaxCalls) {
            halfOpenTestCalls.decrementAndGet()
            metrics.recordRejected()
            throw CircuitBreakerOpenException("Half-open test calls limit exceeded")
        }
        
        return try {
            val result = withTimeout(config.callTimeout.toMillis()) {
                operation()
            }
            
            metrics.recordSuccess()
            
            // 성공적인 테스트 호출이 임계값에 도달하면 CLOSED로 전환
            if (metrics.getConsecutiveSuccesses() >= config.halfOpenSuccessThreshold) {
                transitionTo(CircuitBreakerState.CLOSED)
                println("✅ Circuit Breaker '$name': HALF_OPEN → CLOSED (연속 성공)")
            }
            
            result
            
        } catch (e: Exception) {
            metrics.recordFailure()
            // Half-Open 상태에서 실패하면 즉시 OPEN으로 전환
            transitionTo(CircuitBreakerState.OPEN)
            println("❌ Circuit Breaker '$name': HALF_OPEN → OPEN (테스트 실패)")
            throw CircuitBreakerException("Half-open test failed", e)
        }
    }
    
    /**
     * 현재 상태 확인 (시간 기반 자동 전환 포함)
     */
    private fun getCurrentState(): CircuitBreakerState {
        val currentState = state.get()
        val timeSinceLastChange = System.currentTimeMillis() - lastStateChangeTime.get()
        
        // OPEN 상태에서 일정 시간이 지나면 HALF_OPEN으로 전환
        if (currentState == CircuitBreakerState.OPEN && 
            timeSinceLastChange >= config.openTimeout.toMillis()) {
            
            if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                lastStateChangeTime.set(System.currentTimeMillis())
                halfOpenTestCalls.set(0)
                metrics.resetConsecutiveCounters()
                println("🔄 Circuit Breaker '$name': OPEN → HALF_OPEN (타임아웃)")
                return CircuitBreakerState.HALF_OPEN
            }
        }
        
        return currentState
    }
    
    /**
     * Circuit을 OPEN으로 전환해야 하는지 확인
     */
    private fun checkIfShouldOpenCircuit() {
        val failureRate = metrics.getFailureRate()
        val totalCalls = metrics.getTotalCalls()
        
        // 최소 호출 수와 실패율 임계값 확인
        if (totalCalls >= config.minimumNumberOfCalls && 
            failureRate >= config.failureRateThreshold) {
            
            transitionTo(CircuitBreakerState.OPEN)
            println("⚡ Circuit Breaker '$name': CLOSED → OPEN (실패율: ${"%.1f".format(failureRate * 100)}%)")
        }
    }
    
    /**
     * 상태 전환
     */
    private fun transitionTo(newState: CircuitBreakerState) {
        val oldState = state.getAndSet(newState)
        lastStateChangeTime.set(System.currentTimeMillis())
        
        when (newState) {
            CircuitBreakerState.CLOSED -> {
                metrics.reset()
                halfOpenTestCalls.set(0)
            }
            CircuitBreakerState.OPEN -> {
                // OPEN 상태로 전환 시 메트릭 유지 (분석용)
            }
            CircuitBreakerState.HALF_OPEN -> {
                halfOpenTestCalls.set(0)
                metrics.resetConsecutiveCounters()
            }
        }
        
        // 이벤트 리스너 호출 (모니터링용)
        config.eventListener?.onStateChange(name, oldState, newState)
    }
    
    /**
     * 강제로 상태 변경 (테스트/관리 목적)
     */
    fun forceState(newState: CircuitBreakerState) {
        transitionTo(newState)
        println("🔧 Circuit Breaker '$name': 강제 상태 변경 → $newState")
    }
    
    /**
     * 현재 상태 정보 조회
     */
    fun getStatus(): CircuitBreakerStatus {
        return CircuitBreakerStatus(
            name = name,
            state = state.get(),
            metrics = metrics.getSnapshot(),
            config = config,
            lastStateChangeTime = Instant.ofEpochMilli(lastStateChangeTime.get())
        )
    }
    
    /**
     * Circuit Breaker 리셋 (CLOSED 상태로 강제 전환)
     */
    fun reset() {
        forceState(CircuitBreakerState.CLOSED)
        println("🔄 Circuit Breaker '$name': 리셋 완료")
    }
}

/**
 * Circuit Breaker 상태
 */
enum class CircuitBreakerState {
    CLOSED,    // 정상 상태 (호출 허용)
    OPEN,      // 차단 상태 (호출 거부)
    HALF_OPEN  // 반개방 상태 (제한적 호출 허용)
}

/**
 * Circuit Breaker 설정
 */
data class CircuitBreakerConfig(
    val failureRateThreshold: Double = 0.5,           // 실패율 임계값 (50%)
    val minimumNumberOfCalls: Int = 10,               // 최소 호출 수
    val slidingWindowSize: Int = 20,                  // 슬라이딩 윈도우 크기
    val openTimeout: Duration = Duration.ofSeconds(60), // OPEN 상태 지속 시간
    val halfOpenMaxCalls: Int = 5,                    // Half-Open 상태 최대 테스트 호출
    val halfOpenSuccessThreshold: Int = 3,            // Half-Open → Closed 전환 성공 임계값
    val callTimeout: Duration = Duration.ofSeconds(5), // 개별 호출 타임아웃
    val eventListener: CircuitBreakerEventListener? = null
)

/**
 * Circuit Breaker 메트릭
 */
class CircuitBreakerMetrics {
    private val totalCalls = AtomicLong(0)
    private val successCalls = AtomicLong(0)
    private val failureCalls = AtomicLong(0)
    private val timeoutCalls = AtomicLong(0)
    private val rejectedCalls = AtomicLong(0)
    
    // 연속 성공/실패 카운터
    private val consecutiveSuccesses = AtomicInteger(0)
    private val consecutiveFailures = AtomicInteger(0)
    
    fun recordSuccess() {
        totalCalls.incrementAndGet()
        successCalls.incrementAndGet()
        consecutiveSuccesses.incrementAndGet()
        consecutiveFailures.set(0)
    }
    
    fun recordFailure() {
        totalCalls.incrementAndGet()
        failureCalls.incrementAndGet()
        consecutiveFailures.incrementAndGet()
        consecutiveSuccesses.set(0)
    }
    
    fun recordTimeout() {
        totalCalls.incrementAndGet()
        timeoutCalls.incrementAndGet()
        consecutiveFailures.incrementAndGet()
        consecutiveSuccesses.set(0)
    }
    
    fun recordRejected() {
        rejectedCalls.incrementAndGet()
    }
    
    fun getFailureRate(): Double {
        val total = totalCalls.get()
        if (total == 0L) return 0.0
        
        val failures = failureCalls.get() + timeoutCalls.get()
        return failures.toDouble() / total.toDouble()
    }
    
    fun getTotalCalls(): Long = totalCalls.get()
    fun getSuccessCalls(): Long = successCalls.get()
    fun getFailureCalls(): Long = failureCalls.get()
    fun getTimeoutCalls(): Long = timeoutCalls.get()
    fun getRejectedCalls(): Long = rejectedCalls.get()
    fun getConsecutiveSuccesses(): Int = consecutiveSuccesses.get()
    fun getConsecutiveFailures(): Int = consecutiveFailures.get()
    
    fun reset() {
        totalCalls.set(0)
        successCalls.set(0)
        failureCalls.set(0)
        timeoutCalls.set(0)
        rejectedCalls.set(0)
        consecutiveSuccesses.set(0)
        consecutiveFailures.set(0)
    }
    
    fun resetConsecutiveCounters() {
        consecutiveSuccesses.set(0)
        consecutiveFailures.set(0)
    }
    
    fun getSnapshot(): CircuitBreakerMetrics.Snapshot {
        return Snapshot(
            totalCalls = totalCalls.get(),
            successCalls = successCalls.get(),
            failureCalls = failureCalls.get(),
            timeoutCalls = timeoutCalls.get(),
            rejectedCalls = rejectedCalls.get(),
            consecutiveSuccesses = consecutiveSuccesses.get(),
            consecutiveFailures = consecutiveFailures.get(),
            failureRate = getFailureRate()
        )
    }
    
    data class Snapshot(
        val totalCalls: Long,
        val successCalls: Long,
        val failureCalls: Long,
        val timeoutCalls: Long,
        val rejectedCalls: Long,
        val consecutiveSuccesses: Int,
        val consecutiveFailures: Int,
        val failureRate: Double
    )
}

/**
 * Circuit Breaker 상태 정보
 */
data class CircuitBreakerStatus(
    val name: String,
    val state: CircuitBreakerState,
    val metrics: CircuitBreakerMetrics.Snapshot,
    val config: CircuitBreakerConfig,
    val lastStateChangeTime: Instant
)

/**
 * Circuit Breaker 이벤트 리스너
 */
interface CircuitBreakerEventListener {
    fun onStateChange(circuitBreakerName: String, from: CircuitBreakerState, to: CircuitBreakerState)
    fun onCallSuccess(circuitBreakerName: String, executionTime: Duration)
    fun onCallFailure(circuitBreakerName: String, executionTime: Duration, exception: Exception)
    fun onCallRejected(circuitBreakerName: String)
}

/**
 * Circuit Breaker 예외들
 */
sealed class CircuitBreakerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class CircuitBreakerOpenException(message: String) : CircuitBreakerException(message)

class CircuitBreakerTimeoutException(message: String, cause: Throwable) : CircuitBreakerException(message, cause)

/**
 * Circuit Breaker 레지스트리 (싱글톤 관리)
 */
object CircuitBreakerRegistry {
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()
    
    fun getOrCreate(name: String, config: CircuitBreakerConfig = CircuitBreakerConfig()): CircuitBreaker {
        return circuitBreakers.getOrPut(name) {
            CircuitBreaker(name, config)
        }
    }
    
    fun get(name: String): CircuitBreaker? = circuitBreakers[name]
    
    fun getAllStatus(): List<CircuitBreakerStatus> {
        return circuitBreakers.values.map { it.getStatus() }
    }
    
    fun remove(name: String): CircuitBreaker? = circuitBreakers.remove(name)
    
    fun clear() = circuitBreakers.clear()
}