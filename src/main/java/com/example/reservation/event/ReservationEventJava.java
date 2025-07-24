package com.example.reservation.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 예약 이벤트 계층 구조 (Java)
 * 
 * 특징:
 * 1. abstract class를 통한 이벤트 계층 구조
 * 2. Builder 패턴을 통한 객체 생성
 * 3. if-else/switch를 통한 조건 처리
 * 4. JSON 다형성 직렬화 지원
 * 
 * Java vs Kotlin 비교:
 * - abstract class vs sealed class
 * - Builder pattern vs data class with defaults
 * - 명시적 getter/setter vs property access
 * - switch/if-else vs when expression
 */

/**
 * 이벤트 기본 인터페이스
 */
interface DomainEventJava {
    String getEventId();
    String getAggregateId();
    String getEventType();
    LocalDateTime getTimestamp();
    int getVersion();
    String getCorrelationId();
    String getCausationId();
    Map<String, Object> getMetadata();
}

/**
 * 예약 이벤트 기본 추상 클래스
 * Java abstract class를 통한 계층 구조
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ReservationCreatedEventJava.class, name = "RESERVATION_CREATED"),
    @JsonSubTypes.Type(value = ReservationUpdatedEventJava.class, name = "RESERVATION_UPDATED"),
    @JsonSubTypes.Type(value = ReservationCancelledEventJava.class, name = "RESERVATION_CANCELLED"),
    @JsonSubTypes.Type(value = ReservationConfirmedEventJava.class, name = "RESERVATION_CONFIRMED"),
    @JsonSubTypes.Type(value = CheckInCompletedEventJava.class, name = "CHECK_IN_COMPLETED"),
    @JsonSubTypes.Type(value = CheckOutCompletedEventJava.class, name = "CHECK_OUT_COMPLETED"),
    @JsonSubTypes.Type(value = PaymentProcessedEventJava.class, name = "PAYMENT_PROCESSED"),
    @JsonSubTypes.Type(value = PaymentFailedEventJava.class, name = "PAYMENT_FAILED")
})
public abstract class ReservationEventJava implements DomainEventJava {
    public abstract Long getReservationId();
    public abstract Long getGuestId();
    public abstract Long getRoomId();
}

/**
 * 예약 생성 이벤트 (Java)
 * Java의 전통적인 클래스 정의와 Builder 패턴
 */
public class ReservationCreatedEventJava extends ReservationEventJava {
    private final String eventId;
    private final String aggregateId;
    private final LocalDateTime timestamp;
    private final int version;
    private final String correlationId;
    private final String causationId;
    private final Map<String, Object> metadata;
    
    // 예약 관련 필드
    private final Long reservationId;
    private final Long guestId;
    private final Long roomId;
    private final String confirmationNumber;
    private final LocalDate checkInDate;
    private final LocalDate checkOutDate;
    private final BigDecimal totalAmount;
    private final String status;
    private final List<String> specialRequests;
    private final String source;

    // Private 생성자 (Builder 패턴 사용)
    private ReservationCreatedEventJava(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.aggregateId = builder.aggregateId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.version = builder.version;
        this.correlationId = builder.correlationId;
        this.causationId = builder.causationId;
        this.metadata = builder.metadata != null ? builder.metadata : Collections.emptyMap();
        
        this.reservationId = builder.reservationId;
        this.guestId = builder.guestId;
        this.roomId = builder.roomId;
        this.confirmationNumber = builder.confirmationNumber;
        this.checkInDate = builder.checkInDate;
        this.checkOutDate = builder.checkOutDate;
        this.totalAmount = builder.totalAmount;
        this.status = builder.status;
        this.specialRequests = builder.specialRequests != null ? builder.specialRequests : Collections.emptyList();
        this.source = builder.source;
    }

