package com.example.reservation.circuitbreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Java 기반 Circuit Breaker 구현
 * CompletableFuture와 전통적인 Java 패턴을 활용한 구현
 */
public class JavaCircuitBreaker {
    
    private final String name;
    private final JavaCircuitBreakerConfig config;
    private final AtomicReference<CircuitBreakerState> state;
    private final JavaCircuitBreakerMetrics metrics;
    private final AtomicInteger halfOpenTestCalls;
    private final AtomicLong lastStateChangeTime;
    
    public JavaCircuitBreaker(String name) {
        this(name, new JavaCircuitBreakerConfig());
    }
    
    public JavaCircuitBreaker(String name, JavaCircuitBreakerConfig config) {
        this.name = name;
        this.config = config;
        this.state = new AtomicReference<>(CircuitBreakerState.CLOSED);
        this.metrics = new JavaCircuitBreakerMetrics();
        this.halfOpenTestCalls = new AtomicInteger(0);
        this.lastStateChangeTime = new AtomicLong(System.currentTimeMillis());
    }
    
    /**
     * Circuit Breaker를 통해 동기 함수 실행
     */
    public <T> T execute(Supplier<T> operation) {
        CircuitBreakerState currentState = getCurrentState();
        
        switch (currentState) {
            case CLOSED:
                return executeInClosedState(operation);
            case OPEN:
                return handleOpenState();
            case HALF_OPEN:
                return executeInHalfOpenState(operation);
            default:
                throw new IllegalStateException("Unknown circuit breaker state: " + currentState);
        }
    }
    
