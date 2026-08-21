package com.ssafy.snaphere.global.error;

import com.ssafy.snaphere.global.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러에서 try-catch 를 쓰지 않게 해주는 곳. 서비스는 throw 만 한다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[BusinessException] {} field={}", code.name(), e.getField());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code, code.getMessage(), e.getField()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        String field = fe != null ? fe.getField() : null;
        String message = fe != null ? fe.getDefaultMessage() : ErrorCode.COMMON_400.getMessage();
        return ResponseEntity.status(ErrorCode.COMMON_400.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_400, message, field));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.status(ErrorCode.COMMON_400.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_400, e.getMessage(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.COMMON_403.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_403, ErrorCode.COMMON_403.getMessage(), null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.COMMON_404.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_404, ErrorCode.COMMON_404.getMessage(), null));
    }

    /**
     * TourAPI 호출 실패. 관리자 수동 트리거에서만 올라온다(사용자 경로에서는 TourAPI 를 호출하지 않는다).
     * 인증 실패와 한도 초과를 구분해줘야 원인 파악이 빠르다.
     */
    @ExceptionHandler(com.ssafy.snaphere.domain.tour.client.TourApiCallException.class)
    public ResponseEntity<ApiResponse<Void>> handleTourApi(
            com.ssafy.snaphere.domain.tour.client.TourApiCallException e) {
        String rc = e.getResultCode();
        ErrorCode code = switch (rc) {
            case "30", "20", "NO_KEY", "AUTH_XML" -> ErrorCode.TOUR_002;
            case "22", "BUDGET_EXHAUSTED"         -> ErrorCode.TOUR_003;
            default                               -> ErrorCode.TOUR_001;
        };
        log.warn("TourAPI 호출 실패 resultCode={} message={}", rc, e.getMessage());
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code, e.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[Unexpected]", e);   // 예상 못한 예외는 반드시 스택트레이스를 남긴다
        return ResponseEntity.status(ErrorCode.COMMON_500.getStatus())
                .body(ApiResponse.fail(ErrorCode.COMMON_500, ErrorCode.COMMON_500.getMessage(), null));
    }
}
