package com.untitles.global.exception;

import com.untitles.global.dto.ApiResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockException(Exception e) {
        log.warn("Optimistic lock conflict: {}", e.getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, "다른 곳에서 수정되었습니다.");
    }

    /**
     * 같은 값이 동시에 저장되어 유일성 제약에 걸린 경우 (예: 같은 아이디로 동시 가입) - 409
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, "이미 사용 중이거나 동시에 처리된 요청입니다. 다시 시도해주세요.");
    }

    /**
     * 트랜잭션 예외 처리
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleTransactionException(TransactionSystemException e) {
        Throwable cause = e.getRootCause();

        if (cause instanceof OptimisticLockException ||
                cause instanceof StaleObjectStateException) {
            log.warn("Optimistic lock conflict: {}", cause.getMessage());
            return createErrorResponse(HttpStatus.CONFLICT, "다른 곳에서 수정되었습니다.");
        }

        log.error("Transaction error: ", e);
        io.sentry.Sentry.captureException(e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * 소셜 로그인 이메일이 기존 계정과 겹침 (409): 이메일 로그인 화면으로 넘길 수 있도록 연결 정보를 함께 보낸다
     */
    @ExceptionHandler(com.untitles.domain.auth.social.SocialLinkRequiredException.class)
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> handleSocialLinkRequired(
            com.untitles.domain.auth.social.SocialLinkRequiredException e) {
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("linkToken", e.getLinkToken());
        data.put("email", e.getEmail());
        data.put("provider", e.getProvider().name());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>("error", HttpStatus.CONFLICT.value(), e.getErrorCode().getMessage(), data));
    }

    /**
     * BusinessException 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {}", errorCode.getMessage());
        return createErrorResponse(errorCode.getStatus(), errorCode.getMessage());
    }

    /**
     * IllegalArgumentException 처리 - 서버 오류로 간주
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException: ", e);
        io.sentry.Sentry.captureException(e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * IllegalStateException 처리 - 서버 오류로 간주
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e) {
        log.error("IllegalStateException: ", e);
        io.sentry.Sentry.captureException(e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * UsernameNotFoundException 처리 (로그인 실패)
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNotFoundException(UsernameNotFoundException e) {
        log.warn("UsernameNotFoundException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * BadCredentialsException 처리 (비밀번호 불일치)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(BadCredentialsException e) {
        log.warn("BadCredentialsException: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    /**
     * Validation 에러 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");

        log.warn("Validation error: {}", message);
        return createErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 존재하지 않는 경로 (404)
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception e) {
        return createErrorResponse(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다.");
    }

    /**
     * 업로드 크기 제한(spring.servlet.multipart) 초과 (400)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ErrorCode.IMAGE_TOO_LARGE.getMessage());
    }

    /**
     * 지원하지 않는 HTTP 메서드 (405)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return createErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다.");
    }

    /**
     * 지원하지 않는 Content-Type (415)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return createErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다.");
    }

    /**
     * 잘못된 요청 형식: 깨진 JSON, 파라미터 누락, 타입 불일치 (400)
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        log.warn("Bad request: {}", e.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
    }

    /**
     * 그 외 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        // 여기서 처리하면 Sentry가 자동으로 못 보므로 직접 보낸다 (SENTRY_DSN이 없으면 아무것도 안 함)
        io.sentry.Sentry.captureException(e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    /**
     * 에러 응답 생성
     */
    private ResponseEntity<ApiResponse<Void>> createErrorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(status.value(), message));
    }
}