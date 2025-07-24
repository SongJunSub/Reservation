package com.example.reservation.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 다중 캐시 설정 (Java)
 * 
 * 기능:
 * 1. 다층 캐시 전략 (L1: 로컬, L2: Redis)
 * 2. 캐시별 개별 TTL 설정
 * 3. JSON 직렬화를 통한 복잡한 객체 캐싱
 * 4. 캐시 워밍업 전략
 * 
 * Java 특징:
 * - 명시적 타입 선언과 제네릭 사용
 * - Builder 패턴을 통한 설정 구성
 * - HashMap을 통한 컬렉션 생성
 * - 전통적인 Bean 설정 방식
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class CacheConfigJava {

    // 캐시 이름 상수 정의
    public static final String RESERVATION_CACHE = "reservations";
    public static final String USER_CACHE = "users";
    public static final String ROOM_CACHE = "rooms";
    public static final String AVAILABILITY_CACHE = "availability";
    public static final String STATISTICS_CACHE = "statistics";
    public static final String SEARCH_CACHE = "search";

    private final Duration defaultTtl;
    private final Duration reservationTtl;
    private final Duration userTtl;
    private final Duration roomTtl;

    public CacheConfigJava(
            @Value("${spring.cache.redis.time-to-live:PT1H}") Duration defaultTtl,
            @Value("${app.cache.reservation.ttl:PT30M}") Duration reservationTtl,
            @Value("${app.cache.user.ttl:PT2H}") Duration userTtl,
            @Value("${app.cache.room.ttl:PT1H}") Duration roomTtl) {
        this.defaultTtl = defaultTtl;
        this.reservationTtl = reservationTtl;
        this.userTtl = userTtl;
        this.roomTtl = roomTtl;
    }

    /**
     * 기본 Redis Template 설정
     * Java의 명시적 타입과 Builder 패턴 활용
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key Serializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Value Serializer (JSON 직렬화)
        ObjectMapper objectMapper = createCacheObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);
        
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 다중 레벨 캐시 매니저 설정
     * Java HashMap과 Builder 패턴 활용
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            RedisTemplate<String, Object> redisTemplate) {
        
        // 기본 캐시 설정
        RedisCacheConfiguration defaultCacheConfig = createCacheConfiguration(defaultTtl);
        
        // 캐시별 개별 설정 - Java HashMap 생성
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(RESERVATION_CACHE, createCacheConfiguration(reservationTtl));
        cacheConfigurations.put(USER_CACHE, createCacheConfiguration(userTtl));
        cacheConfigurations.put(ROOM_CACHE, createCacheConfiguration(roomTtl));
        cacheConfigurations.put(AVAILABILITY_CACHE, createCacheConfiguration(Duration.ofMinutes(15)));
        cacheConfigurations.put(STATISTICS_CACHE, createCacheConfiguration(Duration.ofHours(6)));
        cacheConfigurations.put(SEARCH_CACHE, createCacheConfiguration(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // 트랜잭션 지원
                .build();
    }

    /**
     * 캐시 설정 생성 헬퍼
     * Java의 Builder 패턴과 메서드 체이닝
     */
    private RedisCacheConfiguration createCacheConfiguration(Duration ttl) {
        ObjectMapper objectMapper = createCacheObjectMapper();
        GenericJackson2JsonRedisSerializer jsonSerializer = 
                new GenericJackson2JsonRedisSerializer(objectMapper);
        
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues() // null 값 캐싱 비활성화
                .prefixCacheNameWith("reservation:"); // 캐시 키 접두사
    }

    /**
     * 캐시용 ObjectMapper 생성
     * Java의 명시적 설정 방식
     */
    private ObjectMapper createCacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 타임스탬프를 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 알 수 없는 속성 무시
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        // null 값 포함하지 않음
        objectMapper.setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
        );
        
        return objectMapper;
    }

    /**
     * 캐시 워밍업을 위한 RedisTemplate Bean (문자열 전용)
     */
    @Bean("cacheWarmupRedisTemplate")
    public RedisTemplate<String, String> cacheWarmupRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 캐시 메트릭을 위한 RedisTemplate (Long 전용)
     */
    @Bean("metricsRedisTemplate")
    public RedisTemplate<String, Long> metricsRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(Long.class));
        template.afterPropertiesSet();
        return template;
    }
}