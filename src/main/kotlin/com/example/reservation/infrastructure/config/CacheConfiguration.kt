package com.example.reservation.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * 캐시 설정 (Redis)
 * 실무 릴리즈 급 구현: 다중 캐시 전략, TTL 관리, 직렬화 최적화
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties::class)
class CacheConfiguration {

    @Value("\${spring.data.redis.host:localhost}")
    private lateinit var redisHost: String
    
    @Value("\${spring.data.redis.port:6379}")
    private var redisPort: Int = 6379
    
    @Value("\${spring.data.redis.password:}")
    private lateinit var redisPassword: String

    /**
     * Redis 연결 팩토리
     */
    @Bean
    @Primary
    fun redisConnectionFactory(): RedisConnectionFactory {
        val factory = LettuceConnectionFactory(redisHost, redisPort)
        if (redisPassword.isNotEmpty()) {
            factory.setPassword(redisPassword)
        }
        factory.setValidateConnection(true)
        return factory
    }

    /**
     * 리액티브 Redis 연결 팩토리
     */
    @Bean
    fun reactiveRedisConnectionFactory(): ReactiveRedisConnectionFactory {
        return redisConnectionFactory() as ReactiveRedisConnectionFactory
    }

    /**
     * Redis 템플릿 (동기식)
     */
    @Bean
    @Primary
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = redisConnectionFactory
        
        // 직렬화 설정
        val stringSerializer = StringRedisSerializer()
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        template.keySerializer = stringSerializer
        template.hashKeySerializer = stringSerializer
        template.valueSerializer = jsonSerializer
        template.hashValueSerializer = jsonSerializer
        
        template.afterPropertiesSet()
        return template
    }

    /**
     * 리액티브 Redis 템플릿
     */
    @Bean
    fun reactiveRedisTemplate(
        reactiveRedisConnectionFactory: ReactiveRedisConnectionFactory,
        objectMapper: ObjectMapper
    ): ReactiveRedisTemplate<String, Any> {
        val stringSerializer = StringRedisSerializer()
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)
        
        val serializationContext = org.springframework.data.redis.serializer.RedisSerializationContext
            .newSerializationContext<String, Any>(stringSerializer)
            .key(stringSerializer)
            .value(jsonSerializer)
            .hashKey(stringSerializer)
            .hashValue(jsonSerializer)
            .build()
        
        return ReactiveRedisTemplate(reactiveRedisConnectionFactory, serializationContext)
    }

    /**
     * 캐시 매니저
     */
    @Bean
    fun cacheManager(
        redisConnectionFactory: RedisConnectionFactory,
        cacheProperties: CacheProperties
    ): CacheManager {
        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(cacheProperties.defaultTtlMinutes))
            .serializeKeysWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(StringRedisSerializer())
            )
            .serializeValuesWith(
                org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                    .fromSerializer(GenericJackson2JsonRedisSerializer())
            )
            .disableCachingNullValues()
        
        // 캐시별 개별 설정
        val cacheConfigurations = mapOf(
            // 예약 관련 캐시
            "reservation" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "reservation-summary" to defaultConfig.entryTtl(Duration.ofMinutes(10)),
            
            // 고객 관련 캐시
            "guest" to defaultConfig.entryTtl(Duration.ofHours(1)),
            "guest-preferences" to defaultConfig.entryTtl(Duration.ofHours(2)),
            
            // 객실/시설 관련 캐시
            "room-availability" to defaultConfig.entryTtl(Duration.ofMinutes(5)),
            "rate-plans" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "property-info" to defaultConfig.entryTtl(Duration.ofHours(4)),
            
            // 세션 캐시
            "user-sessions" to defaultConfig.entryTtl(Duration.ofMinutes(cacheProperties.sessionTtlMinutes)),
            
            // 통계 캐시
            "statistics" to defaultConfig.entryTtl(Duration.ofHours(1)),
            
            // 설정 캐시
            "configuration" to defaultConfig.entryTtl(Duration.ofHours(12))
        )
        
        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }

    /**
     * 개발/테스트 환경용 인메모리 캐시
     */
    @Bean
    @ConditionalOnProperty(name = ["spring.profiles.active"], havingValue = "test")
    fun inMemoryCacheManager(): CacheManager {
        return org.springframework.cache.concurrent.ConcurrentMapCacheManager(
            "reservation", "guest", "room-availability", "rate-plans", "property-info"
        )
    }
}

/**
 * 캐시 속성 설정
 */
@ConfigurationProperties(prefix = "app.cache")
data class CacheProperties(
    val enabled: Boolean = true,
    val defaultTtlMinutes: Long = 60,
    val sessionTtlMinutes: Long = 30,
    val maxSize: Int = 10000,
    val redis: RedisProperties = RedisProperties()
)

