package com.example.welfare.global.exception;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.LogSanitizer;
import com.example.welfare.global.web.ObservabilityAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e,
                                                                    HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        ObservabilityAttributes.setErrorCode(request, errorCode.getCode());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e,
                                                                       HttpServletRequest request) {
        ObservabilityAttributes.setErrorCode(request, ErrorCode.INVALID_INPUT.getCode());
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError != null ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message, ErrorCode.INVALID_INPUT.getCode()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e,
                                                                               HttpServletRequest request) {
        ObservabilityAttributes.setErrorCode(request, ErrorCode.INVALID_INPUT.getCode());
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(message, ErrorCode.INVALID_INPUT.getCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException e,
                                                                     HttpServletRequest request) {
        ObservabilityAttributes.setErrorCode(request, ErrorCode.INVALID_INPUT.getCode());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("입력값이 올바르지 않습니다.", ErrorCode.INVALID_INPUT.getCode()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MissingPathVariableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterExceptions(Exception e,
                                                                             HttpServletRequest request) {
        ObservabilityAttributes.setErrorCode(request, ErrorCode.INVALID_INPUT.getCode());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("입력값이 올바르지 않습니다.", ErrorCode.INVALID_INPUT.getCode()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                                      HttpServletRequest request) {
        ObservabilityAttributes.setErrorCode(request, ErrorCode.INVALID_INPUT.getCode());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("허용되지 않는 HTTP 메서드입니다.", ErrorCode.INVALID_INPUT.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e,
                                                            HttpServletRequest request) {
        log.error("Unhandled exception errorType={} message={}",
                e.getClass().getSimpleName(),
                LogSanitizer.sanitizeSingleLine(e.getMessage(), 300));
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ObservabilityAttributes.setErrorCode(request, errorCode.getCode());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getMessage(), errorCode.getCode()));
    }
}
