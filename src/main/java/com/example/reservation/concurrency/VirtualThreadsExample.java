package com.example.reservation.concurrency;

import com.example.reservation.controller.CreateReservationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Java 21 Virtual Threads 예제 및 데모
 * Virtual Threads의 특징과 사용 패턴을 보여주는 교육용 코드
 */
@Component
public class VirtualThreadsExample {

    /**
     * Virtual Threads 기본 사용법 데모
     */
    public void demonstrateBasicUsage() {
        System.out.println("🚀 Virtual Threads 기본 사용법 데모");
        System.out.println("=".repeat(50));
        
        // 1. 단일 Virtual Thread 생성
        singleVirtualThreadExample();
        
        // 2. 다중 Virtual Thread 생성
        multipleVirtualThreadsExample();
        
        // 3. Virtual Thread Executor 사용
        virtualThreadExecutorExample();
        
        // 4. 구조화된 동시성 (Structured Concurrency)
        structuredConcurrencyExample();
    }
    
    private void singleVirtualThreadExample() {
        System.out.println("\n1️⃣ 단일 Virtual Thread 예제:");
        
        // Virtual Thread 생성 및 실행
        Thread virtualThread = Thread.ofVirtual()
            .name("virtual-worker-1")
            .start(() -> {
                System.out.println("Virtual Thread 실행 중: " + Thread.currentThread());
                simulateIOWork(1000); // 1초 I/O 작업 시뮬레이션
                System.out.println("Virtual Thread 작업 완료");
            });
        
        try {
            virtualThread.join(); // 작업 완료 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void multipleVirtualThreadsExample() {
        System.out.println("\n2️⃣ 다중 Virtual Thread 예제:");
        
        List<Thread> threads = new ArrayList<>();
        int threadCount = 10;
        
        long startTime = System.currentTimeMillis();
        
        // 10개의 Virtual Thread 생성
        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            Thread thread = Thread.ofVirtual()
                .name("virtual-worker-" + i)
                .start(() -> {
                    System.out.printf("Task %d 시작 (스레드: %s)%n", 
                        taskId, Thread.currentThread().getName());
                    simulateIOWork(500 + (taskId * 100)); // 가변 I/O 시간
                    System.out.printf("Task %d 완료%n", taskId);
                });
            threads.add(thread);
        }
        
        // 모든 스레드 완료 대기
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        long endTime = System.currentTimeMillis();
        System.out.printf("총 실행 시간: %d ms%n", endTime - startTime);
    }
    
