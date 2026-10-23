package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.ErrorDto;
import com.munte.KickOffBet.exceptions.*;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownContentTypeException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(TicketPlacementException.class)
    public ResponseEntity<ErrorDto> handleTicketPlacement(TicketPlacementException ex) {
        log.warn("Ticket placement failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("TICKET_PLACEMENT_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorDto> handleLocked(LockedException ex) {
        log.warn("Locked account login attempt");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorDto("ACCOUNT_LOCKED", "Account is locked. Please try again later."));
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorDto> handleOptimisticLock(OptimisticLockException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("CONCURRENT_MODIFICATION",
                        "The data was modified concurrently, please try again"));
    }

    // Spring's translation of a version conflict detected at flush/commit (the JPA exception above is
    // only seen when it escapes unwrapped)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> handleObjectOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("CONCURRENT_MODIFICATION",
                        "The data was modified concurrently, please try again"));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorDto> handleStorage(StorageException ex) {
        // The detail names buckets and object keys: log it, never send it to the client
        log.error("Storage error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorDto("STORAGE_ERROR", "File storage is temporarily unavailable"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorDto> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Failed login attempt");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorDto("INVALID_CREDENTIALS", "Invalid credentials"));
    }

    @ExceptionHandler(UnknownContentTypeException.class)
    public ResponseEntity<ErrorDto> handleUnknownContentType(UnknownContentTypeException ex) {
        log.error("API response format error: expected JSON but received {}. Content: {}",
                ex.getContentType(), ex.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorDto("EXTERNAL_API_ERROR", "External service returned an invalid format (possibly HTML error page)."));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorDto> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorDto("INVALID_CREDENTIALS", "Invalid credentials."));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorDto> handleRestClientException(RestClientException ex) {
        log.error("External API communication failed: ", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorDto("EXTERNAL_API_ERROR", "Could not reach football data provider."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorDto("RESOURCE_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ErrorDto> handleExternalApi(ExternalApiException ex) {
        log.error("External API error occurred: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorDto("EXTERNAL_API_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorDto> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorDto("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorDto> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorDto("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorDto> handleConflict(ConflictException ex) {
        log.warn("Conflict detected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto("CONFLICT", ex.getMessage()));
    }

    // The UI sends the user to the wallet only for this code, so it is kept apart from BUSINESS_ERROR
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorDto> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto("INSUFFICIENT_FUNDS", ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorDto> handleBusiness(BusinessException ex) {
        log.warn("Business violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorDto("BUSINESS_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(new ErrorDto("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .orElse("Constraint violation");
        return ResponseEntity.badRequest()
                .body(new ErrorDto("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorDto("BAD_REQUEST", "Malformed request body"));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ErrorDto> handleTypeMismatch(TypeMismatchException ex) {
        log.warn("Request parameter type mismatch: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorDto("BAD_REQUEST", "Invalid value for '" + ex.getPropertyName() + "'"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneral(Exception ex) {
        // Spring MVC client errors (missing parameter, unsupported method/media type, unknown path...)
        // carry their own 4xx status; don't turn them into 500s.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.warn("Client error {}: {}", status.value(), ex.getMessage());
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(new ErrorDto(HttpStatus.valueOf(status.value()).name(),
                            detail != null ? detail : "Invalid request"));
        }
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorDto("INTERNAL_ERROR", "An internal server error occurred."));
    }
}
