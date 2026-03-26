package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.request.EmailRequest;
import com.munte.KickOffBet.domain.dto.api.request.LoginRequest;
import com.munte.KickOffBet.domain.dto.api.request.RegisterRequest;
import com.munte.KickOffBet.domain.dto.api.request.ResetPasswordRequest;
import com.munte.KickOffBet.domain.dto.api.response.AuthDto;
import com.munte.KickOffBet.services.users.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AuthDto> register(
            @Valid @RequestPart("data") RegisterRequest request,
            @RequestPart(value = "idCard", required = false) MultipartFile idCard) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request, idCard));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Void> confirmEmail(@RequestParam String token) {
        authService.confirmEmail(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest email) {
        authService.resendVerificationEmail(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest email) {
        authService.forgotPassword(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request);
        return ResponseEntity.noContent().build();
    }
}