    private void virtualThreadExecutorExample() {
        System.out.println("\n3️⃣ Virtual Thread Executor 예제:");
        
        // Virtual Thread Executor 생성
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            
            // 20개의 비동기 작업 제출
            for (int i = 0; i < 20; i++) {
                final int taskId = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    simulateIOWork(200);
                    return String.format("Task %d 결과 (스레드: %s)", 
                        taskId, Thread.currentThread().getName());
                }, executor);
                futures.add(future);
            }
            
            // 모든 결과 수집
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            try {
                allFutures.join();
                System.out.println("모든 작업 완료!");
                
                // 결과 출력
                futures.forEach(future -> {
                    try {
                        System.out.println("  " + future.get());
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("작업 실행 중 오류: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                System.err.println("작업 실행 중 오류: " + e.getMessage());
            }
        }
    }
    
    private void structuredConcurrencyExample() {
        System.out.println("\n4️⃣ 구조화된 동시성 예제:");
        
        // Note: 실제 Structured Concurrency는 Java 19+ Preview 기능
        // 여기서는 개념적 예제를 보여줌
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<String> userDataFuture = CompletableFuture.supplyAsync(() -> {
                simulateIOWork(300);
                return "사용자 데이터";
            }, executor);
            
            CompletableFuture<String> reservationDataFuture = CompletableFuture.supplyAsync(() -> {
                simulateIOWork(250);
                return "예약 데이터";
            }, executor);
            
            CompletableFuture<String> paymentDataFuture = CompletableFuture.supplyAsync(() -> {
                simulateIOWork(400);
                return "결제 데이터";
            }, executor);
            
            // 모든 데이터 수집 후 처리
            CompletableFuture<String> combinedResult = userDataFuture
                .thenCombine(reservationDataFuture, (user, reservation) -> user + " + " + reservation)
                .thenCombine(paymentDataFuture, (combined, payment) -> combined + " + " + payment);
            
            try {
                String result = combinedResult.get(2, TimeUnit.SECONDS);
                System.out.println("통합 결과: " + result);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("구조화된 동시성 실행 중 오류: " + e.getMessage());
            }
        }
    }
    
    /**
     * Virtual Threads vs Platform Threads 성능 비교
     */
    public void compareVirtualVsPlatformThreads() {
        System.out.println("\n🔥 Virtual Threads vs Platform Threads 성능 비교");
        System.out.println("=".repeat(60));
        
        int taskCount = 1000;
        
        // Platform Threads 테스트
        long platformThreadsTime = testPlatformThreads(taskCount);
        
        // 메모리 정리
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Virtual Threads 테스트
        long virtualThreadsTime = testVirtualThreads(taskCount);
        
        // 결과 비교
        printPerformanceComparison(taskCount, platformThreadsTime, virtualThreadsTime);
    }
    
    private long testPlatformThreads(int taskCount) {
        System.out.println("🧵 Platform Threads 테스트 중...");
        
        AtomicInteger completedTasks = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        // 스레드 풀 크기 제한 (OutOfMemoryError 방지)
        int maxThreads = Math.min(taskCount, 200);
        try (ExecutorService executor = Executors.newFixedThreadPool(maxThreads)) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    simulateIOWork(100 + (taskId % 50)); // 가변 I/O 시간
                    completedTasks.incrementAndGet();
                }, executor);
                futures.add(future);
            }
            
            // 모든 작업 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        System.out.printf("  완료된 작업: %d/%d%n", completedTasks.get(), taskCount);
        System.out.printf("  실행 시간: %d ms%n", executionTime);
        
        return executionTime;
    }
    
    private long testVirtualThreads(int taskCount) {
        System.out.println("⚡ Virtual Threads 테스트 중...");
        
        AtomicInteger completedTasks = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    simulateIOWork(100 + (taskId % 50)); // 가변 I/O 시간
                    completedTasks.incrementAndGet();
                }, executor);
                futures.add(future);
            }
            
            // 모든 작업 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        System.out.printf("  완료된 작업: %d/%d%n", completedTasks.get(), taskCount);
        System.out.printf("  실행 시간: %d ms%n", executionTime);
        
        return executionTime;
    }
    
    private void printPerformanceComparison(int taskCount, long platformTime, long virtualTime) {
        System.out.println("\n📊 성능 비교 결과:");
        System.out.println("-".repeat(30));
        
        System.out.printf("작업 수: %d%n", taskCount);
        System.out.printf("Platform Threads: %d ms%n", platformTime);
        System.out.printf("Virtual Threads: %d ms%n", virtualTime);
        
        if (virtualTime < platformTime) {
            double improvement = ((double) (platformTime - virtualTime) / platformTime) * 100;
            System.out.printf("Virtual Threads가 %.1f%% 빠름%n", improvement);
        } else {
            double degradation = ((double) (virtualTime - platformTime) / virtualTime) * 100;
            System.out.printf("Platform Threads가 %.1f%% 빠름%n", degradation);
        }
        
        // 처리량 계산
        double platformThroughput = (double) taskCount / platformTime * 1000;
        double virtualThroughput = (double) taskCount / virtualTime * 1000;
        
        System.out.printf("Platform Threads 처리량: %.1f tasks/sec%n", platformThroughput);
        System.out.printf("Virtual Threads 처리량: %.1f tasks/sec%n", virtualThroughput);
    }
    
    /**
     * Virtual Threads 메모리 사용량 분석
     */
    public void analyzeMemoryUsage() {
        System.out.println("\n🧠 Virtual Threads 메모리 사용량 분석");
        System.out.println("=".repeat(50));
        
        // 초기 메모리 상태
        long initialMemory = getMemoryUsage();
        System.out.printf("초기 메모리 사용량: %d MB%n", initialMemory);
        
        int threadCount = 10000; // 대량의 Virtual Thread 생성
        System.out.printf("%d개의 Virtual Thread 생성 중...%n", threadCount);
        
        List<Thread> threads = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        // 대량의 Virtual Thread 생성
        for (int i = 0; i < threadCount; i++) {
            Thread thread = Thread.ofVirtual()
                .name("memory-test-" + i)
                .start(() -> {
                    try {
                        // 잠시 대기 (메모리 사용량 측정을 위해)
                        Thread.sleep(Duration.ofSeconds(5));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            threads.add(thread);
        }
        
        // Virtual Thread 생성 완료 후 메모리 사용량
        long memoryAfterCreation = getMemoryUsage();
        System.out.printf("Virtual Thread 생성 후 메모리: %d MB (+%d MB)%n", 
            memoryAfterCreation, memoryAfterCreation - initialMemory);
        
        try {
            // 모든 스레드 완료 대기
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        
        // 최종 메모리 사용량
        System.gc(); // 강제 GC
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long finalMemory = getMemoryUsage();
        System.out.printf("작업 완료 후 메모리: %d MB%n", finalMemory);
        System.out.printf("총 실행 시간: %d ms%n", endTime - startTime);
        
        // 메모리 효율성 분석
        double memoryPerThread = (double) (memoryAfterCreation - initialMemory) / threadCount;
        System.out.printf("Virtual Thread당 메모리 사용량: %.2f KB%n", memoryPerThread * 1024);
        
        // Platform Thread와 비교 (참고용)
        System.out.println("\n💡 참고: Platform Thread는 일반적으로 1-8MB 스택 메모리 사용");
        System.out.printf("Virtual Thread는 Platform Thread 대비 %.0f배 메모리 효율적%n", 
            (2 * 1024) / (memoryPerThread * 1024)); // 2MB Platform Thread 기준
    }
    
    /**
     * I/O 작업 시뮬레이션
     */
    private void simulateIOWork(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 현재 메모리 사용량 반환 (MB 단위)
     */
    private long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * Virtual Threads 활용 패턴 예제
     */
    public void demonstrateUsagePatterns() {
        System.out.println("\n🎯 Virtual Threads 활용 패턴");
        System.out.println("=".repeat(40));
        
        // 1. HTTP 클라이언트 요청 병렬 처리
        parallelHttpRequestsPattern();
        
        // 2. 데이터베이스 I/O 병렬 처리
        parallelDatabaseIOPattern();
        
        // 3. 파일 처리 병렬화
        parallelFileProcessingPattern();
    }
    
    private void parallelHttpRequestsPattern() {
        System.out.println("\n1️⃣ HTTP 요청 병렬 처리 패턴:");
        
        List<String> urls = List.of(
            "https://api.example1.com/data",
            "https://api.example2.com/data",
            "https://api.example3.com/data",
            "https://api.example4.com/data",
            "https://api.example5.com/data"
        );
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    // HTTP 요청 시뮬레이션
                    simulateIOWork(200 + (int)(Math.random() * 300));
                    return String.format("Response from %s (Thread: %s)", 
                        url, Thread.currentThread().getName());
                }, executor))
                .toList();
            
            // 모든 응답 수집
            CompletableFuture<List<String>> allResponses = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            ).thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
            
            try {
                List<String> responses = allResponses.get(5, TimeUnit.SECONDS);
                responses.forEach(response -> System.out.println("  " + response));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                System.err.println("HTTP 요청 처리 중 오류: " + e.getMessage());
            }
        }
    }
    
    private void parallelDatabaseIOPattern() {
        System.out.println("\n2️⃣ 데이터베이스 I/O 병렬 처리 패턴:");
        
        List<Integer> userIds = IntStream.rangeClosed(1, 10).boxed().toList();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<String>> futures = userIds.stream()
                .map(userId -> CompletableFuture.supplyAsync(() -> {
                    // 데이터베이스 쿼리 시뮬레이션
                    simulateIOWork(100 + (userId * 10));
                    
                    // 예약 데이터 생성 시뮬레이션
                    CreateReservationRequest request = new CreateReservationRequest(
                        "User " + userId,
                        "Room " + (userId % 10 + 1),
                        "2024-12-25",
                        "2024-12-27",
                        200.0 + userId
                    );
                    
                    return String.format("User %d 예약 처리 완료 (Thread: %s)", 
                        userId, Thread.currentThread().getName());
                }, executor))
                .toList();
            
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                futures.forEach(future -> {
                    try {
                        System.out.println("  " + future.get());
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("데이터베이스 처리 중 오류: " + e.getMessage());
                    }
                });
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("데이터베이스 병렬 처리 중 오류: " + e.getMessage());
            }
        }
    }
    
    private void parallelFileProcessingPattern() {
        System.out.println("\n3️⃣ 파일 처리 병렬화 패턴:");
        
        List<String> filenames = List.of(
            "reservations.csv", "guests.csv", "rooms.csv", 
            "payments.csv", "reports.csv"
        );
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicLong totalProcessedBytes = new AtomicLong(0);
            
            List<CompletableFuture<Long>> futures = filenames.stream()
                .map(filename -> CompletableFuture.supplyAsync(() -> {
                    // 파일 처리 시뮬레이션
                    simulateIOWork(150 + (int)(Math.random() * 200));
                    
                    long fileSize = 1024 * (1 + (int)(Math.random() * 100)); // 1-100KB
                    totalProcessedBytes.addAndGet(fileSize);
                    
                    System.out.printf("  %s 처리 완료 (%d bytes, Thread: %s)%n", 
                        filename, fileSize, Thread.currentThread().getName());
                    
                    return fileSize;
                }, executor))
                .toList();
            
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                System.out.printf("총 처리된 파일 크기: %d bytes%n", totalProcessedBytes.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("파일 처리 중 오류: " + e.getMessage());
            }
        }
    }
}