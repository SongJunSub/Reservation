package com.example.reservation.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis 다중 캐시 설정 (Kotlin)
 * 
 * 기능:
 * 1. 다층 캐시 전략 (L1: 로컬, L2: Redis)
 * 2. 캐시별 개별 TTL 설정
 * 3. JSON 직렬화를 통한 복잡한 객체 캐싱
 * 4. 캐시 워밍업 전략
 * 
 * Kotlin 특징:
 * - 간결한 함수 정의와 타입 추론
 * - mapOf를 통한 컬렉션 생성
 * - when 표현식을 통한 조건부 설정
 * - Extension 함수 활용 가능
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = ["spring.cache.type"], havingValue = "redis", matchIfMissing = true)
class CacheConfig(
    @Value("\${spring.cache.redis.time-to-live:PT1H}") // 기본 1시간
    private val defaultTtl: Duration,
    
    @Value("\${app.cache.reservation.ttl:PT30M}") // 예약 캐시 30분
    private val reservationTtl: Duration,
    
    @Value("\${app.cache.user.ttl:PT2H}") // 사용자 캐시 2시간
    private val userTtl: Duration,
    
    @Value("\${app.cache.room.ttl:PT1H}") // 객실 캐시 1시간
    private val roomTtl: Duration
) {

    companion object {
        // 캐시 이름 상수 정의
        const val RESERVATION_CACHE = "reservations"
        const val USER_CACHE = "users"
        const val ROOM_CACHE = "rooms"
        const val AVAILABILITY_CACHE = "availability"
        const val STATISTICS_CACHE = "statistics"
        const val SEARCH_CACHE = "search"
    }

    /**
     * 기본 Redis Template 설정
     * Kotlin의 간결한 Bean 정의
     */
    @Bean
    @Primary
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        return RedisTemplate<String, Any>().apply {
            setConnectionFactory(connectionFactory)
            
            // Key Serializer
            keySerializer = StringRedisSerializer()
            hashKeySerializer = StringRedisSerializer()
            
            // Value Serializer (JSON 직렬화)
            val objectMapper = createCacheObjectMapper()
            val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
            
            valueSerializer = jsonSerializer
            hashValueSerializer = jsonSerializer
            
            afterPropertiesSet()
        }
    }

    /**
     * 다중 레벨 캐시 매니저 설정
     * Kotlin의 mapOf와 when 표현식 활용
     */
    @Bean
    @Primary
    fun cacheManager(
        connectionFactory: RedisConnectionFactory,
        redisTemplate: RedisTemplate<String, Any>
    ): CacheManager {
        
        // 기본 캐시 설정
        val defaultCacheConfig = createCacheConfiguration(defaultTtl)
        
        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            RESERVATION_CACHE to createCacheConfiguration(reservationTtl),
            USER_CACHE to createCacheConfiguration(userTtl),
            ROOM_CACHE to createCacheConfiguration(roomTtl),
            AVAILABILITY_CACHE to createCacheConfiguration(Duration.ofMinutes(15)),
            STATISTICS_CACHE to createCacheConfiguration(Duration.ofHours(6)),
            SEARCH_CACHE to createCacheConfiguration(Duration.ofMinutes(5))
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware() // 트랜잭션 지원
            .build()
    }

    /**
     * 캐시 설정 생성 헬퍼
     * Kotlin의 함수형 프로그래밍 스타일
     */
    private fun createCacheConfiguration(ttl: Duration): RedisCacheConfiguration {
        val objectMapper = createCacheObjectMapper()
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
            )
            .disableCachingNullValues() // null 값 캐싱 비활성화
            .prefixCacheNameWith("reservation:") // 캐시 키 접두사
    }

    /**
     * 캐시용 ObjectMapper 생성
     * Jackson Kotlin 모듈과 Java Time 모듈 설정
     */
    private fun createCacheObjectMapper(): ObjectMapper {
        return jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            // 타임스탬프를 문자열로 직렬화
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 알 수 없는 속성 무시
            disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // null 값 포함하지 않음
            setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        }
    }

    /**
     * 캐시 워밍업을 위한 RedisTemplate Bean (별도)
     */
    @Bean("cacheWarmupRedisTemplate")
    fun cacheWarmupRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        return RedisTemplate<String, String>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = StringRedisSerializer()
            afterPropertiesSet()
        }
    }

    /**
     * 캐시 메트릭을 위한 RedisTemplate
     */
    @Bean("metricsRedisTemplate")
    fun metricsRedisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, Long> {
        return RedisTemplate<String, Long>().apply {
            setConnectionFactory(connectionFactory)
            keySerializer = StringRedisSerializer()
            valueSerializer = org.springframework.data.redis.serializer.GenericToStringSerializer(Long::class.java)
            afterPropertiesSet()
        }
    }
}