    /**
     * Circuit Breaker를 통해 비동기 함수 실행
     */
    public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> operation) {
        CircuitBreakerState currentState = getCurrentState();
        
        switch (currentState) {
            case CLOSED:
                return executeAsyncInClosedState(operation);
            case OPEN:
                return handleAsyncOpenState();
            case HALF_OPEN:
                return executeAsyncInHalfOpenState(operation);
            default:
                return CompletableFuture.failedFuture(
                    new IllegalStateException("Unknown circuit breaker state: " + currentState)
                );
        }
    }
    
    /**
     * CLOSED 상태에서의 동기 실행
     */
    private <T> T executeInClosedState(Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = executeWithTimeout(operation);
            
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(executionTime);
            
            if (config.getEventListener() != null) {
                config.getEventListener().onCallSuccess(name, Duration.ofMillis(executionTime));
            }
            
            return result;
            
        } catch (CircuitBreakerTimeoutException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordTimeout(executionTime);
            checkIfShouldOpenCircuit();
            
            if (config.getEventListener() != null) {
                config.getEventListener().onCallFailure(name, Duration.ofMillis(executionTime), e);
            }
            
            throw new JavaCircuitBreakerException("Operation timed out", e);
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordFailure(executionTime);
            checkIfShouldOpenCircuit();
            
            if (config.getEventListener() != null) {
                config.getEventListener().onCallFailure(name, Duration.ofMillis(executionTime), e);
            }
            
            throw new JavaCircuitBreakerException("Operation failed", e);
        }
    }
    
    /**
     * CLOSED 상태에서의 비동기 실행
     */
    private <T> CompletableFuture<T> executeAsyncInClosedState(Supplier<CompletableFuture<T>> operation) {
        long startTime = System.currentTimeMillis();
        
        return operation.get()
            .orTimeout(config.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((result, throwable) -> {
                long executionTime = System.currentTimeMillis() - startTime;
                
                if (throwable == null) {
                    metrics.recordSuccess(executionTime);
                    if (config.getEventListener() != null) {
                        config.getEventListener().onCallSuccess(name, Duration.ofMillis(executionTime));
                    }
                } else {
                    metrics.recordFailure(executionTime);
                    checkIfShouldOpenCircuit();
                    if (config.getEventListener() != null) {
                        config.getEventListener().onCallFailure(name, Duration.ofMillis(executionTime), 
                            throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                    }
                }
            });
    }
    
    /**
     * OPEN 상태에서의 처리
     */
    private <T> T handleOpenState() {
        metrics.recordRejected();
        
        if (config.getEventListener() != null) {
            config.getEventListener().onCallRejected(name);
        }
        
        throw new JavaCircuitBreakerOpenException("Circuit breaker is OPEN for '" + name + "'");
    }
    
    /**
     * OPEN 상태에서의 비동기 처리
     */
    private <T> CompletableFuture<T> handleAsyncOpenState() {
        metrics.recordRejected();
        
        if (config.getEventListener() != null) {
            config.getEventListener().onCallRejected(name);
        }
        
        return CompletableFuture.failedFuture(
            new JavaCircuitBreakerOpenException("Circuit breaker is OPEN for '" + name + "'")
        );
    }
    
    /**
     * HALF_OPEN 상태에서의 동기 실행
     */
    private <T> T executeInHalfOpenState(Supplier<T> operation) {
        int currentTestCalls = halfOpenTestCalls.incrementAndGet();
        
        if (currentTestCalls > config.getHalfOpenMaxCalls()) {
            halfOpenTestCalls.decrementAndGet();
            metrics.recordRejected();
            throw new JavaCircuitBreakerOpenException("Half-open test calls limit exceeded");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            T result = executeWithTimeout(operation);
            
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordSuccess(executionTime);
            
            // 성공적인 테스트 호출이 임계값에 도달하면 CLOSED로 전환
            if (metrics.getConsecutiveSuccesses() >= config.getHalfOpenSuccessThreshold()) {
                transitionTo(CircuitBreakerState.CLOSED);
                System.out.println("✅ Circuit Breaker '" + name + "': HALF_OPEN → CLOSED (연속 성공)");
            }
            
            if (config.getEventListener() != null) {
                config.getEventListener().onCallSuccess(name, Duration.ofMillis(executionTime));
            }
            
            return result;
            
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.recordFailure(executionTime);
            
            // Half-Open 상태에서 실패하면 즉시 OPEN으로 전환
            transitionTo(CircuitBreakerState.OPEN);
            System.out.println("❌ Circuit Breaker '" + name + "': HALF_OPEN → OPEN (테스트 실패)");
            
            if (config.getEventListener() != null) {
                config.getEventListener().onCallFailure(name, Duration.ofMillis(executionTime), 
                    e instanceof Exception ? e : new RuntimeException(e));
            }
            
            throw new JavaCircuitBreakerException("Half-open test failed", e);
        }
    }
    
    /**
     * HALF_OPEN 상태에서의 비동기 실행
     */
    private <T> CompletableFuture<T> executeAsyncInHalfOpenState(Supplier<CompletableFuture<T>> operation) {
        int currentTestCalls = halfOpenTestCalls.incrementAndGet();
        
        if (currentTestCalls > config.getHalfOpenMaxCalls()) {
            halfOpenTestCalls.decrementAndGet();
            metrics.recordRejected();
            return CompletableFuture.failedFuture(
                new JavaCircuitBreakerOpenException("Half-open test calls limit exceeded")
            );
        }
        
        long startTime = System.currentTimeMillis();
        
        return operation.get()
            .orTimeout(config.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .whenComplete((result, throwable) -> {
                long executionTime = System.currentTimeMillis() - startTime;
                
                if (throwable == null) {
                    metrics.recordSuccess(executionTime);
                    
                    if (metrics.getConsecutiveSuccesses() >= config.getHalfOpenSuccessThreshold()) {
                        transitionTo(CircuitBreakerState.CLOSED);
                        System.out.println("✅ Circuit Breaker '" + name + "': HALF_OPEN → CLOSED (연속 성공)");
                    }
                    
                    if (config.getEventListener() != null) {
                        config.getEventListener().onCallSuccess(name, Duration.ofMillis(executionTime));
                    }
                } else {
                    metrics.recordFailure(executionTime);
                    transitionTo(CircuitBreakerState.OPEN);
                    System.out.println("❌ Circuit Breaker '" + name + "': HALF_OPEN → OPEN (테스트 실패)");
                    
                    if (config.getEventListener() != null) {
                        config.getEventListener().onCallFailure(name, Duration.ofMillis(executionTime),
                            throwable instanceof Exception ? (Exception) throwable : new RuntimeException(throwable));
                    }
                }
            });
    }
    
    /**
     * 타임아웃을 적용한 동기 실행
     */
    private <T> T executeWithTimeout(Supplier<T> operation) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation);
        
        try {
            return future.get(config.getCallTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new CircuitBreakerTimeoutException("Operation timed out", e);
        } catch (Exception e) {
            throw new RuntimeException("Operation execution failed", e);
        }
    }
    
    /**
     * 현재 상태 확인 (시간 기반 자동 전환 포함)
     */
    private CircuitBreakerState getCurrentState() {
        CircuitBreakerState currentState = state.get();
        long timeSinceLastChange = System.currentTimeMillis() - lastStateChangeTime.get();
        
        // OPEN 상태에서 일정 시간이 지나면 HALF_OPEN으로 전환
        if (currentState == CircuitBreakerState.OPEN && 
            timeSinceLastChange >= config.getOpenTimeout().toMillis()) {
            
            if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                lastStateChangeTime.set(System.currentTimeMillis());
                halfOpenTestCalls.set(0);
                metrics.resetConsecutiveCounters();
                System.out.println("🔄 Circuit Breaker '" + name + "': OPEN → HALF_OPEN (타임아웃)");
                return CircuitBreakerState.HALF_OPEN;
            }
        }
        
        return currentState;
    }
    
    /**
     * Circuit을 OPEN으로 전환해야 하는지 확인
     */
    private void checkIfShouldOpenCircuit() {
        double failureRate = metrics.getFailureRate();
        long totalCalls = metrics.getTotalCalls();
        
        // 최소 호출 수와 실패율 임계값 확인
        if (totalCalls >= config.getMinimumNumberOfCalls() && 
            failureRate >= config.getFailureRateThreshold()) {
            
            transitionTo(CircuitBreakerState.OPEN);
            System.out.printf("⚡ Circuit Breaker '%s': CLOSED → OPEN (실패율: %.1f%%)%n", 
                name, failureRate * 100);
        }
    }
    
    /**
     * 상태 전환
     */
    private void transitionTo(CircuitBreakerState newState) {
        CircuitBreakerState oldState = state.getAndSet(newState);
        lastStateChangeTime.set(System.currentTimeMillis());
        
        switch (newState) {
            case CLOSED:
                metrics.reset();
                halfOpenTestCalls.set(0);
                break;
            case OPEN:
                // OPEN 상태로 전환 시 메트릭 유지 (분석용)
                break;
            case HALF_OPEN:
                halfOpenTestCalls.set(0);
                metrics.resetConsecutiveCounters();
                break;
        }
        
        // 이벤트 리스너 호출 (모니터링용)
        if (config.getEventListener() != null) {
            config.getEventListener().onStateChange(name, oldState, newState);
        }
    }
    
    /**
     * 강제로 상태 변경 (테스트/관리 목적)
     */
    public void forceState(CircuitBreakerState newState) {
        transitionTo(newState);
        System.out.println("🔧 Circuit Breaker '" + name + "': 강제 상태 변경 → " + newState);
    }
    
    /**
     * 현재 상태 정보 조회
     */
    public JavaCircuitBreakerStatus getStatus() {
        return new JavaCircuitBreakerStatus(
            name,
            state.get(),
            metrics.getSnapshot(),
            config,
            Instant.ofEpochMilli(lastStateChangeTime.get())
        );
    }
    
    /**
     * Circuit Breaker 리셋 (CLOSED 상태로 강제 전환)
     */
    public void reset() {
        forceState(CircuitBreakerState.CLOSED);
        System.out.println("🔄 Circuit Breaker '" + name + "': 리셋 완료");
    }
    
    // Getter methods
    public String getName() { return name; }
    public CircuitBreakerState getState() { return state.get(); }
    public JavaCircuitBreakerMetrics getMetrics() { return metrics; }
}

