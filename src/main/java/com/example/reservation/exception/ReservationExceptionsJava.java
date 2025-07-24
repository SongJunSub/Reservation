package com.example.reservation.exception;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Java 예약 시스템 예외 계층
 * Kotlin과의 비교를 위한 Java 구현
 * 
 * 주요 차이점:
 * 1. 추상 클래스와 상속 구조가 더 verbose
 * 2. Map 초기화가 더 복잡 
 * 3. 빌더 패턴 활용 가능
 * 4. 생성자 오버로딩이 더 명시적
 */

/**
 * 최상위 비즈니스 예외 (추상 클래스)
 */
public abstract class BusinessExceptionJava extends RuntimeException {
    
    private final String errorCode;
    private final Map<String, Object> errorDetails;
    private final LocalDateTime timestamp;
    
    protected BusinessExceptionJava(String message, Throwable cause, String errorCode, Map<String, Object> errorDetails) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorDetails = errorDetails != null ? new HashMap<>(errorDetails) : new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }
    
    protected BusinessExceptionJava(String message, String errorCode) {
        this(message, null, errorCode, new HashMap<>());
    }
    
    protected BusinessExceptionJava(String message, String errorCode, Map<String, Object> errorDetails) {
        this(message, null, errorCode, errorDetails);
    }
    
    public abstract int getHttpStatus();
    
    // Getters
    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getErrorDetails() { return new HashMap<>(errorDetails); }
    public LocalDateTime getTimestamp() { return timestamp; }
}

/**
 * 예약 도메인 예외 기본 클래스
 */
public abstract class ReservationExceptionJava extends BusinessExceptionJava {
    
    protected ReservationExceptionJava(String message, Throwable cause, String errorCode, Map<String, Object> errorDetails) {
        super(message, cause, errorCode, errorDetails);
    }
    
    protected ReservationExceptionJava(String message, String errorCode) {
        super(message, errorCode);
    }
    
    protected ReservationExceptionJava(String message, String errorCode, Map<String, Object> errorDetails) {
        super(message, errorCode, errorDetails);
    }
}

// === 예약 생성 관련 예외 ===

/**
 * 예약 생성 실패 예외
 */
public class ReservationCreationExceptionJava extends ReservationExceptionJava {
    
    public ReservationCreationExceptionJava(String message) {
        super(message, "RESERVATION_CREATION_FAILED");
    }
    
    public ReservationCreationExceptionJava(String message, Throwable cause) {
        super(message, cause, "RESERVATION_CREATION_FAILED", new HashMap<>());
    }
    
    public ReservationCreationExceptionJava(String message, Map<String, Object> errorDetails) {
        super(message, "RESERVATION_CREATION_FAILED", errorDetails);
    }
    
    @Override
    public int getHttpStatus() {
        return 400; // Bad Request
    }
}

/**
 * 객실 가용성 부족 예외
 */
public class RoomUnavailableExceptionJava extends ReservationExceptionJava {
    
    private final Long roomId;
    private final String requestedCheckIn;
    private final String requestedCheckOut;
    
    public RoomUnavailableExceptionJava(String message, Long roomId, String requestedCheckIn, String requestedCheckOut) {
        super(message, "ROOM_UNAVAILABLE", createErrorDetails(roomId, requestedCheckIn, requestedCheckOut));
        this.roomId = roomId;
        this.requestedCheckIn = requestedCheckIn;
        this.requestedCheckOut = requestedCheckOut;
    }
    
    public RoomUnavailableExceptionJava(String message, Long roomId, String requestedCheckIn, String requestedCheckOut, Throwable cause) {
        super(message, cause, "ROOM_UNAVAILABLE", createErrorDetails(roomId, requestedCheckIn, requestedCheckOut));
        this.roomId = roomId;
        this.requestedCheckIn = requestedCheckIn;
        this.requestedCheckOut = requestedCheckOut;
    }
    
