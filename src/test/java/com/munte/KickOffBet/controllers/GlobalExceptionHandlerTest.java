package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.ErrorDto;
import com.munte.KickOffBet.exceptions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleLocked_returns401() {
        ResponseEntity<ErrorDto> response = handler.handleLocked(new LockedException("Account locked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(response.getBody().getError()).contains("locked");
    }

    @Test
    void handleStorage_returns503() {
        ResponseEntity<ErrorDto> response = handler.handleStorage(new StorageException("Storage failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("STORAGE_ERROR");
        assertThat(response.getBody().getError()).contains("Storage failed");
    }

    @Test
    void handleBadCredentials_returns401() {
        ResponseEntity<ErrorDto> response = handler.handleBadCredentials(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(response.getBody().getError()).contains("Invalid credentials");
    }

    @Test
    void handleNotFound_returns404() {
        ResponseEntity<ErrorDto> response = handler.handleNotFound(new ResourceNotFoundException("User not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getError()).contains("User not found");
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<ErrorDto> response = handler.handleConflict(new ConflictException("Email exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("CONFLICT");
        assertThat(response.getBody().getError()).contains("Email exists");
    }

    @Test
    void handleBusiness_returns400() {
        ResponseEntity<ErrorDto> response = handler.handleBusiness(new BusinessException("Insufficient funds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("BUSINESS_ERROR");
        assertThat(response.getBody().getError()).contains("Insufficient funds");
    }

    @Test
    void handleGeneral_returns500() {
        ResponseEntity<ErrorDto> response = handler.handleGeneral(new Exception("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getError()).contains("internal server error");
    }
}
