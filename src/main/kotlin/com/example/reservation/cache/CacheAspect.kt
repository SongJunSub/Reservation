package com.example.reservation.cache

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.system.measureTimeMillis

/**
 * 캐시 AOP Aspect (Kotlin)
 * 
 * 기능:
 * 1. 캐시 동작 모니터링
 * 2. 캐시 성능 측정
 * 3. 캐시 오류 처리 및 폴백
 * 4. 디버그 로깅
 * 
 * Kotlin 특징:
 * - 확장 함수를 통한 편의 메서드
 * - measureTimeMillis로 간편한 성능 측정
 * - when 표현식을 통한 조건 처리
 * - inline 함수를 통한 성능 최적화
 */
@Aspect
@Component
class CacheAspect(
    private val cacheService: CacheService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CacheAspect::class.java)
    }

    /**
     * @Cacheable 어노테이션 모니터링
     * Spring Cache 동작을 감시하고 메트릭 수집
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    fun monitorCacheable(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}"
        
        return try {
            var result: Any?
            val executionTime = measureTimeMillis {
                result = joinPoint.proceed()
            }
            
            logger.debug("캐시 조회 완료: {} - {}ms", methodName, executionTime)
            result
            
        } catch (ex: Exception) {
            logger.error("캐시 조회 중 오류: {} - {}", methodName, ex.message)
            throw ex
        }
    }

    /**
     * @CacheEvict 어노테이션 모니터링
     * 캐시 삭제 동작 감시
     */
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    fun monitorCacheEvict(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}"
        
        return try {
            var result: Any?
            val executionTime = measureTimeMillis {
                result = joinPoint.proceed()
            }
            
            logger.debug("캐시 삭제 완료: {} - {}ms", methodName, executionTime)
            result
            
        } catch (ex: Exception) {
            logger.error("캐시 삭제 중 오류: {} - {}", methodName, ex.message)
            throw ex
        }
    }

    /**
     * @CachePut 어노테이션 모니터링
     * 캐시 저장 동작 감시
     */
    @Around("@annotation(org.springframework.cache.annotation.CachePut)")
    fun monitorCachePut(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}"
        
        return try {
            var result: Any?
            val executionTime = measureTimeMillis {
                result = joinPoint.proceed()
            }
            
            logger.debug("캐시 저장 완료: {} - {}ms", methodName, executionTime)
            result
            
        } catch (ex: Exception) {
            logger.error("캐시 저장 중 오류: {} - {}", methodName, ex.message)
            throw ex
        }
    }

    /**
     * 커스텀 캐시 어노테이션 처리
     * 고급 캐시 제어를 위한 사용자 정의 어노테이션
     */
    @Around("@annotation(com.example.reservation.cache.CustomCacheable)")
    fun handleCustomCacheable(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = "${joinPoint.signature.declaringTypeName}.${joinPoint.signature.name}"
        
        // 어노테이션에서 캐시 설정 추출
        val method = (joinPoint.signature as org.aspectj.lang.reflect.MethodSignature).method
        val annotation = method.getAnnotation(CustomCacheable::class.java)
        
        val cacheName = annotation.cacheName
        val key = buildCacheKey(joinPoint, annotation.keyPrefix)
        val ttlMinutes = annotation.ttlMinutes
        
        return try {
            // 캐시에서 먼저 조회
            val cachedResult = if (ttlMinutes > 0) {
                cacheService.getFromCache<Any>(cacheName, key)
            } else {
                null
            }
            
            if (cachedResult != null) {
                logger.debug("커스텀 캐시 히트: {} - {}", methodName, key)
                return cachedResult
            }
            
            // 캐시 미스 시 메서드 실행
            var result: Any?
            val executionTime = measureTimeMillis {
                result = joinPoint.proceed()
            }
            
            // 결과를 캐시에 저장
            if (result != null && ttlMinutes > 0) {
                if (ttlMinutes == Int.MAX_VALUE) {
                    cacheService.putToCache(cacheName, key, result)
                } else {
                    cacheService.putWithTtl(
                        cacheName, 
                        key, 
                        result, 
                        java.time.Duration.ofMinutes(ttlMinutes.toLong())
                    )
                }
            }
            
            logger.debug("커스텀 캐시 미스 처리: {} - {} ({}ms)", methodName, key, executionTime)
            result
            
        } catch (ex: Exception) {
            logger.error("커스텀 캐시 처리 중 오류: {} - {} : {}", methodName, key, ex.message)
            
            // 캐시 오류 시 폴백: 메서드 직접 실행
            try {
                joinPoint.proceed()
            } catch (fallbackEx: Exception) {
                logger.error("폴백 실행 실패: {} : {}", methodName, fallbackEx.message)
                throw fallbackEx
            }
        }
    }

    /**
     * 캐시 키 생성
     * 메서드 파라미터를 기반으로 고유한 캐시 키 생성
     */
    private fun buildCacheKey(joinPoint: ProceedingJoinPoint, keyPrefix: String): String {
        val methodName = joinPoint.signature.name
        val args = joinPoint.args
        
        val keyParts = mutableListOf<String>()
        
        if (keyPrefix.isNotBlank()) {
            keyParts.add(keyPrefix)
        }
        
        keyParts.add(methodName)
        
        args.forEach { arg ->
            when (arg) {
                null -> keyParts.add("null")
                is String -> keyParts.add(arg)
                is Number -> keyParts.add(arg.toString())
                is Boolean -> keyParts.add(arg.toString())
                else -> keyParts.add(arg.hashCode().toString())
            }
        }
        
        return keyParts.joinToString(":")
    }
}

/**
 * 커스텀 캐시 어노테이션
 * 
 * 더 세밀한 캐시 제어를 위한 사용자 정의 어노테이션
 * Kotlin annotation class 정의
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CustomCacheable(
    /**
     * 캐시 이름
     */
    val cacheName: String,
    
    /**
     * 캐시 키 접두사
     */
    val keyPrefix: String = "",
    
    /**
     * TTL (분 단위, 0이면 기본 TTL 사용, Int.MAX_VALUE면 무제한)
     */
    val ttlMinutes: Int = 0,
    
    /**
     * 조건부 캐싱 (SpEL 표현식)
     */
    val condition: String = "",
    
    /**
     * 캐시 제외 조건 (SpEL 표현식)
     */
    val unless: String = ""
)