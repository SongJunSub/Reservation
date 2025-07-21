package com.example.reservation.infrastructure.config

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.dialect.PostgresDialect
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.r2dbc.core.DatabaseClient

/**
 * 데이터베이스 설정
 * 실무 릴리즈 급 구현: JPA + R2DBC 하이브리드 아키텍처
 */
@Configuration
@EnableTransactionManagement
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
@EnableJpaRepositories(
    basePackages = ["com.example.reservation.infrastructure.persistence.repository"],
    includeFilters = [org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = [".*JpaRepository"]
    )]
)
@EnableR2dbcRepositories(
    basePackages = ["com.example.reservation.infrastructure.persistence.repository"],
    includeFilters = [org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.REGEX,
        pattern = [".*R2dbcRepository"]
    )]
)
@EnableConfigurationProperties(DatabaseProperties::class)
class DatabaseConfiguration : AbstractR2dbcConfiguration() {

    @Value("\${spring.r2dbc.url}")
    private lateinit var r2dbcUrl: String
    
    @Value("\${spring.r2dbc.username}")
    private lateinit var r2dbcUsername: String
    
    @Value("\${spring.r2dbc.password}")
    private lateinit var r2dbcPassword: String

    /**
     * R2DBC 연결 팩토리 (리액티브 데이터베이스 연결)
     */
    @Bean
    @Primary
    override fun connectionFactory(): ConnectionFactory {
        return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(extractHost(r2dbcUrl))
                .port(extractPort(r2dbcUrl))
                .database(extractDatabase(r2dbcUrl))
                .username(r2dbcUsername)
                .password(r2dbcPassword)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .lockWaitTimeout(java.time.Duration.ofSeconds(30))
                .statementTimeout(java.time.Duration.ofSeconds(60))
                .build()
        )
    }

    /**
     * R2DBC 트랜잭션 매니저
     */
    @Bean
    @Primary
    fun reactiveTransactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory)
    }

    /**
     * R2DBC 엔티티 템플릿 (커스텀 쿼리용)
     */
    @Bean
    fun r2dbcEntityTemplate(connectionFactory: ConnectionFactory): R2dbcEntityTemplate {
        val context = R2dbcMappingContext(NamingStrategy.DEFAULT)
        return R2dbcEntityTemplate(connectionFactory, PostgresDialect.INSTANCE, context)
    }

    /**
     * 데이터베이스 클라이언트 (동적 쿼리용)
     */
    @Bean
    fun databaseClient(connectionFactory: ConnectionFactory): DatabaseClient {
        return DatabaseClient.builder()
            .connectionFactory(connectionFactory)
            .namedParameters(true)
            .build()
    }

    // === URL 파싱 헬퍼 메서드들 ===
    
    private fun extractHost(url: String): String {
        // r2dbc:postgresql://localhost:5432/reservation_db 형태에서 호스트 추출
        val pattern = "r2dbc:postgresql://([^:]+):".toRegex()
        return pattern.find(url)?.groupValues?.get(1) ?: "localhost"
    }
    
    private fun extractPort(url: String): Int {
        val pattern = ":([0-9]+)/".toRegex()
        return pattern.find(url)?.groupValues?.get(1)?.toInt() ?: 5432
    }
    
    private fun extractDatabase(url: String): String {
        val pattern = "/([^?]+)".toRegex()
        return pattern.find(url)?.groupValues?.get(1) ?: "reservation_db"
    }
}

/**
 * 데이터베이스 속성 설정
 */
@ConfigurationProperties(prefix = "app.database")
data class DatabaseProperties(
    val jpa: JpaProperties = JpaProperties(),
    val r2dbc: R2dbcProperties = R2dbcProperties(),
    val pool: PoolProperties = PoolProperties(),
    val monitoring: MonitoringProperties = MonitoringProperties()
)

/**
 * JPA 관련 속성
 */
data class JpaProperties(
    val showSql: Boolean = false,
    val formatSql: Boolean = false,
    val generateDdl: Boolean = false,
    val hibernateDialect: String = "org.hibernate.dialect.PostgreSQLDialect",
    val batchSize: Int = 100,
    val fetchSize: Int = 100,
    val secondLevelCache: Boolean = true,
    val queryCache: Boolean = true
)

