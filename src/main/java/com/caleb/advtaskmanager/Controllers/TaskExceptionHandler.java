package com.caleb.advtaskmanager.Controllers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.caleb.advtaskmanager.Dtos.TaskConflict;
import com.caleb.advtaskmanager.Exceptions.InvalidRequestException;
import com.caleb.advtaskmanager.Exceptions.TaskOverlapException;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.ConstraintViolationException;

/** Shared JSON error responses for both user and task endpoints. */
@RestControllerAdvice
public class TaskExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TaskExceptionHandler.class);

    @ExceptionHandler(TaskOverlapException.class)
    public ResponseEntity<Object> handleOverlap(TaskOverlapException exception, WebRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), Map.of(), true,
            exception.getConflictingTasks(), new HttpHeaders(), request);
    }

    // Spring's built-in handler preserves headers such as Allow on a 405 response.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        String message = defaultMessage(status);
        if (exception instanceof InvalidRequestException invalid) {
            fields.putAll(invalid.getFieldErrors());
            message = invalid.getReason();
        } else if (exception instanceof ResponseStatusException reason) {
            if (reason.getReason() != null) message = reason.getReason();
        } else if (exception instanceof MethodArgumentNotValidException invalid) {
            invalid.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
            message = "Please correct the invalid fields";
        } else if (exception instanceof HandlerMethodValidationException invalid && !status.is5xxServerError()) {
            invalid.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                fields.putIfAbsent(result.getMethodParameter().getParameterName(), error.getDefaultMessage())));
            message = "Please correct the invalid parameters";
        } else if (exception instanceof HttpMessageNotReadableException) {
            message = "Invalid request body. Send a JSON object with valid values; dates must use yyyy-MM-dd'T'HH:mm:ss. See /api/tasks/options for allowed task options.";
        } else if (exception instanceof TypeMismatchException mismatch) {
            message = "Invalid request parameter type";
            if (mismatch.getRequiredType() != null && mismatch.getRequiredType().isEnum()) {
                message += ". Allowed values: " + java.util.Arrays.toString(mismatch.getRequiredType().getEnumConstants());
            }
        }
        if (status.is5xxServerError()) {
            LOG.error("Request failed at {}", path(request), exception);
            message = defaultMessage(status);
        }
        return response(status, message, fields, null, null, headers, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
            fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST, "Please correct the invalid fields", fields,
            null, null, new HttpHeaders(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException exception, WebRequest request) {
        LOG.warn("Database constraint rejected request at {}", path(request));
        return response(HttpStatus.CONFLICT,
            "This change conflicts with existing data. Check for a duplicate username or a user who still has tasks.",
            Map.of(), null, null, new HttpHeaders(), request);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Object> handleConcurrentChange(DataAccessException exception, WebRequest request) {
        return response(HttpStatus.CONFLICT, "Another request is changing this data. Refresh and try again.",
            Map.of(), null, null, new HttpHeaders(), request);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessResourceException.class})
    public ResponseEntity<Object> handleDatabase(DataAccessException exception, WebRequest request) {
        LOG.error("Database request failed at {}", path(request), exception);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "The database is temporarily unavailable. Please try again later.",
            Map.of(), null, null, new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        LOG.error("Unexpected error at {}", path(request), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, defaultMessage(HttpStatus.INTERNAL_SERVER_ERROR),
            Map.of(), null, null, new HttpHeaders(), request);
    }

    private ResponseEntity<Object> response(HttpStatusCode status, String message, Map<String, String> fields,
            Boolean confirmationRequired, List<TaskConflict> conflicts, HttpHeaders headers, WebRequest request) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        return new ResponseEntity<>(new ApiError(Instant.now(), status.value(),
            knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase(), message, path(request),
            fields, confirmationRequired, conflicts), headers, status);
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private String defaultMessage(HttpStatusCode status) {
        if (status.is5xxServerError()) return "An unexpected server error occurred. Please try again later.";
        return switch (status.value()) {
            case 404 -> "The requested resource was not found";
            case 405 -> "This HTTP method is not supported for this endpoint";
            case 415 -> "Unsupported content type. Send application/json.";
            default -> "The request could not be processed";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(Instant timestamp, int status, String error, String message, String path,
                           Map<String, String> fieldErrors, Boolean confirmationRequired,
                           List<TaskConflict> conflictingTasks) {}
}
