package com.example.reservation.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 이벤트 설정 (Java)
 * 
 * 기능:
 * 1. Kafka Producer/Consumer 설정
 * 2. JSON 직렬화/역직렬화 설정
 * 3. 에러 처리 및 재시도 전략
 * 4. 동시성 및 성능 최적화
 * 
 * Java 특징:
 * - HashMap을 통한 명시적 설정 맵 생성
 * - Builder 패턴을 통한 객체 설정
 * - 명시적 제네릭 타입 선언
 * - 전통적인 Bean 설정 방식
 */
@Configuration
@EnableKafka
public class EventConfigJava {

    // 토픽 이름 상수
    public static final String RESERVATION_EVENTS_TOPIC = "reservation.events";
    public static final String PAYMENT_EVENTS_TOPIC = "payment.events";
    public static final String NOTIFICATION_EVENTS_TOPIC = "notification.events";
    public static final String AUDIT_EVENTS_TOPIC = "audit.events";
    public static final String ANALYTICS_EVENTS_TOPIC = "analytics.events";

    private final String bootstrapServers;
    private final String groupId;
    private final int producerRetries;
    private final int consumerConcurrency;

    public EventConfigJava(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:reservation-service}") String groupId,
            @Value("${app.kafka.producer.retries:3}") int producerRetries,
            @Value("${app.kafka.consumer.concurrency:3}") int consumerConcurrency) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.producerRetries = producerRetries;
        this.consumerConcurrency = consumerConcurrency;
    }

    /**
     * Kafka Producer 설정
     * Java HashMap을 통한 명시적 설정
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // 성능 및 안정성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, producerRetries);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        
        // 중복 방지 설정
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        // 타임아웃 설정
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka Template 설정
     * Java의 명시적 객체 생성과 설정
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        
        // 기본 토픽 설정
        template.setDefaultTopic(RESERVATION_EVENTS_TOPIC);
        
        // 프로듀서 리스너 설정 (성공/실패 콜백)
        template.setProducerListener(new ProducerListener<String, Object>() {
            @Override
            public void onSuccess(
                    org.apache.kafka.clients.producer.ProducerRecord<String, Object> producerRecord,
                    org.apache.kafka.clients.producer.RecordMetadata recordMetadata) {
                System.out.println("이벤트 발송 성공: " + producerRecord.topic() + "-" + producerRecord.partition());
            }

            @Override
            public void onError(
                    org.apache.kafka.clients.producer.ProducerRecord<String, Object> producerRecord,
                    org.apache.kafka.clients.producer.RecordMetadata recordMetadata,
                    Exception exception) {
                System.out.println("이벤트 발송 실패: " + exception.getMessage());
            }
        });
        
        return template;
    }

    /**
     * Kafka Consumer 설정
     * Java의 명시적 맵 생성과 타입 선언
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // JSON 역직렬화 설정
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.example.reservation.event.ReservationEventJava");
        
        // 오프셋 및 커밋 설정
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);
        
        // 세션 및 하트비트 설정
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        
        // 가져오기 설정
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Kafka Listener Container Factory 설정
     * Java의 명시적 객체 생성과 메서드 체이닝
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory());
        
        // 동시성 설정
        factory.setConcurrency(consumerConcurrency);
        
        // 컨테이너 속성 설정
        ContainerProperties containerProperties = factory.getContainerProperties();
        
        // 수동 커밋 모드
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 재시도 설정
        containerProperties.setAsyncAcks(true);
        
        // 에러 핸들러 설정
        DefaultErrorHandler errorHandler = new DefaultErrorHandler();
        errorHandler.setRetryTemplate(createRetryTemplate());
        factory.setCommonErrorHandler(errorHandler);
        
        // 배치 리스너 설정 (선택사항)
        factory.setBatchListener(false);
        
        return factory;
    }

    /**
     * 재시도 템플릿 생성
     * Java의 명시적 객체 생성과 설정
     */
    private RetryTemplate createRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // 재시도 정책 설정
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // 백오프 정책 설정
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        return retryTemplate;
    }

    /**
     * 커스텀 JSON 직렬화를 위한 ObjectMapper
     */
    @Bean("kafkaObjectMapper")
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        objectMapper.registerModule(new JavaTimeModule());
        
        // 타임스탬프 설정
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // null 값 처리
        objectMapper.setSerializationInclusion(
                com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
        );
        
        // 알 수 없는 속성 무시
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        
        return objectMapper;
    }
}