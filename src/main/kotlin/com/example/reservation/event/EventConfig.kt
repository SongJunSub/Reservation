package com.example.reservation.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

/**
 * Kafka 이벤트 설정 (Kotlin)
 * 
 * 기능:
 * 1. Kafka Producer/Consumer 설정
 * 2. JSON 직렬화/역직렬화 설정
 * 3. 에러 처리 및 재시도 전략
 * 4. 동시성 및 성능 최적화
 * 
 * Kotlin 특징:
 * - mapOf를 통한 간결한 설정 맵 생성
 * - apply 스코프 함수를 통한 객체 설정
 * - 타입 추론을 통한 간결한 제네릭 선언
 * - 람다 표현식을 통한 콜백 설정
 */
@Configuration
@EnableKafka
class EventConfig(
    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private val bootstrapServers: String,
    
    @Value("\${spring.kafka.consumer.group-id:reservation-service}")
    private val groupId: String,
    
    @Value("\${app.kafka.producer.retries:3}")
    private val producerRetries: Int,
    
    @Value("\${app.kafka.consumer.concurrency:3}")
    private val consumerConcurrency: Int
) {

    companion object {
        // 토픽 이름 상수
        const val RESERVATION_EVENTS_TOPIC = "reservation.events"
        const val PAYMENT_EVENTS_TOPIC = "payment.events"
        const val NOTIFICATION_EVENTS_TOPIC = "notification.events"
        const val AUDIT_EVENTS_TOPIC = "audit.events"
        const val ANALYTICS_EVENTS_TOPIC = "analytics.events"
    }

    /**
     * Kafka Producer 설정
     * Kotlin mapOf를 통한 간결한 설정
     */
    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            
            // 성능 및 안정성 설정
            ProducerConfig.ACKS_CONFIG to "all", // 모든 복제본에 쓰기 확인
            ProducerConfig.RETRIES_CONFIG to producerRetries,
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,
            ProducerConfig.LINGER_MS_CONFIG to 10,
            ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432,
            
            // 중복 방지 설정
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
            
            // 타임아웃 설정
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 30000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 120000
        )
        
        return DefaultKafkaProducerFactory(configProps)
    }

    /**
     * Kafka Template 설정
     * Kotlin apply 스코프 함수 활용
     */
    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> {
        return KafkaTemplate(producerFactory()).apply {
            // 기본 토픽 설정
            defaultTopic = RESERVATION_EVENTS_TOPIC
            
            // 프로듀서 리스너 설정 (성공/실패 콜백)
            setProducerListener(object : ProducerListener<String, Any> {
                override fun onSuccess(
                    producerRecord: org.apache.kafka.clients.producer.ProducerRecord<String, Any>?,
                    recordMetadata: org.apache.kafka.clients.producer.RecordMetadata?
                ) {
                    // Kotlin에서는 logger를 companion object에서 정의하거나 
                    // 확장 함수로 만들 수 있음
                    println("이벤트 발송 성공: ${producerRecord?.topic()}-${producerRecord?.partition()}")
                }

                override fun onError(
                    producerRecord: org.apache.kafka.clients.producer.ProducerRecord<String, Any>?,
                    recordMetadata: org.apache.kafka.clients.producer.RecordMetadata?,
                    exception: Exception?
                ) {
                    println("이벤트 발송 실패: ${exception?.message}")
                }
            })
        }
    }

    /**
     * Kafka Consumer 설정
     * Kotlin의 간결한 맵 생성과 타입 추론
     */
    @Bean
    fun consumerFactory(): ConsumerFactory<String, Any> {
        val configProps = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            
            // JSON 역직렬화 설정
            ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java.name,
            JsonDeserializer.TRUSTED_PACKAGES to "*",
            JsonDeserializer.VALUE_DEFAULT_TYPE to "com.example.reservation.event.ReservationEvent",
            
            // 오프셋 및 커밋 설정
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false, // 수동 커밋으로 변경
            ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG to 1000,
            
            // 세션 및 하트비트 설정
            ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG to 30000,
            ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG to 10000,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
            ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG to 300000,
            
            // 가져오기 설정
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to 1,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to 500
        )
        
        return DefaultKafkaConsumerFactory(configProps)
    }

    /**
     * Kafka Listener Container Factory 설정
     * Kotlin apply를 통한 fluent 설정
     */
    @Bean
    fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
        return ConcurrentKafkaListenerContainerFactory<String, Any>().apply {
            consumerFactory = consumerFactory()
            
            // 동시성 설정
            setConcurrency(consumerConcurrency)
            
            // 컨테이너 속성 설정
            containerProperties.apply {
                // 수동 커밋 모드
                ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
                
                // 재시도 설정  
                isAsyncAcks = true
                
                // 에러 핸들러 설정
                setCommonErrorHandler(org.springframework.kafka.listener.DefaultErrorHandler().apply {
                    setRetryTemplate(createRetryTemplate())
                })
            }
            
            // 배치 리스너 설정 (선택사항)
            isBatchListener = false
            
            // 리스너 타입 설정
            containerProperties.messageListener = null
        }
    }

    /**
     * 재시도 템플릿 생성
     * Kotlin의 간결한 함수 정의
     */
    private fun createRetryTemplate(): org.springframework.retry.support.RetryTemplate {
        return org.springframework.retry.support.RetryTemplate().apply {
            setRetryPolicy(
                org.springframework.retry.policy.SimpleRetryPolicy().apply {
                    maxAttempts = 3
                }
            )
            
            setBackOffPolicy(
                org.springframework.retry.backoff.ExponentialBackOffPolicy().apply {
                    initialInterval = 1000L
                    multiplier = 2.0
                    maxInterval = 10000L
                }
            )
        }
    }

    /**
     * 커스텀 JSON 직렬화를 위한 ObjectMapper
     */
    @Bean("kafkaObjectMapper")
    fun kafkaObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            
            // 타임스탬프 설정
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            
            // null 값 처리
            setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            
            // 알 수 없는 속성 무시
            disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }
    }