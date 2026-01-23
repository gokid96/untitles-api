package com.untitles.global.exception;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 낙관적 락 충돌 (409 Conflict)
     */
    @ExceptionHandler({
            OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class,
            StaleObjectStateException.class
    })
    public ResponseEntity<Map<String, Object>> handleOptimisticLockException(Exception e) {
        log.warn("Optimistic lock conflict: {}", e.getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, "다른 곳에서 수정되었습니다.");
    }

    /**
     * 트랜잭션 예외 처리
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionException(TransactionSystemException e) {
        Throwable cause = e.getRootCause();

        if (cause instanceof OptimisticLockException ||
                cause instanceof StaleObjectStateException) {
            log.warn("Optimistic lock conflict: {}", cause.getMessage());
            return createErrorResponse(HttpStatus.CONFLICT, "다른 곳에서 수정되었습니다.");
        }

        log.error("Transaction error: ", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * IllegalArgumentException 처리 (비즈니스 로직 에러)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * IllegalStateException 처리
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        log.warn("IllegalStateException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * UsernameNotFoundException 처리 (로그인 실패)
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameNotFoundException(UsernameNotFoundException e) {
        log.warn("UsernameNotFoundException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * BadCredentialsException 처리 (비밀번호 불일치)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("BadCredentialsException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * Validation 에러 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");

        log.warn("Validation error: {}", message);
        return createErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * 에러 응답 생성
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", status.value());
        response.put("message", message);
        return ResponseEntity.status(status).body(response);
    }
}
