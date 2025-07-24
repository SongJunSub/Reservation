package com.example.reservation.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 인증 실패 진입점 (Java WebFlux)
 * 
 * 기능:
 * 1. 인증되지 않은 요청에 대한 응답 처리
 * 2. 일관된 에러 응답 형식 제공
 * 3. 보안 로깅 및 모니터링
 * 4. 리액티브 스트림 기반 응답 생성
 * 
 * Java 특징:
 * - 전통적인 클래스 정의와 명시적 타입
 * - if-else 조건문 vs Kotlin when 표현식
 * - Builder 패턴 vs data class
 * - Stream API를 통한 헤더 처리
 */
@Component
public class JwtAuthenticationEntryPointJava implements ServerAuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationEntryPointJava.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPointJava(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 인증 실패시 호출되는 메서드
     * Java의 명시적 타입과 전통적인 예외 처리
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        var request = exchange.getRequest();
        var response = exchange.getResponse();

        // 보안 로깅 - Java의 명시적 문자열 포매팅
        logger.warn(
                "인증되지 않은 접근 시도 - IP: {}, URI: {}, User-Agent: {}, 오류: {}",
                getClientIpAddress(exchange),
                request.getURI(),
                request.getHeaders().getFirst("User-Agent"),
                ex.getMessage()
        );

        // 응답 헤더 설정 - Java의 체이닝 방식
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        response.getHeaders().add("Cache-Control", "no-cache, no-store, must-revalidate");
        response.getHeaders().add("Pragma", "no-cache");
        response.getHeaders().add("Expires", "0");

        // 에러 응답 생성
        ErrorResponseJava errorResponse = createErrorResponse(request.getURI().toString(), ex);
        
        return response.writeWith(
                Mono.fromCallable(() -> {
                    try {
                        byte[] jsonBytes = objectMapper.writeValueAsBytes(errorResponse);
                        return response.bufferFactory().wrap(jsonBytes);
                    } catch (Exception e) {
                        throw new RuntimeException("JSON 직렬화 실패", e);
                    }
                })
        ).doOnError(error -> 
                logger.error("인증 에러 응답 생성 중 오류 발생", error)
        );
    }

    /**
     * 에러 응답 객체 생성
     * Java의 if-else 조건문과 명시적 객체 생성
     */
    private ErrorResponseJava createErrorResponse(String path, AuthenticationException ex) {
        String errorCode;
        String errorMessage;

        // Java의 전통적인 조건문 vs Kotlin when
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired")) {
            errorCode = "TOKEN_EXPIRED";
            errorMessage = "인증 토큰이 만료되었습니다. 다시 로그인해 주세요.";
        } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("invalid")) {
            errorCode = "INVALID_TOKEN";
            errorMessage = "유효하지 않은 인증 토큰입니다.";
        } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("malformed")) {
            errorCode = "MALFORMED_TOKEN";
            errorMessage = "잘못된 형식의 인증 토큰입니다.";
        } else {
            errorCode = "AUTHENTICATION_REQUIRED";
            errorMessage = "인증이 필요합니다. 로그인 후 다시 시도해 주세요.";
        }

        return new ErrorResponseJava(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                errorCode,
                errorMessage,
                path,
                getSuggestion(errorCode),
                null
        );
    }

    /**
     * 에러 코드별 해결 방안 제안
     * Java의 switch문 vs Kotlin when 표현식
     */
    private String getSuggestion(String errorCode) {
        switch (errorCode) {
            case "TOKEN_EXPIRED":
                return "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받거나, 다시 로그인해 주세요.";
            case "INVALID_TOKEN":
            case "MALFORMED_TOKEN":
                return "올바른 Bearer 토큰을 Authorization 헤더에 포함하여 요청해 주세요.";
            default:
                return "POST /api/auth/login 엔드포인트를 통해 로그인한 후, 발급받은 토큰을 사용해 주세요.";
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     * Java의 명시적 null 체크와 조건문
     */
    private String getClientIpAddress(ServerWebExchange exchange) {
        var request = exchange.getRequest();
        
        // 순서대로 헤더 확인
        String[] headerNames = {"X-Forwarded-For", "X-Real-IP", "X-Forwarded", "X-Cluster-Client-IP"};
        
        for (String headerName : headerNames) {
            String headerValue = request.getHeaders().getFirst(headerName);
            if (headerValue != null && !headerValue.trim().isEmpty() && 
                !"unknown".equalsIgnoreCase(headerValue.trim())) {
                
                // 첫 번째 IP 주소 추출 (콤마로 구분된 경우)
                String[] ips = headerValue.split(",");
                if (ips.length > 0) {
                    return ips[0].trim();
                }
            }
        }
        
        // 헤더에서 찾지 못한 경우 RemoteAddress 사용
        if (request.getRemoteAddress() != null && 
            request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
}

/**
 * 에러 응답 클래스 (Java)
 * 
 * Java 특징:
 * - 전통적인 클래스 정의 방식
 * - 명시적 생성자와 getter 메서드
 * - Builder 패턴 활용 가능
 * - final 필드를 통한 불변성
 */
class ErrorResponseJava {
    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final String suggestion;
    private final Map<String, Object> details;

    public ErrorResponseJava(LocalDateTime timestamp, int status, String error, String code,
                            String message, String path, String suggestion, Map<String, Object> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.path = path;
        this.suggestion = suggestion;
        this.details = details;
    }

    /**
     * 추가 컨텍스트 정보 포함을 위한 편의 메서드
     * Java의 Builder 패턴 스타일
     */
    public ErrorResponseJava withDetails(Map<String, Object> additionalDetails) {
        Map<String, Object> newDetails = new HashMap<>();
        if (this.details != null) {
            newDetails.putAll(this.details);
        }
        if (additionalDetails != null) {
            newDetails.putAll(additionalDetails);
        }
        
        return new ErrorResponseJava(
                this.timestamp, this.status, this.error, this.code,
                this.message, this.path, this.suggestion, newDetails
        );
    }

    /**
     * 디버그 정보 포함 여부 (프로덕션에서는 제외)
     */
    public ErrorResponseJava withDebugInfo(Map<String, Object> debugInfo, boolean includeDebug) {
        if (includeDebug) {
            Map<String, Object> debugDetails = new HashMap<>();
            debugDetails.put("debug", debugInfo);
            return withDetails(debugDetails);
        } else {
            return this;
        }
    }

    // Getter 메서드들
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public String getSuggestion() { return suggestion; }
    public Map<String, Object> getDetails() { return details; }

    @Override
    public String toString() {
        return "ErrorResponseJava{" +
                "timestamp=" + timestamp +
                ", status=" + status +
                ", error='" + error + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", path='" + path + '\'' +
                ", suggestion='" + suggestion + '\'' +
                ", details=" + details +
                '}';
    }
}