/**
 * Java Circuit Breaker 설정 클래스
 */
public class JavaCircuitBreakerConfig {
    private double failureRateThreshold = 0.5;           // 실패율 임계값 (50%)
    private int minimumNumberOfCalls = 10;               // 최소 호출 수
    private int slidingWindowSize = 20;                  // 슬라이딩 윈도우 크기
    private Duration openTimeout = Duration.ofSeconds(60); // OPEN 상태 지속 시간
    private int halfOpenMaxCalls = 5;                    // Half-Open 상태 최대 테스트 호출
    private int halfOpenSuccessThreshold = 3;            // Half-Open → Closed 전환 성공 임계값
    private Duration callTimeout = Duration.ofSeconds(5); // 개별 호출 타임아웃
    private JavaCircuitBreakerEventListener eventListener; // 이벤트 리스너
    
    // Builder 패턴
    public static class Builder {
        private JavaCircuitBreakerConfig config = new JavaCircuitBreakerConfig();
        
        public Builder failureRateThreshold(double threshold) {
            config.failureRateThreshold = threshold;
            return this;
        }
        
        public Builder minimumNumberOfCalls(int calls) {
            config.minimumNumberOfCalls = calls;
            return this;
        }
        
        public Builder slidingWindowSize(int size) {
            config.slidingWindowSize = size;
            return this;
        }
        
