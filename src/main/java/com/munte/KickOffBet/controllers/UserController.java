package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @GetMapping("/me/id-card")
    public ResponseEntity<byte[]> getMyIdCard() {
        byte[] idCardData = userService.getMyIdCard();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"id-card\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(idCardData);
    }

    @PostMapping("/me/id-card")
    public ResponseEntity<Void> uploadIdCard(@RequestParam("file") MultipartFile file) {
        userService.uploadIdCard(file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount() {
        userService.deactivateMyAccount();
        return ResponseEntity.noContent().build();
    }
}