package com.chubb.cdc.debezium.presentation.rest.advice;

import com.chubb.cdc.debezium.application.port.input.CdcEngine;
import com.chubb.cdc.debezium.application.usecase.configuration.LoadConfigurationUseCase;
import com.chubb.cdc.debezium.application.usecase.changecapture.ProcessChangeEventUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for REST API endpoints.
 * <p>
 * Provides standard error response format across all endpoints.
 * All errors include correlation ID for tracing and troubleshooting.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle validation errors (400 Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Validation error [correlationId={}]", correlationId, ex);

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request validation failed")
                .path(extractPath(request))
                .correlationId(correlationId)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions (400 Bad Request).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Invalid argument [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle illegal state exceptions (409 Conflict).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Invalid state [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(ex.getMessage())
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle CDC engine exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(CdcEngine.CdcEngineException.class)
    public ResponseEntity<ErrorResponse> handleCdcEngineException(
            CdcEngine.CdcEngineException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("CDC engine error [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("CDC Engine Error")
                .message(ex.getMessage())
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle configuration load exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(LoadConfigurationUseCase.ConfigurationLoadException.class)
    public ResponseEntity<ErrorResponse> handleConfigurationLoadException(
            LoadConfigurationUseCase.ConfigurationLoadException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Configuration load error [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Configuration Error")
                .message(ex.getMessage())
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle processing exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(ProcessChangeEventUseCase.ProcessingException.class)
    public ResponseEntity<ErrorResponse> handleProcessingException(
            ProcessChangeEventUseCase.ProcessingException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Event processing error [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Processing Error")
                .message(ex.getMessage())
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle 404 Not Found.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.warn("Endpoint not found [correlationId={}]: {}", correlationId, ex.getRequestURL());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message("The requested endpoint does not exist")
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle all other exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {

        String correlationId = generateCorrelationId();
        log.error("Unexpected error [correlationId={}]", correlationId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please contact support with correlation ID: " + correlationId)
                .path(extractPath(request))
                .correlationId(correlationId)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Generate a unique correlation ID for error tracking.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extract the request path from WebRequest.
     */
    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    /**
     * Standard error response DTO matching OpenAPI contract.
     */
    @lombok.Data
    @lombok.Builder
    public static class ErrorResponse {
        private Instant timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private String correlationId;
        private Map<String, String> fieldErrors;
    }
}
