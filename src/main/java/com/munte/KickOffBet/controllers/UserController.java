package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.dto.api.response.StoredFile;
import com.munte.KickOffBet.services.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

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
        StoredFile idCard = userService.getMyIdCard();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(idCard.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(resolveMediaType(idCard.contentType()))
                .body(idCard.bytes());
    }

    @PostMapping(value = "/me/id-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadIdCard(@RequestParam("file") MultipartFile file) {
        userService.uploadIdCard(file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deactivateMyAccount() {
        userService.deactivateMyAccount();
        return ResponseEntity.noContent().build();
    }

    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }
}
