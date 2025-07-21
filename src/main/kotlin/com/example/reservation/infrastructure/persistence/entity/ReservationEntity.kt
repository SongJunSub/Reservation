package com.example.reservation.infrastructure.persistence.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * 예약 엔티티 (JPA + R2DBC 호환)
 * 실무 릴리즈 급 구현: 감사 기능, 소프트 삭제, 인덱스 최적화
 */
@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener::class)
data class ReservationEntity(
    @Id
    @Column("id")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "confirmation_number", nullable = false, unique = true, length = 20)
    @jakarta.persistence.Column(name = "confirmation_number", nullable = false, unique = true, length = 20)
    val confirmationNumber: String,

    @Column(name = "guest_id", nullable = false)
    @jakarta.persistence.Column(name = "guest_id", nullable = false)
    val guestId: UUID,

    @Column(name = "property_id", nullable = false)
    @jakarta.persistence.Column(name = "property_id", nullable = false)
    val propertyId: UUID,

    @Column(name = "room_type_id", nullable = false)
    @jakarta.persistence.Column(name = "room_type_id", nullable = false)
    val roomTypeId: UUID,

    @Column(name = "check_in_date", nullable = false)
    @jakarta.persistence.Column(name = "check_in_date", nullable = false)
    val checkInDate: LocalDate,

    @Column(name = "check_out_date", nullable = false)
    @jakarta.persistence.Column(name = "check_out_date", nullable = false)
    val checkOutDate: LocalDate,

    @Column(name = "adult_count", nullable = false)
    @jakarta.persistence.Column(name = "adult_count", nullable = false)
    val adultCount: Int = 1,

    @Column(name = "child_count", nullable = false)
    @jakarta.persistence.Column(name = "child_count", nullable = false)
    val childCount: Int = 0,

    @Column(name = "infant_count", nullable = false)
    @jakarta.persistence.Column(name = "infant_count", nullable = false)
    val infantCount: Int = 0,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    @jakarta.persistence.Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    @jakarta.persistence.Column(name = "currency", nullable = false, length = 3)
    val currency: String = "KRW",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @jakarta.persistence.Column(name = "status", nullable = false, length = 20)
    val status: ReservationStatus = ReservationStatus.PENDING,

    @Column(name = "payment_id")
    @jakarta.persistence.Column(name = "payment_id")
    val paymentId: UUID? = null,

    @Column(name = "special_requests", length = 1000)
    @jakarta.persistence.Column(name = "special_requests", length = 1000)
    val specialRequests: String? = null,

    @Column(name = "rate_plan_code", length = 50)
    @jakarta.persistence.Column(name = "rate_plan_code", length = 50)
    val ratePlanCode: String? = null,

    @Column(name = "promotion_code", length = 50)
    @jakarta.persistence.Column(name = "promotion_code", length = 50)
    val promotionCode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    @jakarta.persistence.Column(name = "source", nullable = false, length = 20)
    val source: ReservationSource = ReservationSource.WEBSITE,

    // 체크인/체크아웃 시간
    @Column(name = "actual_check_in_time")
    @jakarta.persistence.Column(name = "actual_check_in_time")
    val actualCheckInTime: LocalDateTime? = null,

    @Column(name = "actual_check_out_time")
    @jakarta.persistence.Column(name = "actual_check_out_time")
    val actualCheckOutTime: LocalDateTime? = null,

    @Column(name = "estimated_check_in_time")
    @jakarta.persistence.Column(name = "estimated_check_in_time")
    val estimatedCheckInTime: LocalDateTime? = null,

    @Column(name = "estimated_check_out_time")
    @jakarta.persistence.Column(name = "estimated_check_out_time")
    val estimatedCheckOutTime: LocalDateTime? = null,

    // 취소 관련
    @Column(name = "cancellation_deadline")
    @jakarta.persistence.Column(name = "cancellation_deadline")
    val cancellationDeadline: LocalDateTime? = null,

    @Column(name = "cancelled_at")
    @jakarta.persistence.Column(name = "cancelled_at")
    val cancelledAt: LocalDateTime? = null,

    @Column(name = "cancellation_reason", length = 100)
    @jakarta.persistence.Column(name = "cancellation_reason", length = 100)
    val cancellationReason: String? = null,

    @Column(name = "refund_amount", precision = 19, scale = 2)
    @jakarta.persistence.Column(name = "refund_amount", precision = 19, scale = 2)
    val refundAmount: BigDecimal? = null,

    // 마케팅 및 커뮤니케이션
    @Column(name = "marketing_opt_in", nullable = false)
    @jakarta.persistence.Column(name = "marketing_opt_in", nullable = false)
    val marketingOptIn: Boolean = false,

    @Column(name = "communication_preferences", length = 200)
    @jakarta.persistence.Column(name = "communication_preferences", length = 200)
    val communicationPreferences: String? = null, // JSON 형태로 저장

    // 감사 필드
    @CreatedDate
    @Column(name = "created_at", nullable = false)
    @jakarta.persistence.Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    @jakarta.persistence.Column(name = "modified_at", nullable = false)
    val modifiedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by")
    @jakarta.persistence.Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "modified_by")
    @jakarta.persistence.Column(name = "modified_by")
    val modifiedBy: UUID? = null,

    // 소프트 삭제
    @Column(name = "is_deleted", nullable = false)
    @jakarta.persistence.Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false,

    @Column(name = "deleted_at")
    @jakarta.persistence.Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @Column(name = "deleted_by")
    @jakarta.persistence.Column(name = "deleted_by")
    val deletedBy: UUID? = null,

    // 버전 관리 (낙관적 락)
    @Version
    @Column(name = "version", nullable = false)
    @jakarta.persistence.Column(name = "version", nullable = false)
    val version: Long = 0,

    // 메타데이터 (JSON)
    @Column(name = "metadata", length = 2000)
    @jakarta.persistence.Column(name = "metadata", length = 2000)
    val metadata: String? = null
) {
    
    /**
     * 예약 상태 검증
     */
    fun isActive(): Boolean = !isDeleted && status != ReservationStatus.CANCELLED

    /**
     * 취소 가능 여부 확인
     */
    fun canBeCancelled(): Boolean {
        val now = LocalDateTime.now()
        return isActive() && 
               status in listOf(ReservationStatus.CONFIRMED, ReservationStatus.PENDING) &&
               (cancellationDeadline == null || now.isBefore(cancellationDeadline))
    }

    /**
     * 수정 가능 여부 확인
     */
    fun canBeModified(): Boolean {
        return isActive() && 
               status in listOf(ReservationStatus.CONFIRMED, ReservationStatus.PENDING) &&
               checkInDate.isAfter(LocalDate.now())
    }

    /**
     * 체크인 가능 여부 확인
     */
    fun canCheckIn(): Boolean {
        val today = LocalDate.now()
        return status == ReservationStatus.CONFIRMED &&
               !checkInDate.isAfter(today) &&
               actualCheckInTime == null
    }

    /**
     * 체크아웃 가능 여부 확인
     */
    fun canCheckOut(): Boolean {
        return status == ReservationStatus.CHECKED_IN &&
               actualCheckInTime != null &&
               actualCheckOutTime == null
    }

    /**
     * 숙박 일수 계산
     */
    fun getNights(): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(checkInDate, checkOutDate)
    }

    /**
     * 총 인원수 계산
     */
    fun getTotalGuests(): Int = adultCount + childCount + infantCount

    companion object {
        /**
         * 확인번호 생성
         */
        fun generateConfirmationNumber(): String {
            val timestamp = System.currentTimeMillis().toString().takeLast(6)
            val random = (100..999).random().toString()
            return "RSV$timestamp$random"
        }
    }
}

/**
 * 예약 상태 열거형
 */
enum class ReservationStatus {
    PENDING,        // 대기중
    CONFIRMED,      // 확정
    CHECKED_IN,     // 체크인
    CHECKED_OUT,    // 체크아웃
    COMPLETED,      // 완료
    CANCELLED,      // 취소
    NO_SHOW,        // 노쇼
    MODIFIED        // 수정됨
}

/**
 * 예약 출처 열거형
 */
enum class ReservationSource {
    WEBSITE,        // 웹사이트
    MOBILE_APP,     // 모바일 앱
    PHONE,          // 전화
    EMAIL,          // 이메일
    WALK_IN,        // 현장 접수
    TRAVEL_AGENT,   // 여행사
    OTA,            // 온라인 여행사
    CORPORATE,      // 기업
    GROUP,          // 단체
    API             // API
}