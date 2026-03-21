package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.request.RegisterRequest;
import com.munte.KickOffBet.domain.dto.api.response.AuthDto;
import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.dto.api.response.UserListDto;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.mapper.UserMapper;
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.UserService;
import com.munte.KickOffBet.util.PageableValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "email", "lastName", "status"
    );

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping("/create-admin")
    public ResponseEntity<AuthDto> createAdmin(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerAdmin(request));
    }

    @GetMapping
    public ResponseEntity<Page<UserListDto>> getAllUsers(
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                userService.getAllUsers(pageable).map(userMapper::toListDto));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<UserListDto>> getUsersByStatus(
            @PathVariable UserStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                userService.getUsersByStatus(status, pageable).map(userMapper::toListDto));
    }

    @GetMapping("/pending-verification")
    public ResponseEntity<Page<UserListDto>> getPendingVerification(
            @PageableDefault Pageable pageable) {
        // fără PageableValidator — sort-ul e fix în repository, nu vine din frontend
        return ResponseEntity.ok(
                userService.getPendingVerification(pageable).map(userMapper::toListDto));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserListDto>> searchByEmail(
            @RequestParam String email,
            @PageableDefault(sort = "createdAt") Pageable pageable) {
        PageableValidator.validate(pageable, ALLOWED_SORT_FIELDS);
        return ResponseEntity.ok(
                userService.searchUsersByEmail(email, pageable).map(userMapper::toListDto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(userMapper.toDto(userService.getUserById(id)));
    }

    @GetMapping("/{id}/id-card")
    public ResponseEntity<byte[]> getUserIdCard(@PathVariable UUID id) {
        byte[] idCard = userService.getUserCard(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"id-card\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(idCard);
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<Void> approveUser(@PathVariable UUID id) {
        userService.approveUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> rejectUser(@PathVariable UUID id) {
        userService.rejectUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/suspend")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID id) {
        userService.suspendUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable UUID id) {
        userService.activateUser(id);
        return ResponseEntity.noContent().build();
    }
}