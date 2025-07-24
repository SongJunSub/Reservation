package com.example.reservation.cache

import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 캐시 서비스 (Kotlin)
 * 
 * 기능:
 * 1. 프로그래밍 방식 캐시 조작
 * 2. 캐시 워밍업 및 사전 로딩
 * 3. 캐시 메트릭 수집
 * 4. 대량 캐시 작업 (배치)
 * 
 * Kotlin 특징:
 * - 확장 함수를 통한 편의 메서드
 * - inline 함수를 통한 성능 최적화
 * - 타입 안전한 제네릭 활용
 * - 코루틴 지원 (필요시)
 */
@Service
class CacheService(
    private val cacheManager: CacheManager,
    private val redisTemplate: RedisTemplate<String, Any>,
    private val metricsRedisTemplate: RedisTemplate<String, Long>
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CacheService::class.java)
        private const val CACHE_METRICS_PREFIX = "cache:metrics:"
        private const val CACHE_HIT_SUFFIX = ":hits"
        private const val CACHE_MISS_SUFFIX = ":misses"
    }

    /**
     * 캐시에서 값 조회 (타입 안전)
     * Kotlin 제네릭과 inline 함수 활용
     */
    inline fun <reified T> getFromCache(cacheName: String, key: String): T? {
        return try {
            val cache = cacheManager.getCache(cacheName)
            val cacheValue = cache?.get(key, T::class.java)
            
            if (cacheValue != null) {
                recordCacheHit(cacheName)
                logger.debug("캐시 히트: {} - {}", cacheName, key)
            } else {
                recordCacheMiss(cacheName)
                logger.debug("캐시 미스: {} - {}", cacheName, key)
            }
            
            cacheValue
        } catch (ex: Exception) {
            logger.warn("캐시 조회 실패: {} - {} : {}", cacheName, key, ex.message)
            recordCacheMiss(cacheName)
            null
        }
    }

    /**
     * 캐시에 값 저장
     * Kotlin의 null 안전성 활용
     */
    fun putToCache(cacheName: String, key: String, value: Any?) {
        if (value == null) {
            logger.debug("null 값은 캐시하지 않음: {} - {}", cacheName, key)
            return
        }

        try {
            val cache = cacheManager.getCache(cacheName)
            cache?.put(key, value)
            logger.debug("캐시 저장: {} - {}", cacheName, key)
        } catch (ex: Exception) {
            logger.error("캐시 저장 실패: {} - {} : {}", cacheName, key, ex.message)
        }
    }

    /**
     * 조건부 캐시 저장 (키가 존재하지 않을 때만)
     * Kotlin의 확장 함수 스타일
     */
    fun putIfAbsent(cacheName: String, key: String, value: Any?): Boolean {
        if (value == null) return false

        return try {
            val cache = cacheManager.getCache(cacheName)
            val existingValue = cache?.get(key)
            
            if (existingValue == null) {
                cache?.put(key, value)
                logger.debug("조건부 캐시 저장: {} - {}", cacheName, key)
                true
            } else {
                logger.debug("캐시 키 이미 존재: {} - {}", cacheName, key)
                false
            }
        } catch (ex: Exception) {
            logger.error("조건부 캐시 저장 실패: {} - {} : {}", cacheName, key, ex.message)
            false
        }
    }

    /**
     * 캐시에서 값 삭제
     */
    fun evictFromCache(cacheName: String, key: String) {
        try {
            val cache = cacheManager.getCache(cacheName)
            cache?.evict(key)
            logger.debug("캐시 삭제: {} - {}", cacheName, key)
        } catch (ex: Exception) {
            logger.error("캐시 삭제 실패: {} - {} : {}", cacheName, key, ex.message)
        }
    }

    /**
     * 전체 캐시 클리어
     */
    fun clearCache(cacheName: String) {
        try {
            val cache = cacheManager.getCache(cacheName)
            cache?.clear()
            logger.info("캐시 전체 삭제: {}", cacheName)
        } catch (ex: Exception) {
            logger.error("캐시 전체 삭제 실패: {} : {}", cacheName, ex.message)
        }
    }

    /**
     * 모든 캐시 클리어
     */
    fun clearAllCaches() {
        try {
            cacheManager.cacheNames.forEach { cacheName ->
                clearCache(cacheName)
            }
            logger.info("모든 캐시 삭제 완료")
        } catch (ex: Exception) {
            logger.error("모든 캐시 삭제 실패: {}", ex.message)
        }
    }

    /**
     * 캐시 존재 여부 확인
     */
    fun exists(cacheName: String, key: String): Boolean {
        return try {
            val cache = cacheManager.getCache(cacheName)
            cache?.get(key) != null
        } catch (ex: Exception) {
            logger.warn("캐시 존재 확인 실패: {} - {} : {}", cacheName, key, ex.message)
            false
        }
    }

    /**
     * TTL 기반 캐시 저장
     * Redis 특화 기능
     */
    fun putWithTtl(cacheName: String, key: String, value: Any, ttl: Duration) {
        try {
            val redisKey = buildRedisKey(cacheName, key)
            redisTemplate.opsForValue().set(redisKey, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
            logger.debug("TTL 캐시 저장: {} - {} (TTL: {})", cacheName, key, ttl)
        } catch (ex: Exception) {
            logger.error("TTL 캐시 저장 실패: {} - {} : {}", cacheName, key, ex.message)
        }
    }

    /**
     * 대량 캐시 조회
     * Kotlin 컬렉션 함수 활용
     */
    fun multiGet(cacheName: String, keys: List<String>): Map<String, Any?> {
        return keys.associateWith { key ->
            getFromCache<Any>(cacheName, key)
        }.filterValues { it != null }
    }

    /**
     * 대량 캐시 저장
     */
    fun multiPut(cacheName: String, keyValuePairs: Map<String, Any>) {
        try {
            val cache = cacheManager.getCache(cacheName)
            keyValuePairs.forEach { (key, value) ->
                cache?.put(key, value)
            }
            logger.debug("대량 캐시 저장: {} - {} items", cacheName, keyValuePairs.size)
        } catch (ex: Exception) {
            logger.error("대량 캐시 저장 실패: {} : {}", cacheName, ex.message)
        }
    }

    /**
     * 패턴 기반 캐시 삭제
     * Redis 특화 기능
     */
    fun evictByPattern(cacheName: String, keyPattern: String) {
        try {
            val pattern = buildRedisKey(cacheName, keyPattern)
            val keys = redisTemplate.keys(pattern)
            
            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
                logger.debug("패턴 기반 캐시 삭제: {} - {} keys", pattern, keys.size)
            }
        } catch (ex: Exception) {
            logger.error("패턴 기반 캐시 삭제 실패: {} - {} : {}", cacheName, keyPattern, ex.message)
        }
    }

    /**
     * 캐시 워밍업
     * 자주 사용되는 데이터를 미리 캐시에 로드
     */
    fun warmUpCache(cacheName: String, dataLoader: () -> Map<String, Any>) {
        try {
            logger.info("캐시 워밍업 시작: {}", cacheName)
            val startTime = System.currentTimeMillis()
            
            val data = dataLoader()
            multiPut(cacheName, data)
            
            val duration = System.currentTimeMillis() - startTime
            logger.info("캐시 워밍업 완료: {} - {} items in {}ms", cacheName, data.size, duration)
            
            recordCacheWarmup(cacheName, data.size, duration)
        } catch (ex: Exception) {
            logger.error("캐시 워밍업 실패: {} : {}", cacheName, ex.message)
        }
    }

    /**
     * 캐시 메트릭 조회
     */
    fun getCacheMetrics(cacheName: String): CacheMetrics {
        return try {
            val hits = metricsRedisTemplate.opsForValue().get("$CACHE_METRICS_PREFIX$cacheName$CACHE_HIT_SUFFIX") ?: 0L
            val misses = metricsRedisTemplate.opsForValue().get("$CACHE_METRICS_PREFIX$cacheName$CACHE_MISS_SUFFIX") ?: 0L
            val total = hits + misses
            val hitRate = if (total > 0) hits.toDouble() / total.toDouble() else 0.0
            
            CacheMetrics(
                cacheName = cacheName,
                hits = hits,
                misses = misses,
                hitRate = hitRate,
                lastUpdated = LocalDateTime.now()
            )
        } catch (ex: Exception) {
            logger.error("캐시 메트릭 조회 실패: {} : {}", cacheName, ex.message)
            CacheMetrics(cacheName, 0, 0, 0.0, LocalDateTime.now())
        }
    }

    /**
     * 모든 캐시 메트릭 조회
     */
    fun getAllCacheMetrics(): List<CacheMetrics> {
        return cacheManager.cacheNames.map { cacheName ->
            getCacheMetrics(cacheName)
        }
    }

    // === Private 헬퍼 메서드들 ===

    private fun buildRedisKey(cacheName: String, key: String): String = "reservation:$cacheName:$key"

    private fun recordCacheHit(cacheName: String) {
        try {
            metricsRedisTemplate.opsForValue().increment("$CACHE_METRICS_PREFIX$cacheName$CACHE_HIT_SUFFIX")
        } catch (ex: Exception) {
            logger.debug("캐시 히트 메트릭 기록 실패: {}", ex.message)
        }
    }

    private fun recordCacheMiss(cacheName: String) {
        try {
            metricsRedisTemplate.opsForValue().increment("$CACHE_METRICS_PREFIX$cacheName$CACHE_MISS_SUFFIX")
        } catch (ex: Exception) {
            logger.debug("캐시 미스 메트릭 기록 실패: {}", ex.message)
        }
    }

    private fun recordCacheWarmup(cacheName: String, itemCount: Int, durationMs: Long) {
        try {
            val metricsKey = "$CACHE_METRICS_PREFIX$cacheName:warmup"
            val warmupInfo = mapOf(
                "itemCount" to itemCount,
                "durationMs" to durationMs,
                "timestamp" to LocalDateTime.now().toString()
            )
            redisTemplate.opsForValue().set(metricsKey, warmupInfo, Duration.ofDays(1))
        } catch (ex: Exception) {
            logger.debug("캐시 워밍업 메트릭 기록 실패: {}", ex.message)
        }
    }
}

/**
 * 캐시 메트릭 데이터 클래스
 * Kotlin data class의 간편함
 */
data class CacheMetrics(
    val cacheName: String,
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val lastUpdated: LocalDateTime
) {
    val total: Long get() = hits + misses
    val missRate: Double get() = 1.0 - hitRate
}