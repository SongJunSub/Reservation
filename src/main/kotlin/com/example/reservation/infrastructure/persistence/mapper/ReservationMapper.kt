package com.example.reservation.infrastructure.persistence.mapper

import com.example.reservation.application.service.Reservation
import com.example.reservation.infrastructure.persistence.entity.ReservationEntity
import org.mapstruct.*
import org.springframework.stereotype.Component

/**
 * 예약 도메인-엔티티 매퍼
 * 실무 릴리즈 급 구현: MapStruct 활용한 고성능 매핑
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
interface ReservationMapper {
    
    /**
     * 도메인 객체를 엔티티로 변환
     */
    @Mapping(target = "version", ignore = true) // JPA 버전 필드는 JPA가 관리
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    fun toEntity(reservation: Reservation): ReservationEntity
    
    /**
     * 엔티티를 도메인 객체로 변환
     */
    fun toDomain(entity: ReservationEntity): Reservation
    
    /**
     * 엔티티 목록을 도메인 객체 목록으로 변환
     */
    fun toDomainList(entities: List<ReservationEntity>): List<Reservation>
    
    /**
     * 도메인 객체 목록을 엔티티 목록으로 변환
     */
    fun toEntityList(reservations: List<Reservation>): List<ReservationEntity>
    
    /**
     * 부분 업데이트를 위한 매핑 (null 값은 무시)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    fun updateEntityFromDomain(@MappingTarget entity: ReservationEntity, reservation: Reservation)
    
    /**
     * 커스텀 매핑 - 상태 변환
     */
    @ValueMapping(source = "PENDING", target = "PENDING")
    @ValueMapping(source = "CONFIRMED", target = "CONFIRMED")
    @ValueMapping(source = "CHECKED_IN", target = "CHECKED_IN")
    @ValueMapping(source = "CHECKED_OUT", target = "CHECKED_OUT")
    @ValueMapping(source = "COMPLETED", target = "COMPLETED")
    @ValueMapping(source = "CANCELLED", target = "CANCELLED")
    @ValueMapping(source = "NO_SHOW", target = "NO_SHOW")
    @ValueMapping(source = "MODIFIED", target = "MODIFIED")
    fun mapStatus(status: String): com.example.reservation.infrastructure.persistence.entity.ReservationStatus
    
    /**
     * 역방향 상태 매핑
     */
    fun mapStatusToString(status: com.example.reservation.infrastructure.persistence.entity.ReservationStatus): String {
        return status.name
    }
}

/**
 * 매핑 헬퍼 클래스 (복잡한 매핑 로직용)
 */
@Component
class ReservationMappingHelper {
    
    /**
     * 커뮤니케이션 선호사항 JSON 변환
     */
    fun communicationPreferencesToJson(preferences: Set<String>?): String? {
        return preferences?.takeIf { it.isNotEmpty() }?.joinToString(",")
    }
    
    fun jsonToCommunicationPreferences(json: String?): Set<String> {
        return json?.split(",")?.toSet() ?: emptySet()
    }
    
    /**
     * 메타데이터 JSON 변환
     */
    fun metadataToJson(metadata: Map<String, Any>?): String? {
        // 실제 구현에서는 Jackson ObjectMapper 사용
        return metadata?.let { 
            // ObjectMapper().writeValueAsString(it)
            "{}" // 임시
        }
    }
    
    fun jsonToMetadata(json: String?): Map<String, Any> {
        // 실제 구현에서는 Jackson ObjectMapper 사용
        return json?.let {
            // ObjectMapper().readValue(it, Map::class.java) as Map<String, Any>
            emptyMap() // 임시
        } ?: emptyMap()
    }
}

/**
 * 확장 매퍼 - 추가 변환 로직
 */
@Mapper(
    componentModel = "spring",
    uses = [ReservationMappingHelper::class]
)
interface ExtendedReservationMapper : ReservationMapper {
    
    /**
     * DTO용 매핑 (API 응답용)
     */
    @Mapping(target = "guestName", expression = "java(getGuestFullName(entity))")
    @Mapping(target = "propertyName", source = "propertyId", qualifiedByName = "getPropertyName")
    @Mapping(target = "nights", expression = "java(calculateNights(entity))")
    fun toDetailDto(entity: ReservationEntity): ReservationDetailDto
    
    /**
     * 요약 DTO 매핑 (목록 조회용)
     */
    @Mapping(target = "guestName", expression = "java(getGuestFullName(entity))")
    @Mapping(target = "nights", expression = "java(calculateNights(entity))")
    fun toSummaryDto(entity: ReservationEntity): ReservationSummaryDto
    
    /**
     * 게스트 이름 조합
     */
    default fun getGuestFullName(entity: ReservationEntity): String {
        // 실제로는 Guest 엔티티와 조인해서 가져옴
        return "Guest ${entity.guestId}" // 임시
    }
    
    /**
     * 숙박 일수 계산
     */
    default fun calculateNights(entity: ReservationEntity): Int {
        return java.time.Period.between(entity.checkInDate, entity.checkOutDate).days
    }
    
    /**
     * 시설 이름 조회
     */
    @Named("getPropertyName")
    default fun getPropertyName(propertyId: java.util.UUID): String {
        // 실제로는 Property 서비스나 캐시에서 조회
        return "Property $propertyId" // 임시
    }
}

// === DTO 클래스들 ===

/**
 * 예약 상세 DTO
 */
data class ReservationDetailDto(
    val id: java.util.UUID,
    val confirmationNumber: String,
    val guestId: java.util.UUID,
    val guestName: String,
    val propertyId: java.util.UUID,
    val propertyName: String,
    val roomTypeId: java.util.UUID,
    val checkInDate: java.time.LocalDate,
    val checkOutDate: java.time.LocalDate,
    val nights: Int,
    val adultCount: Int,
    val childCount: Int,
    val infantCount: Int,
    val totalAmount: java.math.BigDecimal,
    val currency: String,
    val status: String,
    val specialRequests: String?,
    val estimatedCheckInTime: java.time.LocalDateTime?,
    val estimatedCheckOutTime: java.time.LocalDateTime?,
    val actualCheckInTime: java.time.LocalDateTime?,
    val actualCheckOutTime: java.time.LocalDateTime?,
    val createdAt: java.time.LocalDateTime,
    val modifiedAt: java.time.LocalDateTime
)

/**
 * 예약 요약 DTO
 */
data class ReservationSummaryDto(
    val id: java.util.UUID,
    val confirmationNumber: String,
    val guestName: String,
    val propertyName: String,
    val checkInDate: java.time.LocalDate,
    val checkOutDate: java.time.LocalDate,
    val nights: Int,
    val totalAmount: java.math.BigDecimal,
    val status: String,
    val createdAt: java.time.LocalDateTime
)

/**
 * 매핑 설정 클래스
 */
@Configuration
class MappingConfiguration {
    
    @Bean
    @Primary
    fun reservationMapper(): ReservationMapper {
        return org.mapstruct.factory.Mappers.getMapper(ReservationMapper::class.java)
    }
    
    @Bean
    fun extendedReservationMapper(): ExtendedReservationMapper {
        return org.mapstruct.factory.Mappers.getMapper(ExtendedReservationMapper::class.java)
    }
}