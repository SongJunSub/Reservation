package com.example.reservation.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 캐시 서비스 (Java)
 * 
 * 기능:
 * 1. 프로그래밍 방식 캐시 조작
 * 2. 캐시 워밍업 및 사전 로딩
 * 3. 캐시 메트릭 수집
 * 4. 대량 캐시 작업 (배치)
 * 
 * Java 특징:
 * - 명시적 제네릭 타입 선언
 * - 전통적인 try-catch 예외 처리
 * - Stream API를 통한 컬렉션 처리
 * - 함수형 인터페이스 활용 (Supplier)
 */
@Service
public class CacheServiceJava {

    private static final Logger logger = LoggerFactory.getLogger(CacheServiceJava.class);
    private static final String CACHE_METRICS_PREFIX = "cache:metrics:";
    private static final String CACHE_HIT_SUFFIX = ":hits";
    private static final String CACHE_MISS_SUFFIX = ":misses";

    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, Long> metricsRedisTemplate;

    public CacheServiceJava(
            CacheManager cacheManager,
            RedisTemplate<String, Object> redisTemplate,
            RedisTemplate<String, Long> metricsRedisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
        this.metricsRedisTemplate = metricsRedisTemplate;
    }

    /**
     * 캐시에서 값 조회 (타입 안전)
     * Java 제네릭과 명시적 캐스팅
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromCache(String cacheName, String key, Class<T> type) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper valueWrapper = cache.get(key);
                if (valueWrapper != null) {
                    recordCacheHit(cacheName);
                    logger.debug("캐시 히트: {} - {}", cacheName, key);
                    Object value = valueWrapper.get();
                    return type.isInstance(value) ? (T) value : null;
                }
            }
            
            recordCacheMiss(cacheName);
            logger.debug("캐시 미스: {} - {}", cacheName, key);
            return null;
            
        } catch (Exception ex) {
            logger.warn("캐시 조회 실패: {} - {} : {}", cacheName, key, ex.getMessage());
            recordCacheMiss(cacheName);
            return null;
        }
    }

    /**
     * 캐시에 값 저장
     * Java의 null 체크와 명시적 조건문
     */
    public void putToCache(String cacheName, String key, Object value) {
        if (value == null) {
            logger.debug("null 값은 캐시하지 않음: {} - {}", cacheName, key);
            return;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
                logger.debug("캐시 저장: {} - {}", cacheName, key);
            }
        } catch (Exception ex) {
            logger.error("캐시 저장 실패: {} - {} : {}", cacheName, key, ex.getMessage());
        }
    }

    /**
     * 조건부 캐시 저장 (키가 존재하지 않을 때만)
     * Java의 명시적 조건 처리
     */
    public boolean putIfAbsent(String cacheName, String key, Object value) {
        if (value == null) {
            return false;
        }

        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Cache.ValueWrapper existingValue = cache.get(key);
                if (existingValue == null) {
                    cache.put(key, value);
                    logger.debug("조건부 캐시 저장: {} - {}", cacheName, key);
                    return true;
                } else {
                    logger.debug("캐시 키 이미 존재: {} - {}", cacheName, key);
                    return false;
                }
            }
            return false;
        } catch (Exception ex) {
            logger.error("조건부 캐시 저장 실패: {} - {} : {}", cacheName, key, ex.getMessage());
            return false;
        }
    }

    /**
     * 캐시에서 값 삭제
     */
    public void evictFromCache(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                logger.debug("캐시 삭제: {} - {}", cacheName, key);
            }
        } catch (Exception ex) {
            logger.error("캐시 삭제 실패: {} - {} : {}", cacheName, key, ex.getMessage());
        }
    }

    /**
     * 전체 캐시 클리어
     */
    public void clearCache(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                logger.info("캐시 전체 삭제: {}", cacheName);
            }
        } catch (Exception ex) {
            logger.error("캐시 전체 삭제 실패: {} : {}", cacheName, ex.getMessage());
        }
    }

    /**
     * 모든 캐시 클리어
     */
    public void clearAllCaches() {
        try {
            Collection<String> cacheNames = cacheManager.getCacheNames();
            for (String cacheName : cacheNames) {
                clearCache(cacheName);
            }
            logger.info("모든 캐시 삭제 완료");
        } catch (Exception ex) {
            logger.error("모든 캐시 삭제 실패: {}", ex.getMessage());
        }
    }

    /**
     * 캐시 존재 여부 확인
     */
    public boolean exists(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            return cache != null && cache.get(key) != null;
        } catch (Exception ex) {
            logger.warn("캐시 존재 확인 실패: {} - {} : {}", cacheName, key, ex.getMessage());
            return false;
        }
    }

    /**
     * TTL 기반 캐시 저장
     * Redis 특화 기능
     */
    public void putWithTtl(String cacheName, String key, Object value, Duration ttl) {
        try {
            String redisKey = buildRedisKey(cacheName, key);
            redisTemplate.opsForValue().set(redisKey, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
            logger.debug("TTL 캐시 저장: {} - {} (TTL: {})", cacheName, key, ttl);
        } catch (Exception ex) {
            logger.error("TTL 캐시 저장 실패: {} - {} : {}", cacheName, key, ex.getMessage());
        }
    }

    /**
     * 대량 캐시 조회
     * Java Stream API 활용
     */
    public Map<String, Object> multiGet(String cacheName, List<String> keys) {
        return keys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> getFromCache(cacheName, key, Object.class),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ))
                .entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    /**
     * 대량 캐시 저장
     */
    public void multiPut(String cacheName, Map<String, Object> keyValuePairs) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                keyValuePairs.forEach(cache::put);
                logger.debug("대량 캐시 저장: {} - {} items", cacheName, keyValuePairs.size());
            }
        } catch (Exception ex) {
            logger.error("대량 캐시 저장 실패: {} : {}", cacheName, ex.getMessage());
        }
    }

    /**
     * 패턴 기반 캐시 삭제
     * Redis 특화 기능
     */
    public void evictByPattern(String cacheName, String keyPattern) {
        try {
            String pattern = buildRedisKey(cacheName, keyPattern);
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.debug("패턴 기반 캐시 삭제: {} - {} keys", pattern, keys.size());
            }
        } catch (Exception ex) {
            logger.error("패턴 기반 캐시 삭제 실패: {} - {} : {}", cacheName, keyPattern, ex.getMessage());
        }
    }

    /**
     * 캐시 워밍업
     * Java 함수형 인터페이스 (Supplier) 활용
     */
    public void warmUpCache(String cacheName, Supplier<Map<String, Object>> dataLoader) {
        try {
            logger.info("캐시 워밍업 시작: {}", cacheName);
            long startTime = System.currentTimeMillis();
            
            Map<String, Object> data = dataLoader.get();
            multiPut(cacheName, data);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("캐시 워밍업 완료: {} - {} items in {}ms", cacheName, data.size(), duration);
            
            recordCacheWarmup(cacheName, data.size(), duration);
        } catch (Exception ex) {
            logger.error("캐시 워밍업 실패: {} : {}", cacheName, ex.getMessage());
        }
    }

    /**
     * 캐시 메트릭 조회
     */
    public CacheMetricsJava getCacheMetrics(String cacheName) {
        try {
            Long hits = metricsRedisTemplate.opsForValue().get(CACHE_METRICS_PREFIX + cacheName + CACHE_HIT_SUFFIX);
            Long misses = metricsRedisTemplate.opsForValue().get(CACHE_METRICS_PREFIX + cacheName + CACHE_MISS_SUFFIX);
            
            long hitCount = hits != null ? hits : 0L;
            long missCount = misses != null ? misses : 0L;
            long total = hitCount + missCount;
            double hitRate = total > 0 ? (double) hitCount / (double) total : 0.0;
            
            return new CacheMetricsJava(
                    cacheName,
                    hitCount,
                    missCount,
                    hitRate,
                    LocalDateTime.now()
            );
        } catch (Exception ex) {
            logger.error("캐시 메트릭 조회 실패: {} : {}", cacheName, ex.getMessage());
            return new CacheMetricsJava(cacheName, 0L, 0L, 0.0, LocalDateTime.now());
        }
    }

    /**
     * 모든 캐시 메트릭 조회
     */
    public List<CacheMetricsJava> getAllCacheMetrics() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        return cacheNames.stream()
                .map(this::getCacheMetrics)
                .collect(Collectors.toList());
    }

    // === Private 헬퍼 메서드들 ===

    private String buildRedisKey(String cacheName, String key) {
        return "reservation:" + cacheName + ":" + key;
    }

    private void recordCacheHit(String cacheName) {
        try {
            metricsRedisTemplate.opsForValue().increment(CACHE_METRICS_PREFIX + cacheName + CACHE_HIT_SUFFIX);
        } catch (Exception ex) {
            logger.debug("캐시 히트 메트릭 기록 실패: {}", ex.getMessage());
        }
    }

    private void recordCacheMiss(String cacheName) {
        try {
            metricsRedisTemplate.opsForValue().increment(CACHE_METRICS_PREFIX + cacheName + CACHE_MISS_SUFFIX);
        } catch (Exception ex) {
            logger.debug("캐시 미스 메트릭 기록 실패: {}", ex.getMessage());
        }
    }

    private void recordCacheWarmup(String cacheName, int itemCount, long durationMs) {
        try {
            String metricsKey = CACHE_METRICS_PREFIX + cacheName + ":warmup";
            Map<String, Object> warmupInfo = new HashMap<>();
            warmupInfo.put("itemCount", itemCount);
            warmupInfo.put("durationMs", durationMs);
            warmupInfo.put("timestamp", LocalDateTime.now().toString());
            
            redisTemplate.opsForValue().set(metricsKey, warmupInfo, Duration.ofDays(1));
        } catch (Exception ex) {
            logger.debug("캐시 워밍업 메트릭 기록 실패: {}", ex.getMessage());
        }
    }
}

/**
 * 캐시 메트릭 클래스 (Java)
 * 
 * Java 특징:
 * - 전통적인 클래스 정의와 getter 메서드
 * - final 필드를 통한 불변성
 * - 계산된 속성을 위한 메서드 정의
 */
class CacheMetricsJava {
    private final String cacheName;
    private final long hits;
    private final long misses;
    private final double hitRate;
    private final LocalDateTime lastUpdated;

    public CacheMetricsJava(String cacheName, long hits, long misses, double hitRate, LocalDateTime lastUpdated) {
        this.cacheName = cacheName;
        this.hits = hits;
        this.misses = misses;
        this.hitRate = hitRate;
        this.lastUpdated = lastUpdated;
    }

    public String getCacheName() { return cacheName; }
    public long getHits() { return hits; }
    public long getMisses() { return misses; }
    public double getHitRate() { return hitRate; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    
    public long getTotal() { return hits + misses; }
    public double getMissRate() { return 1.0 - hitRate; }

    @Override
    public String toString() {
        return "CacheMetricsJava{" +
                "cacheName='" + cacheName + '\'' +
                ", hits=" + hits +
                ", misses=" + misses +
                ", hitRate=" + hitRate +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}