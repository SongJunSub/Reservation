package com.example.reservation.cache

import com.example.reservation.cache.CacheConfig.Companion.RESERVATION_CACHE
import com.example.reservation.cache.CacheConfig.Companion.ROOM_CACHE
import com.example.reservation.cache.CacheConfig.Companion.STATISTICS_CACHE
import com.example.reservation.cache.CacheConfig.Companion.USER_CACHE
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 캐시 워밍업 서비스 (Kotlin)
 * 
 * 기능:
 * 1. 애플리케이션 시작시 캐시 사전 로딩
 * 2. 주기적 캐시 갱신
 * 3. 캐시 건강 상태 모니터링
 * 4. 전략적 캐시 무효화
 * 
 * Kotlin 특징:
 * - 코루틴을 통한 비동기 처리
 * - data class를 통한 간결한 데이터 구조
 * - when 표현식을 통한 조건 처리
 * - 확장 함수 활용
 */
@Service
class CacheWarmupService(
    private val cacheService: CacheService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(CacheWarmupService::class.java)
    }

    /**
     * 애플리케이션 시작시 캐시 워밍업
     * Spring의 @EventListener 활용
     */
    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun warmupCachesOnStartup() {
        logger.info("애플리케이션 시작: 캐시 워밍업 시작")
        
        try {
            // 병렬로 각 캐시 워밍업 실행
            warmupReservationCache()
            warmupRoomCache()  
            warmupUserCache()
            warmupStatisticsCache()
            
            logger.info("캐시 워밍업 완료")
        } catch (ex: Exception) {
            logger.error("캐시 워밍업 중 오류 발생", ex)
        }
    }

    /**
     * 주기적 캐시 갱신 (매 시간)
     * Spring의 @Scheduled 활용
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    @Async
    fun refreshCachesScheduled() {
        logger.debug("주기적 캐시 갱신 시작")
        
        try {
            refreshHotData()
            cleanupExpiredCaches()
            
            logger.debug("주기적 캐시 갱신 완료")
        } catch (ex: Exception) {
            logger.error("주기적 캐시 갱신 중 오류", ex)
        }
    }

    /**
     * 캐시 건강 상태 체크 (매 5분)
     */
    @Scheduled(fixedRate = 300000) // 5분마다
    fun checkCacheHealth() {
        try {
            val metrics = cacheService.getAllCacheMetrics()
            
            metrics.forEach { metric ->
                when {
                    metric.hitRate < 0.5 && metric.total > 100 -> {
                        logger.warn("낮은 캐시 히트율 감지: {} (히트율: {:.2f}%)", 
                                  metric.cacheName, metric.hitRate * 100)
                        // 캐시 전략 재검토 필요
                    }
                    metric.total == 0L -> {
                        logger.debug("사용되지 않은 캐시: {}", metric.cacheName)
                    }
                    else -> {
                        logger.debug("캐시 상태 양호: {} (히트율: {:.2f}%)", 
                                  metric.cacheName, metric.hitRate * 100)
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("캐시 건강 상태 체크 실패", ex)
        }
    }

    /**
     * 예약 캐시 워밍업
     * 최근 예약과 활성 예약을 미리 로드
     */
    private fun warmupReservationCache() {
        cacheService.warmUpCache(RESERVATION_CACHE) {
            // 실제 구현에서는 ReservationRepository에서 데이터 조회
            mapOf(
                "recent_reservations" to generateMockReservations(50),
                "active_reservations" to generateMockReservations(100),
                "today_checkins" to generateMockReservations(20),
                "today_checkouts" to generateMockReservations(15)
            )
        }
    }

    /**
     * 객실 캐시 워밍업
     * 모든 객실 정보와 가용성 데이터 로드
     */
    private fun warmupRoomCache() {
        cacheService.warmUpCache(ROOM_CACHE) {
            // 실제 구현에서는 RoomRepository에서 데이터 조회
            mapOf(
                "all_rooms" to generateMockRooms(200),
                "available_rooms" to generateMockRooms(150),
                "premium_rooms" to generateMockRooms(50),
                "room_amenities" to generateMockAmenities()
            )
        }
    }

    /**
     * 사용자 캐시 워밍업
     * 활성 사용자와 VIP 고객 정보 로드
     */
    private fun warmupUserCache() {
        cacheService.warmUpCache(USER_CACHE) {
            // 실제 구현에서는 UserRepository에서 데이터 조회
            mapOf(
                "active_users" to generateMockUsers(500),
                "vip_customers" to generateMockUsers(100),
                "recent_signups" to generateMockUsers(50)
            )
        }
    }

    /**
     * 통계 캐시 워밍업
     * 대시보드용 통계 데이터 사전 계산
     */
    private fun warmupStatisticsCache() {
        cacheService.warmUpCache(STATISTICS_CACHE) {
            // 실제 구현에서는 복잡한 통계 쿼리 실행
            mapOf(
                "daily_stats" to generateDailyStats(),
                "monthly_stats" to generateMonthlyStats(),
                "occupancy_rates" to generateOccupancyRates(),
                "revenue_stats" to generateRevenueStats()
            )
        }
    }

    /**
     * 핫 데이터 갱신
     * 자주 변경되는 데이터의 캐시 갱신
     */
    private fun refreshHotData() {
        // 실시간성이 중요한 데이터 갱신
        refreshAvailabilityCache()
        refreshSearchCache()
        refreshRecentActivityCache()
    }

    /**
     * 가용성 캐시 갱신
     */
    private fun refreshAvailabilityCache() {
        try {
            // 실제 구현에서는 실시간 가용성 계산
            val availabilityData = mapOf(
                "current_availability" to "mock_availability_data",
                "next_30_days" to "mock_30_day_availability"
            )
            
            cacheService.multiPut(CacheConfig.AVAILABILITY_CACHE, availabilityData)
            logger.debug("가용성 캐시 갱신 완료")
        } catch (ex: Exception) {
            logger.warn("가용성 캐시 갱신 실패", ex)
        }
    }

    /**
     * 검색 캐시 갱신
     */
    private fun refreshSearchCache() {
        try {
            // 인기 검색어와 검색 결과 갱신
            val searchData = mapOf(
                "popular_searches" to generatePopularSearches(),
                "cached_results" to generateCachedSearchResults()
            )
            
            cacheService.multiPut(CacheConfig.SEARCH_CACHE, searchData)
            logger.debug("검색 캐시 갱신 완료")
        } catch (ex: Exception) {
            logger.warn("검색 캐시 갱신 실패", ex)
        }
    }

    /**
     * 최근 활동 캐시 갱신
     */
    private fun refreshRecentActivityCache() {
        try {
            // 최근 예약, 취소, 수정 등의 활동 로그
            val activityData = mapOf(
                "recent_bookings" to generateRecentActivities("booking"),
                "recent_cancellations" to generateRecentActivities("cancellation"),
                "recent_modifications" to generateRecentActivities("modification")
            )
            
            cacheService.multiPut(RESERVATION_CACHE, activityData)
            logger.debug("최근 활동 캐시 갱신 완료")
        } catch (ex: Exception) {
            logger.warn("최근 활동 캐시 갱신 실패", ex)
        }
    }

    /**
     * 만료된 캐시 정리
     */
    private fun cleanupExpiredCaches() {
        try {
            // 패턴 기반으로 만료된 캐시 삭제
            cacheService.evictByPattern(CacheConfig.SEARCH_CACHE, "temp:*")
            cacheService.evictByPattern(CacheConfig.STATISTICS_CACHE, "hourly:*")
            
            logger.debug("만료된 캐시 정리 완료")
        } catch (ex: Exception) {
            logger.warn("캐시 정리 실패", ex)
        }
    }

    // === Mock 데이터 생성 헬퍼 메서드들 ===
    // 실제 구현에서는 실제 데이터베이스에서 조회

    private fun generateMockReservations(count: Int): List<Map<String, Any>> =
        (1..count).map { 
            mapOf(
                "id" to it,
                "guestName" to "Guest$it",
                "roomNumber" to "10$it",
                "checkIn" to LocalDateTime.now().plusDays(it.toLong()),
                "status" to "CONFIRMED"
            )
        }

    private fun generateMockRooms(count: Int): List<Map<String, Any>> =
        (1..count).map {
            mapOf(
                "id" to it,
                "roomNumber" to "Room$it",
                "type" to "STANDARD",
                "available" to true
            )
        }

    private fun generateMockUsers(count: Int): List<Map<String, Any>> =
        (1..count).map {
            mapOf(
                "id" to it,
                "username" to "user$it",
                "email" to "user$it@example.com",
                "active" to true
            )
        }

    private fun generateMockAmenities(): Map<String, Any> =
        mapOf(
            "wifi" to true,
            "parking" to true,
            "pool" to false,
            "gym" to true
        )

    private fun generateDailyStats(): Map<String, Any> =
        mapOf(
            "totalBookings" to 150,
            "totalRevenue" to 45000.0,
            "occupancyRate" to 0.85,
            "timestamp" to LocalDateTime.now()
        )

    private fun generateMonthlyStats(): Map<String, Any> =
        mapOf(
            "totalBookings" to 4500,
            "totalRevenue" to 1350000.0,
            "avgOccupancyRate" to 0.78,
            "timestamp" to LocalDateTime.now()
        )

    private fun generateOccupancyRates(): Map<String, Double> =
        mapOf(
            "today" to 0.85,
            "thisWeek" to 0.78,
            "thisMonth" to 0.82
        )

    private fun generateRevenueStats(): Map<String, Any> =
        mapOf(
            "dailyRevenue" to 45000.0,
            "weeklyRevenue" to 315000.0,
            "monthlyRevenue" to 1350000.0
        )

    private fun generatePopularSearches(): List<String> =
        listOf("서울", "부산", "제주도", "강원도", "경주")

    private fun generateCachedSearchResults(): Map<String, List<String>> =
        mapOf(
            "seoul_hotels" to listOf("Hotel A", "Hotel B", "Hotel C"),
            "busan_resorts" to listOf("Resort X", "Resort Y", "Resort Z")
        )

    private fun generateRecentActivities(type: String): List<Map<String, Any>> =
        (1..10).map {
            mapOf(
                "id" to it,
                "type" to type,
                "timestamp" to LocalDateTime.now().minusHours(it.toLong()),
                "details" to "Activity $type $it"
            )
        }
}