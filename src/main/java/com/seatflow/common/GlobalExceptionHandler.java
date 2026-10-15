package com.seatflow.common;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into the same {@link ApiResponse} envelope used by
 * successful calls, so clients only ever parse one shape.
 *
 * <p>中文批注：全局异常兜底。把业务异常、参数校验失败、未知异常统一转换成 ApiResponse，
 * 让业务代码只管抛出、不必到处 try-catch，同时也避免把异常堆栈直接泄露给调用方。
 * 最后那个 {@code @ExceptionHandler(Exception.class)} 是最后一道防线；
 * 它兜得住所有异常，也容易把该暴露的问题藏起来，所以 error 级别一定要留下堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("business error: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.failure(ex.getStatus(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("validation error: {}", detail);
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("unexpected error", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure(500, "internal error"));
    }
}