        public Builder openTimeout(Duration timeout) {
            config.openTimeout = timeout;
            return this;
        }
        
        public Builder halfOpenMaxCalls(int calls) {
            config.halfOpenMaxCalls = calls;
            return this;
        }
        
        public Builder halfOpenSuccessThreshold(int threshold) {
            config.halfOpenSuccessThreshold = threshold;
            return this;
        }
        
        public Builder callTimeout(Duration timeout) {
            config.callTimeout = timeout;
            return this;
        }
        
        public Builder eventListener(JavaCircuitBreakerEventListener listener) {
            config.eventListener = listener;
            return this;
        }
        
        public JavaCircuitBreakerConfig build() {
            return config;
        }
    }
    
    // Getters
    public double getFailureRateThreshold() { return failureRateThreshold; }
    public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
    public int getSlidingWindowSize() { return slidingWindowSize; }
    public Duration getOpenTimeout() { return openTimeout; }
    public int getHalfOpenMaxCalls() { return halfOpenMaxCalls; }
    public int getHalfOpenSuccessThreshold() { return halfOpenSuccessThreshold; }
    public Duration getCallTimeout() { return callTimeout; }
    public JavaCircuitBreakerEventListener getEventListener() { return eventListener; }
}

/**
 * Java Circuit Breaker 메트릭 클래스
 */
public class JavaCircuitBreakerMetrics {
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong failureCalls = new AtomicLong(0);
    private final AtomicLong timeoutCalls = new AtomicLong(0);
    private final AtomicLong rejectedCalls = new AtomicLong(0);
    
    // 연속 성공/실패 카운터
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    // 응답 시간 메트릭
    private final AtomicLong totalExecutionTime = new AtomicLong(0);
    private final AtomicLong maxExecutionTime = new AtomicLong(0);
    
    public void recordSuccess(long executionTimeMs) {
        totalCalls.incrementAndGet();
        successCalls.incrementAndGet();
        consecutiveSuccesses.incrementAndGet();
        consecutiveFailures.set(0);
        
        updateExecutionTime(executionTimeMs);
    }
    
    public void recordFailure(long executionTimeMs) {
        totalCalls.incrementAndGet();
        failureCalls.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
        
        updateExecutionTime(executionTimeMs);
    }
    
    public void recordTimeout(long executionTimeMs) {
        totalCalls.incrementAndGet();
        timeoutCalls.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
        
        updateExecutionTime(executionTimeMs);
    }
    
    public void recordRejected() {
        rejectedCalls.incrementAndGet();
    }
    
    private void updateExecutionTime(long executionTimeMs) {
        totalExecutionTime.addAndGet(executionTimeMs);
        
        // 최대 실행 시간 업데이트 (CAS 사용)
        long currentMax = maxExecutionTime.get();
        while (executionTimeMs > currentMax) {
            if (maxExecutionTime.compareAndSet(currentMax, executionTimeMs)) {
                break;
            }
            currentMax = maxExecutionTime.get();
        }
    }
    
    public double getFailureRate() {
        long total = totalCalls.get();
        if (total == 0L) return 0.0;
        
        long failures = failureCalls.get() + timeoutCalls.get();
        return (double) failures / (double) total;
    }
    
    public double getAverageExecutionTime() {
        long total = totalCalls.get();
        if (total == 0L) return 0.0;
        
        return (double) totalExecutionTime.get() / (double) total;
    }
    
    public void reset() {
        totalCalls.set(0);
        successCalls.set(0);
        failureCalls.set(0);
        timeoutCalls.set(0);
        rejectedCalls.set(0);
        consecutiveSuccesses.set(0);
        consecutiveFailures.set(0);
        totalExecutionTime.set(0);
        maxExecutionTime.set(0);
    }
    
    public void resetConsecutiveCounters() {
        consecutiveSuccesses.set(0);
        consecutiveFailures.set(0);
    }
    
    public MetricsSnapshot getSnapshot() {
        return new MetricsSnapshot(
            totalCalls.get(),
            successCalls.get(),
            failureCalls.get(),
            timeoutCalls.get(),
            rejectedCalls.get(),
            consecutiveSuccesses.get(),
            consecutiveFailures.get(),
            getFailureRate(),
            getAverageExecutionTime(),
            maxExecutionTime.get()
        );
    }
    