    private static Map<String, Object> createErrorDetails(Long roomId, String checkIn, String checkOut) {
        Map<String, Object> details = new HashMap<>();
        details.put("roomId", roomId);
        details.put("requestedCheckIn", checkIn);
        details.put("requestedCheckOut", checkOut);
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 409; // Conflict
    }
    
    // Getters
    public Long getRoomId() { return roomId; }
    public String getRequestedCheckIn() { return requestedCheckIn; }
    public String getRequestedCheckOut() { return requestedCheckOut; }
}

/**
 * 예약 정책 위반 예외
 */
public class ReservationPolicyViolationExceptionJava extends ReservationExceptionJava {
    
    private final String policyType;
    private final String violatedRule;
    
    public ReservationPolicyViolationExceptionJava(String message, String policyType, String violatedRule) {
        super(message, "POLICY_VIOLATION", createErrorDetails(policyType, violatedRule));
        this.policyType = policyType;
        this.violatedRule = violatedRule;
    }
    
    private static Map<String, Object> createErrorDetails(String policyType, String violatedRule) {
        Map<String, Object> details = new HashMap<>();
        details.put("policyType", policyType);
        details.put("violatedRule", violatedRule);
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 422; // Unprocessable Entity
    }
    
    // Getters
    public String getPolicyType() { return policyType; }
    public String getViolatedRule() { return violatedRule; }
}

// === 예약 조회 관련 예외 ===

/**
 * 예약 미발견 예외
 */
public class ReservationNotFoundExceptionJava extends ReservationExceptionJava {
    
    private final String searchCriteria;
    private final String searchValue;
    
    public ReservationNotFoundExceptionJava(String searchCriteria, String searchValue) {
        super(
            String.format("예약을 찾을 수 없습니다: %s = %s", searchCriteria, searchValue),
            "RESERVATION_NOT_FOUND",
            createErrorDetails(searchCriteria, searchValue)
        );
        this.searchCriteria = searchCriteria;
        this.searchValue = searchValue;
    }
    
    private static Map<String, Object> createErrorDetails(String criteria, String value) {
        Map<String, Object> details = new HashMap<>();
        details.put("searchCriteria", criteria);
        details.put("searchValue", value);
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 404; // Not Found
    }
    
    // Getters
    public String getSearchCriteria() { return searchCriteria; }
    public String getSearchValue() { return searchValue; }
}

// === 검증 관련 예외 ===

/**
 * 입력 검증 예외
 * Java의 복잡한 Map 처리 방식 보여줌
 */
public class ValidationExceptionJava extends BusinessExceptionJava {
    
    private final Map<String, String> fieldErrors;
    private final java.util.List<String> globalErrors;
    
    public ValidationExceptionJava(String message, Map<String, String> fieldErrors, java.util.List<String> globalErrors) {
        super(message, "VALIDATION_FAILED", createErrorDetails(fieldErrors, globalErrors));
        this.fieldErrors = fieldErrors != null ? new HashMap<>(fieldErrors) : new HashMap<>();
        this.globalErrors = globalErrors != null ? new java.util.ArrayList<>(globalErrors) : new java.util.ArrayList<>();
    }
    
    public ValidationExceptionJava(String message) {
        this(message, new HashMap<>(), new java.util.ArrayList<>());
    }
    
    private static Map<String, Object> createErrorDetails(Map<String, String> fieldErrors, java.util.List<String> globalErrors) {
        Map<String, Object> details = new HashMap<>();
        details.put("fieldErrors", fieldErrors != null ? fieldErrors : new HashMap<>());
        details.put("globalErrors", globalErrors != null ? globalErrors : new java.util.ArrayList<>());
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 400; // Bad Request
    }
    
    // Getters
    public Map<String, String> getFieldErrors() {
        return new HashMap<>(fieldErrors);
    }
    
