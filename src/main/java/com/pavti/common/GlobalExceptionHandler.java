package com.pavti.common;

import com.pavti.dal.LoggerDal;
import com.pavti.filter.CurrentUser;
import com.pavti.filter.TokenInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One place that turns every error into an ApiResponse. Health 360 does this with
 * try/catch in each controller; here controllers stay short and this class does it once.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LoggerDal loggerDal;

    public GlobalExceptionHandler(LoggerDal loggerDal) {
        this.loggerDal = loggerDal;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ApiResponse.failure(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Some details are missing or wrong.");
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, "The request is not valid JSON."));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiResponse<Object>> handleNotFound(Exception e) {
        return ResponseEntity.status(404).body(ApiResponse.failure(404, "No such API."));
    }

    /** Anything unexpected: hide details from the user, store them in error_logs. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), e);
        StackTraceElement where = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
        CurrentUser user = (CurrentUser) request.getAttribute(TokenInterceptor.CURRENT_USER);
        loggerDal.storeErrorLog(
                where != null ? where.getClassName() : "unknown",
                where != null ? where.getMethodName() : "unknown",
                "Unexpected error on " + request.getRequestURI(),
                e,
                user != null ? user.userId() : null);
        return ResponseEntity.status(500).body(ApiResponse.failure(500, "Something went wrong. Please try again."));
    }
}