    // Getters
    public long getTotalCalls() { return totalCalls.get(); }
    public long getSuccessCalls() { return successCalls.get(); }
    public long getFailureCalls() { return failureCalls.get(); }
    public long getTimeoutCalls() { return timeoutCalls.get(); }
    public long getRejectedCalls() { return rejectedCalls.get(); }
    public int getConsecutiveSuccesses() { return consecutiveSuccesses.get(); }
    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
    
    /**
     * 메트릭 스냅샷 클래스
     */
    public static class MetricsSnapshot {
        private final long totalCalls;
        private final long successCalls;
        private final long failureCalls;
        private final long timeoutCalls;
        private final long rejectedCalls;
        private final int consecutiveSuccesses;
        private final int consecutiveFailures;
        private final double failureRate;
        private final double averageExecutionTime;
        private final long maxExecutionTime;
        
        public MetricsSnapshot(long totalCalls, long successCalls, long failureCalls, 
                             long timeoutCalls, long rejectedCalls, int consecutiveSuccesses,
                             int consecutiveFailures, double failureRate, 
                             double averageExecutionTime, long maxExecutionTime) {
            this.totalCalls = totalCalls;
            this.successCalls = successCalls;
            this.failureCalls = failureCalls;
            this.timeoutCalls = timeoutCalls;
            this.rejectedCalls = rejectedCalls;
            this.consecutiveSuccesses = consecutiveSuccesses;
            this.consecutiveFailures = consecutiveFailures;
            this.failureRate = failureRate;
            this.averageExecutionTime = averageExecutionTime;
            this.maxExecutionTime = maxExecutionTime;
        }
        
        // Getters
        public long getTotalCalls() { return totalCalls; }
        public long getSuccessCalls() { return successCalls; }
        public long getFailureCalls() { return failureCalls; }
        public long getTimeoutCalls() { return timeoutCalls; }
        public long getRejectedCalls() { return rejectedCalls; }
        public int getConsecutiveSuccesses() { return consecutiveSuccesses; }
        public int getConsecutiveFailures() { return consecutiveFailures; }
        public double getFailureRate() { return failureRate; }
        public double getAverageExecutionTime() { return averageExecutionTime; }
        public long getMaxExecutionTime() { return maxExecutionTime; }
    }
}

/**
 * Java Circuit Breaker 상태 정보
 */
public class JavaCircuitBreakerStatus {
    private final String name;
    private final CircuitBreakerState state;
    private final JavaCircuitBreakerMetrics.MetricsSnapshot metrics;
    private final JavaCircuitBreakerConfig config;
    private final Instant lastStateChangeTime;
    
    public JavaCircuitBreakerStatus(String name, CircuitBreakerState state, 
                                  JavaCircuitBreakerMetrics.MetricsSnapshot metrics,
                                  JavaCircuitBreakerConfig config, Instant lastStateChangeTime) {
        this.name = name;
        this.state = state;
        this.metrics = metrics;
        this.config = config;
        this.lastStateChangeTime = lastStateChangeTime;
    }
    
    // Getters
    public String getName() { return name; }
    public CircuitBreakerState getState() { return state; }
    public JavaCircuitBreakerMetrics.MetricsSnapshot getMetrics() { return metrics; }
    public JavaCircuitBreakerConfig getConfig() { return config; }
    public Instant getLastStateChangeTime() { return lastStateChangeTime; }
}

/**
 * Java Circuit Breaker 이벤트 리스너
 */
public interface JavaCircuitBreakerEventListener {
    void onStateChange(String circuitBreakerName, CircuitBreakerState from, CircuitBreakerState to);
    void onCallSuccess(String circuitBreakerName, Duration executionTime);
    void onCallFailure(String circuitBreakerName, Duration executionTime, Exception exception);
    void onCallRejected(String circuitBreakerName);
}

/**
 * Java Circuit Breaker 예외들
 */
class JavaCircuitBreakerException extends RuntimeException {
    public JavaCircuitBreakerException(String message) {
        super(message);
    }
    
    public JavaCircuitBreakerException(String message, Throwable cause) {
        super(message, cause);
    }
}

class JavaCircuitBreakerOpenException extends JavaCircuitBreakerException {
    public JavaCircuitBreakerOpenException(String message) {
        super(message);
    }
}

class CircuitBreakerTimeoutException extends JavaCircuitBreakerException {
    public CircuitBreakerTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}