    public java.util.List<String> getGlobalErrors() {
        return new java.util.ArrayList<>(globalErrors);
    }
    
    // === Static Factory Methods (Java 패턴) ===
    
    /**
     * 단일 필드 검증 실패
     */
    public static ValidationExceptionJava fieldError(String field, String error) {
        Map<String, String> fieldErrors = new HashMap<>();
        fieldErrors.put(field, error);
        return new ValidationExceptionJava("검증 실패: " + field, fieldErrors, new java.util.ArrayList<>());
    }
    
    /**
     * 다중 필드 검증 실패
     */
    public static ValidationExceptionJava multipleFieldErrors(Map<String, String> errors) {
        return new ValidationExceptionJava("다중 필드 검증 실패", errors, new java.util.ArrayList<>());
    }
    
    /**
     * 글로벌 검증 실패
     */
    public static ValidationExceptionJava globalError(String error) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        errors.add(error);
        return new ValidationExceptionJava("글로벌 검증 실패", new HashMap<>(), errors);
    }
    
    /**
     * 빌더 패턴을 활용한 ValidationException 생성
     */
    public static class Builder {
        private String message = "검증 실패";
        private Map<String, String> fieldErrors = new HashMap<>();
        private java.util.List<String> globalErrors = new java.util.ArrayList<>();
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder fieldError(String field, String error) {
            this.fieldErrors.put(field, error);
            return this;
        }
        
        public Builder fieldErrors(Map<String, String> errors) {
            this.fieldErrors.putAll(errors);
            return this;
        }
        
        public Builder globalError(String error) {
            this.globalErrors.add(error);
            return this;
        }
        
        public Builder globalErrors(java.util.List<String> errors) {
            this.globalErrors.addAll(errors);
            return this;
        }
        
        public ValidationExceptionJava build() {
            return new ValidationExceptionJava(message, fieldErrors, globalErrors);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
}

// === 결제 관련 예외 ===

/**
 * 결제 처리 예외
 */
public class PaymentProcessingExceptionJava extends BusinessExceptionJava {
    
    private final String paymentId;
    private final String paymentMethod;
    
    public PaymentProcessingExceptionJava(String message, String paymentId, String paymentMethod) {
        super(message, "PAYMENT_PROCESSING_FAILED", createErrorDetails(paymentId, paymentMethod));
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }
    
    public PaymentProcessingExceptionJava(String message, String paymentId, String paymentMethod, Throwable cause) {
        super(message, cause, "PAYMENT_PROCESSING_FAILED", createErrorDetails(paymentId, paymentMethod));
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }
    
    private static Map<String, Object> createErrorDetails(String paymentId, String paymentMethod) {
        Map<String, Object> details = new HashMap<>();
        details.put("paymentId", paymentId != null ? paymentId : "unknown");
        details.put("paymentMethod", paymentMethod != null ? paymentMethod : "unknown");
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 402; // Payment Required
    }
    
    // Getters
    public String getPaymentId() { return paymentId; }
    public String getPaymentMethod() { return paymentMethod; }
}

// === 동시성 관련 예외 ===

/**
 * 낙관적 락 실패 예외
 */
public class OptimisticLockExceptionJava extends BusinessExceptionJava {
    
    private final String entityId;
    private final String entityType;
    
    public OptimisticLockExceptionJava(String message, String entityId, String entityType) {
        super(message, "OPTIMISTIC_LOCK_FAILED", createErrorDetails(entityId, entityType));
        this.entityId = entityId;
        this.entityType = entityType;
    }
    
    private static Map<String, Object> createErrorDetails(String entityId, String entityType) {
        Map<String, Object> details = new HashMap<>();
        details.put("entityId", entityId);
        details.put("entityType", entityType);
        return details;
    }
    
    @Override
    public int getHttpStatus() {
        return 409; // Conflict
    }
    
    // Getters
    public String getEntityId() { return entityId; }
    public String getEntityType() { return entityType; }
}