package com.photography.backend.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "VALIDATION_ERROR");
        body.put("message", "Validation failed");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        body.put("fieldErrors", errors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Malformed request bodies — bad JSON, unknown enum value, unparseable
     * date — are client errors. Without this they fell through to the
     * catch-all and surfaced as an unexplained 500.
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadableBody(
            org.springframework.http.converter.HttpMessageNotReadableException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "MALFORMED_REQUEST");
        body.put("message", "Request body is malformed or contains invalid values");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusinessException(BusinessException ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", ex.getStatus().value());
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, ex.getStatus());
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        // Log the real cause server-side (never returned to the client) so the
        // status here can be traced to the exact constraint / bad value.
        Throwable root = ex.getMostSpecificCause();
        String sqlState = root instanceof SQLException sql ? sql.getSQLState() : null;
        log.warn("Data integrity violation on {} [sqlState={}]: {}",
                request.getDescription(false), sqlState, root.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        // SQLState class 22 = data exception (malformed value, value too long…):
        // the request itself is invalid, not in conflict with existing data.
        if (sqlState != null && sqlState.startsWith("22")) {
            body.put("status", HttpStatus.BAD_REQUEST.value());
            body.put("error", "INVALID_DATA");
            body.put("message", "Dữ liệu gửi lên không hợp lệ hoặc quá dài");
            return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
        }
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "CONFLICT");
        body.put("message", "The request conflicts with existing data");
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler({org.springframework.web.multipart.MaxUploadSizeExceededException.class,
            org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<?> handleMultipartException(Exception ex, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", "PAYLOAD_TOO_LARGE");
        body.put("message", ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException
                ? "Uploaded file exceeds the maximum allowed size"
                : "Malformed multipart request: " + ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex, WebRequest request) {
        String errorId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [{}] on {}", errorId, request.getDescription(false), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "INTERNAL_SERVER_ERROR");
        body.put("errorId", errorId);
        body.put("message", "An unexpected error occurred");
        body.put("path", request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