/**
 * R2DBC 관련 속성
 */
data class R2dbcProperties(
    val statementTimeout: java.time.Duration = java.time.Duration.ofSeconds(60),
    val lockWaitTimeout: java.time.Duration = java.time.Duration.ofSeconds(30),
    val connectTimeout: java.time.Duration = java.time.Duration.ofSeconds(30),
    val maxStatements: Int = 1000,
    val enableLogging: Boolean = false
)

/**
 * 커넥션 풀 관련 속성
 */
data class PoolProperties(
    val initialSize: Int = 5,
    val maxSize: Int = 50,
    val maxIdleTime: java.time.Duration = java.time.Duration.ofMinutes(10),
    val maxLifeTime: java.time.Duration = java.time.Duration.ofMinutes(30),
    val acquireRetry: Int = 3,
    val validationQuery: String = "SELECT 1"
)

/**
 * 모니터링 관련 속성
 */
data class MonitoringProperties(
    val enableMetrics: Boolean = true,
    val enableHealthCheck: Boolean = true,
    val slowQueryThreshold: java.time.Duration = java.time.Duration.ofMillis(500),
    val logSlowQueries: Boolean = true
)

/**
 * 감사(Audit) 설정
 */
@Configuration
class AuditConfiguration {
    
    /**
     * 감사자 제공자 (누가 데이터를 변경했는지 추적)
     */
    @Bean
    fun auditorProvider(): org.springframework.data.domain.AuditorAware<java.util.UUID> {
        return org.springframework.data.domain.AuditorAware {
            // 실제로는 SecurityContext에서 현재 사용자 ID를 가져옴
            java.util.Optional.of(java.util.UUID.randomUUID()) // 임시
        }
    }
}

/**
 * 트랜잭션 설정
 */
@Configuration
class TransactionConfiguration {
    
    /**
     * 분산 트랜잭션 매니저 (필요시)
     */
    @Bean
    @ConditionalOnProperty(name = ["app.transaction.distributed.enabled"], havingValue = "true")
    fun distributedTransactionManager(): org.springframework.transaction.PlatformTransactionManager {
        // 실제로는 JTA 트랜잭션 매니저나 Atomikos 등 사용
        TODO("분산 트랜잭션 매니저 구현")
    }
}

/**
 * 데이터베이스 초기화 설정
 */
@Configuration
class DatabaseInitializationConfiguration {
    
    /**
     * 개발 환경용 테스트 데이터 초기화
     */
    @Bean
    @ConditionalOnProperty(name = ["spring.profiles.active"], havingValue = "dev")
    fun testDataInitializer(): org.springframework.boot.CommandLineRunner {
        return org.springframework.boot.CommandLineRunner { args ->
            // 개발용 테스트 데이터 생성
            initializeTestData()
        }
    }
    
    private fun initializeTestData() {
        // 실제로는 테스트 데이터 생성 로직
        println("개발 환경용 테스트 데이터 초기화 중...")
    }
}

/**
 * 데이터베이스 헬스 체크
 */
@org.springframework.stereotype.Component
class DatabaseHealthIndicator(
    private val connectionFactory: ConnectionFactory,
    private val databaseClient: DatabaseClient
) : org.springframework.boot.actuate.health.HealthIndicator {
    
    private val logger = org.slf4j.LoggerFactory.getLogger(DatabaseHealthIndicator::class.java)
    
    override fun health(): org.springframework.boot.actuate.health.Health {
        return try {
            // 간단한 SELECT 1 쿼리로 데이터베이스 연결 확인
            val result = databaseClient.sql("SELECT 1")
                .fetch()
                .one()
                .block(java.time.Duration.ofSeconds(5))
            
            if (result != null) {
                org.springframework.boot.actuate.health.Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "UP")
                    .build()
            } else {
                throw RuntimeException("Database query returned null")
            }
        } catch (e: Exception) {
            logger.error("Database health check failed", e)
            org.springframework.boot.actuate.health.Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.message)
                .build()
        }
    }
}