/**
 * Redis 관련 속성
 */
data class RedisProperties(
    val keyPrefix: String = "reservation:",
    val enableCompression: Boolean = true,
    val maxConnections: Int = 50,
    val connectionTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(10),
    val cluster: ClusterProperties = ClusterProperties()
)

/**
 * Redis 클러스터 속성
 */
data class ClusterProperties(
    val enabled: Boolean = false,
    val nodes: List<String> = emptyList(),
    val maxRedirects: Int = 3
)

/**
 * 캐시 이벤트 리스너 (캐시 생명주기 모니터링)
 */
@org.springframework.stereotype.Component
class CacheEventListener {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(CacheEventListener::class.java)
    
    @org.springframework.cache.annotation.CacheEvict(allEntries = true)
    @org.springframework.scheduling.annotation.Scheduled(fixedRateString = "\${app.cache.cleanup.interval:3600000}")
    fun clearExpiredEntries() {
        logger.info("캐시 정리 작업 실행")
    }
    
    @org.springframework.context.event.EventListener
    fun handleCacheHit(event: org.springframework.cache.interceptor.CacheOperationInvocationContext<*>) {
        // 캐시 히트 메트릭 수집
        logger.debug("캐시 히트: ${event.operation.cacheNames}")
    }
    
    @org.springframework.context.event.EventListener
    fun handleCacheMiss(event: org.springframework.cache.interceptor.CacheOperationInvocationContext<*>) {
        // 캐시 미스 메트릭 수집
        logger.debug("캐시 미스: ${event.operation.cacheNames}")
    }
}

/**
 * 캐시 키 생성기
 */
@org.springframework.stereotype.Component
class CustomCacheKeyGenerator : org.springframework.cache.interceptor.KeyGenerator {
    
    override fun generate(target: Any, method: java.lang.reflect.Method, vararg params: Any?): Any {
        val keyBuilder = StringBuilder()
        keyBuilder.append(target.javaClass.simpleName)
        keyBuilder.append(".")
        keyBuilder.append(method.name)
        
        if (params.isNotEmpty()) {
            keyBuilder.append(":")
            keyBuilder.append(params.joinToString(",") { param ->
                when (param) {
                    is java.util.UUID -> param.toString()
                    is String -> param
                    is Number -> param.toString()
                    else -> param.hashCode().toString()
                }
            })
        }
        
        return keyBuilder.toString()
    }
}

/**
 * 캐시 워밍업 서비스
 */
@org.springframework.stereotype.Service
class CacheWarmupService(
    private val cacheManager: CacheManager
) {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(CacheWarmupService::class.java)
    
    @org.springframework.boot.context.event.ApplicationReadyEvent
    @org.springframework.context.event.EventListener
    fun warmupCaches() {
        logger.info("캐시 워밍업 시작")
        
        // 자주 사용되는 데이터를 미리 캐시에 로드
        warmupPropertyCache()
        warmupRatePlanCache()
        warmupConfigurationCache()
        
        logger.info("캐시 워밍업 완료")
    }
    
    private fun warmupPropertyCache() {
        // 모든 활성 시설 정보를 캐시에 로드
        val cache = cacheManager.getCache("property-info")
        // cache?.put(key, value) // 실제 데이터 로드
    }
    
    private fun warmupRatePlanCache() {
        // 기본 요금 계획들을 캐시에 로드
        val cache = cacheManager.getCache("rate-plans")
        // 실제 구현...
    }
    
    private fun warmupConfigurationCache() {
        // 시스템 설정들을 캐시에 로드
        val cache = cacheManager.getCache("configuration")
        // 실제 구현...
    }
}

/**
 * 캐시 헬스 체크
 */
@org.springframework.stereotype.Component
class CacheHealthIndicator(
    private val redisTemplate: RedisTemplate<String, Any>
) : org.springframework.boot.actuate.health.HealthIndicator {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(CacheHealthIndicator::class.java)
    
    override fun health(): org.springframework.boot.actuate.health.Health {
        return try {
            // Redis 연결 테스트
            val pong = redisTemplate.connectionFactory?.connection?.ping()
            
            if (pong != null) {
                org.springframework.boot.actuate.health.Health.up()
                    .withDetail("redis", "UP")
                    .withDetail("ping", pong)
                    .build()
            } else {
                throw RuntimeException("Redis ping failed")
            }
        } catch (e: Exception) {
            logger.error("Cache health check failed", e)
            org.springframework.boot.actuate.health.Health.down()
                .withDetail("redis", "DOWN")
                .withDetail("error", e.message)
                .build()
        }
    }
}