    // Getter 메서드들
    @Override
    public String getEventId() { return eventId; }
    @Override
    public String getAggregateId() { return aggregateId; }
    @Override
    public String getEventType() { return "RESERVATION_CREATED"; }
    @Override
    public LocalDateTime getTimestamp() { return timestamp; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getCorrelationId() { return correlationId; }
    @Override
    public String getCausationId() { return causationId; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    
    @Override
    public Long getReservationId() { return reservationId; }
    @Override
    public Long getGuestId() { return guestId; }
    @Override
    public Long getRoomId() { return roomId; }
    
    public String getConfirmationNumber() { return confirmationNumber; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getStatus() { return status; }
    public List<String> getSpecialRequests() { return specialRequests; }
    public String getSource() { return source; }

    /**
     * 이벤트 정보 요약
     * Java의 String.format 활용
     */
    public String getSummary() {
        return String.format("예약 생성: %s (객실: %d, 금액: %s)", 
                           confirmationNumber, roomId, totalAmount);
    }

    /**
     * 숙박 일수 계산
     */
    public long getNights() {
        return ChronoUnit.DAYS.between(checkInDate, checkOutDate);
    }

    /**
     * Builder 패턴 구현
     */
    public static class Builder {
        private String eventId;
        private String aggregateId;
        private LocalDateTime timestamp;
        private int version = 1;
        private String correlationId;
        private String causationId;
        private Map<String, Object> metadata;
        
        private Long reservationId;
        private Long guestId;
        private Long roomId;
        private String confirmationNumber;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private BigDecimal totalAmount;
        private String status;
        private List<String> specialRequests;
        private String source;

        public Builder eventId(String eventId) { this.eventId = eventId; return this; }
        public Builder aggregateId(String aggregateId) { this.aggregateId = aggregateId; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder version(int version) { this.version = version; return this; }
        public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public Builder causationId(String causationId) { this.causationId = causationId; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        
        public Builder reservationId(Long reservationId) { this.reservationId = reservationId; return this; }
        public Builder guestId(Long guestId) { this.guestId = guestId; return this; }
        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder confirmationNumber(String confirmationNumber) { this.confirmationNumber = confirmationNumber; return this; }
        public Builder checkInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; return this; }
        public Builder checkOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; return this; }
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder specialRequests(List<String> specialRequests) { this.specialRequests = specialRequests; return this; }
        public Builder source(String source) { this.source = source; return this; }

        public ReservationCreatedEventJava build() {
            return new ReservationCreatedEventJava(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

/**
 * 예약 수정 이벤트 (Java)
 */
public class ReservationUpdatedEventJava extends ReservationEventJava {
    private final String eventId;
    private final String aggregateId;
    private final LocalDateTime timestamp;
    private final int version;
    private final String correlationId;
    private final String causationId;
    private final Map<String, Object> metadata;
    
    private final Long reservationId;
    private final Long guestId;
    private final Long roomId;
    private final Map<String, Object> changes;
    private final Map<String, Object> previousValues;
    private final String reason;

    private ReservationUpdatedEventJava(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.aggregateId = builder.aggregateId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.version = builder.version;
        this.correlationId = builder.correlationId;
        this.causationId = builder.causationId;
        this.metadata = builder.metadata != null ? builder.metadata : Collections.emptyMap();
        
        this.reservationId = builder.reservationId;
        this.guestId = builder.guestId;
        this.roomId = builder.roomId;
        this.changes = builder.changes;
        this.previousValues = builder.previousValues;
        this.reason = builder.reason;
    }

    // Getter 메서드들
    @Override
    public String getEventId() { return eventId; }
    @Override
    public String getAggregateId() { return aggregateId; }
    @Override
    public String getEventType() { return "RESERVATION_UPDATED"; }
    @Override
    public LocalDateTime getTimestamp() { return timestamp; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getCorrelationId() { return correlationId; }
    @Override
    public String getCausationId() { return causationId; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    
    @Override
    public Long getReservationId() { return reservationId; }
    @Override
    public Long getGuestId() { return guestId; }
    @Override
    public Long getRoomId() { return roomId; }
    
    public Map<String, Object> getChanges() { return changes; }
    public Map<String, Object> getPreviousValues() { return previousValues; }
    public String getReason() { return reason; }

    /**
     * 변경사항 요약 생성
     * Java Stream API 활용
     */
    public String getChangesSummary() {
        return changes.entrySet().stream()
                .map(entry -> {
                    String field = entry.getKey();
                    Object newValue = entry.getValue();
                    Object previousValue = previousValues.get(field);
                    return field + ": " + previousValue + " → " + newValue;
                })
                .reduce((s1, s2) -> s1 + ", " + s2)
                .orElse("");
    }

    public static class Builder {
        private String eventId;
        private String aggregateId;
        private LocalDateTime timestamp;
        private int version = 1;
        private String correlationId;
        private String causationId;
        private Map<String, Object> metadata;
        
        private Long reservationId;
        private Long guestId;
        private Long roomId;
        private Map<String, Object> changes;
        private Map<String, Object> previousValues;
        private String reason;

        // Builder 메서드들 (생략...)
        public Builder reservationId(Long reservationId) { this.reservationId = reservationId; return this; }
        public Builder guestId(Long guestId) { this.guestId = guestId; return this; }
        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder changes(Map<String, Object> changes) { this.changes = changes; return this; }
        public Builder previousValues(Map<String, Object> previousValues) { this.previousValues = previousValues; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }

        public ReservationUpdatedEventJava build() {
            return new ReservationUpdatedEventJava(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

/**
 * 예약 취소 이벤트 (Java)
 */
public class ReservationCancelledEventJava extends ReservationEventJava {
    private final String eventId;
    private final String aggregateId;
    private final LocalDateTime timestamp;
    private final int version;
    private final String correlationId;
    private final String causationId;
    private final Map<String, Object> metadata;
    
    private final Long reservationId;
    private final Long guestId;
    private final Long roomId;
    private final String cancellationReason;
    private final BigDecimal refundAmount;
    private final BigDecimal cancellationFee;
    private final String cancelledBy;

    private ReservationCancelledEventJava(Builder builder) {
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.aggregateId = builder.aggregateId;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.version = builder.version;
        this.correlationId = builder.correlationId;
        this.causationId = builder.causationId;
        this.metadata = builder.metadata != null ? builder.metadata : Collections.emptyMap();
        
        this.reservationId = builder.reservationId;
        this.guestId = builder.guestId;
        this.roomId = builder.roomId;
        this.cancellationReason = builder.cancellationReason;
        this.refundAmount = builder.refundAmount;
        this.cancellationFee = builder.cancellationFee;
        this.cancelledBy = builder.cancelledBy;
    }

    // Getter 메서드들
    @Override
    public String getEventId() { return eventId; }
    @Override
    public String getAggregateId() { return aggregateId; }
    @Override
    public String getEventType() { return "RESERVATION_CANCELLED"; }
    @Override
    public LocalDateTime getTimestamp() { return timestamp; }
    @Override
    public int getVersion() { return version; }
    @Override
    public String getCorrelationId() { return correlationId; }
    @Override
    public String getCausationId() { return causationId; }
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
    
    @Override
    public Long getReservationId() { return reservationId; }
    @Override
    public Long getGuestId() { return guestId; }
    @Override
    public Long getRoomId() { return roomId; }
    
    public String getCancellationReason() { return cancellationReason; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public BigDecimal getCancellationFee() { return cancellationFee; }
    public String getCancelledBy() { return cancelledBy; }

    /**
     * 환불 정보 확인
     */
    public boolean hasRefund() {
        return refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public static class Builder {
        private String eventId;
        private String aggregateId;
        private LocalDateTime timestamp;
        private int version = 1;
        private String correlationId;
        private String causationId;
        private Map<String, Object> metadata;
        
        private Long reservationId;
        private Long guestId;
        private Long roomId;
        private String cancellationReason;
        private BigDecimal refundAmount;
        private BigDecimal cancellationFee;
        private String cancelledBy;

        // Builder 메서드들
        public Builder reservationId(Long reservationId) { this.reservationId = reservationId; return this; }
        public Builder guestId(Long guestId) { this.guestId = guestId; return this; }
        public Builder roomId(Long roomId) { this.roomId = roomId; return this; }
        public Builder cancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; return this; }
        public Builder refundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; return this; }
        public Builder cancellationFee(BigDecimal cancellationFee) { this.cancellationFee = cancellationFee; return this; }
        public Builder cancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; return this; }

        public ReservationCancelledEventJava build() {
            return new ReservationCancelledEventJava(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}

// 추가 이벤트 클래스들도 유사한 패턴으로 구현
// (ReservationConfirmedEventJava, CheckInCompletedEventJava, etc.)

/**
 * 이벤트 유틸리티 클래스
 * Java의 정적 메서드를 통한 유틸리티 제공
 */
public class ReservationEventUtils {
    
    /**
     * 이벤트 심각도 판단
     * Java switch문 활용
     */
    public static EventSeverityJava getSeverity(ReservationEventJava event) {
        switch (event.getEventType()) {
            case "PAYMENT_FAILED":
                return EventSeverityJava.HIGH;
            case "RESERVATION_CANCELLED":
                return EventSeverityJava.MEDIUM;
            case "RESERVATION_CREATED":
            case "RESERVATION_CONFIRMED":
                return EventSeverityJava.LOW;
            default:
                return EventSeverityJava.LOW;
        }
    }

    /**
     * 이벤트가 알림 발송 대상인지 확인
     */
    public static boolean requiresNotification(ReservationEventJava event) {
        String eventType = event.getEventType();
        return "RESERVATION_CREATED".equals(eventType) ||
               "RESERVATION_CONFIRMED".equals(eventType) ||
               "RESERVATION_CANCELLED".equals(eventType) ||
               "CHECK_IN_COMPLETED".equals(eventType) ||
               "CHECK_OUT_COMPLETED".equals(eventType);
    }
}

/**
 * 이벤트 심각도 열거형 (Java)
 */
enum EventSeverityJava {
    LOW, MEDIUM, HIGH, CRITICAL
}