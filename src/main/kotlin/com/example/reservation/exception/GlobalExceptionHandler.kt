package com.example.reservation.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.TransactionSystemException
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import jakarta.validation.ConstraintViolationException

/**
 * 글로벌 예외 핸들러
 * 모든 예외를 일관된 형태로 처리하고 적절한 HTTP 응답 생성
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    
    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("비즈니스 예외 발생: {} - {}", ex.errorCode, ex.message, ex)
        
        val errorResponse = ErrorResponse(
            timestamp = ex.timestamp,
            status = ex.getHttpStatus(),
            error = HttpStatus.valueOf(ex.getHttpStatus()).reasonPhrase,
            code = ex.errorCode,
            message = ex.message ?: "알 수 없는 오류가 발생했습니다",
            details = ex.errorDetails,
            path = extractPath(request)
        )
        
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse)
    }
    
    /**
     * Bean Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("입력 검증 실패: {}", ex.message)
        
        val fieldErrors = ex.bindingResult.fieldErrors.associate { 
            it.field to (it.defaultMessage ?: "검증 실패") 
        }
        
        val globalErrors = ex.bindingResult.globalErrors.map { 
            it.defaultMessage ?: "글로벌 검증 실패" 
        }
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 400,
            error = "Bad Request",
            code = "VALIDATION_FAILED",
            message = "입력 값 검증에 실패했습니다",
            details = mapOf(
                "fieldErrors" to fieldErrors,
                "globalErrors" to globalErrors
            ),
            path = extractPath(request)
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    /**
     * Constraint Violation 예외 처리
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(
        ex: ConstraintViolationException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("제약 조건 위반: {}", ex.message)
        
        val violations = ex.constraintViolations.associate { violation ->
            violation.propertyPath.toString() to violation.message
        }
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 400,
            error = "Bad Request",
            code = "CONSTRAINT_VIOLATION",
            message = "제약 조건 위반",
            details = mapOf("violations" to violations),
            path = extractPath(request)
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    /**
     * 데이터 무결성 위반 예외 처리
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolationException(
        ex: DataIntegrityViolationException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.error("데이터 무결성 위반: {}", ex.message, ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 409,
            error = "Conflict",
            code = "DATA_INTEGRITY_VIOLATION",
            message = "데이터 무결성 제약 조건을 위반했습니다",
            details = mapOf("rootCause" to (ex.rootCause?.message ?: "알 수 없음")),
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }
    
    /**
     * 낙관적 락 실패 예외 처리
     */
    @ExceptionHandler(OptimisticLockingFailureException::class, ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailureException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("낙관적 락 실패: {}", ex.message)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 409,
            error = "Conflict",
            code = "OPTIMISTIC_LOCK_FAILED",
            message = "다른 사용자가 동시에 같은 데이터를 수정했습니다. 새로고침 후 다시 시도해주세요.",
            details = emptyMap(),
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }
    
    /**
     * 트랜잭션 시스템 예외 처리
     */
    @ExceptionHandler(TransactionSystemException::class)
    fun handleTransactionSystemException(
        ex: TransactionSystemException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.error("트랜잭션 시스템 오류: {}", ex.message, ex)
        
        // 근본 원인이 validation 예외인지 확인
        val rootCause = ex.rootCause
        if (rootCause is ConstraintViolationException) {
            return handleConstraintViolationException(rootCause, request)
        }
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 500,
            error = "Internal Server Error",
            code = "TRANSACTION_FAILED",
            message = "트랜잭션 처리 중 오류가 발생했습니다",
            details = mapOf("rootCause" to (rootCause?.message ?: "알 수 없음")),
            path = extractPath(request)
        )
        
        return ResponseEntity.internalServerError().body(errorResponse)
    }
    
    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(
        ex: IllegalArgumentException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("잘못된 인수: {}", ex.message)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 400,
            error = "Bad Request",
            code = "ILLEGAL_ARGUMENT",
            message = ex.message ?: "잘못된 인수가 전달되었습니다",
            details = emptyMap(),
            path = extractPath(request)
        )
        
        return ResponseEntity.badRequest().body(errorResponse)
    }
    
    /**
     * IllegalStateException 처리
     */
    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(
        ex: IllegalStateException,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.warn("잘못된 상태: {}", ex.message)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 409,
            error = "Conflict",
            code = "ILLEGAL_STATE",
            message = ex.message ?: "현재 상태에서는 해당 작업을 수행할 수 없습니다",
            details = emptyMap(),
            path = extractPath(request)
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }
    
    /**
     * 일반 예외 처리 (최후의 안전망)
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: WebRequest
    ): ResponseEntity<ErrorResponse> {
        
        logger.error("예상치 못한 오류 발생: {}", ex.message, ex)
        
        val errorResponse = ErrorResponse(
            timestamp = LocalDateTime.now(),
            status = 500,
            error = "Internal Server Error",
            code = "INTERNAL_ERROR",
            message = "서버 내부 오류가 발생했습니다. 관리자에게 문의해주세요.",
            details = mapOf(
                "exceptionType" to ex.javaClass.simpleName,
                "stackTrace" to ex.stackTrace.take(5).map { it.toString() }
            ),
            path = extractPath(request)
        )
        
        return ResponseEntity.internalServerError().body(errorResponse)
    }
    
    /**
     * 요청 경로 추출
     */
    private fun extractPath(request: WebRequest): String {
        return request.getDescription(false).removePrefix("uri=")
    }
}

/**
 * 표준화된 에러 응답 형식
 */
data class ErrorResponse(
    val timestamp: LocalDateTime,
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val details: Map<String, Any>,
    val path: String,
    val traceId: String = generateTraceId()
) {
    companion object {
        private fun generateTraceId(): String {
            return java.util.UUID.randomUUID().toString().substring(0, 8)
        }
    }
}