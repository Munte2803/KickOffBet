package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.dto.api.request.EmailRequest;
import com.munte.KickOffBet.domain.dto.api.request.LoginRequest;
import com.munte.KickOffBet.domain.dto.api.request.RegisterRequest;
import com.munte.KickOffBet.domain.dto.api.request.ResetPasswordRequest;
import com.munte.KickOffBet.domain.dto.api.response.AuthDto;
import com.munte.KickOffBet.domain.dto.api.response.RefreshTokenDto;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.entity.VerificationToken;
import com.munte.KickOffBet.domain.enums.TokenType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ConflictException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.repository.VerificationTokenRepository;
import com.munte.KickOffBet.security.JwtService;
import com.munte.KickOffBet.services.users.AuthService;
import com.munte.KickOffBet.services.users.EmailService;
import com.munte.KickOffBet.services.users.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final VerificationTokenRepository verificationTokenRepository;

    @Value("${minio.bucket.id-cards}")
    private String idCardsBucket;

    @Override
    @Transactional
    public AuthDto register(RegisterRequest request, MultipartFile idCard) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use");
        }

        if (LocalDate.now().minusYears(18).isBefore(request.getBirthDate())) {
            throw new BusinessException("You must be at least 18 years old to register");
        }

        if (idCard == null || idCard.isEmpty()) {
            throw new BusinessException("ID card is required for registration");
        }

        String idCardUrl = storageService.uploadFile(idCard, idCardsBucket);

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setBirthDate(request.getBirthDate());
        user.setBalance(BigDecimal.ZERO);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerified(false);
        user.setIdCardUrl(idCardUrl);
        user.setIdCardVerified(false);

        userRepository.save(user);

        String token= UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(token);
        verificationToken.setType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiresAt(OffsetDateTime.now().plusHours(24));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, token);

        log.info("New user registered: {}", user.getEmail());

        String jwtToken = jwtService.generateToken(user);

        return AuthDto.builder()
                .token(jwtToken)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    @Transactional
    @Override
    public AuthDto registerAdmin(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already in use");
        }

        User admin = new User();
        admin.setFirstName(request.getFirstName());
        admin.setLastName(request.getLastName());
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setBirthDate(request.getBirthDate());
        admin.setBalance(BigDecimal.ZERO);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setEmailVerified(true);
        admin.setIdCardVerified(true);

        userRepository.save(admin);
        log.info("New admin created: {}", admin.getEmail());

        String token = jwtService.generateToken(admin);

        return AuthDto.builder()
                .token(token)
                .email(admin.getEmail())
                .firstName(admin.getFirstName())
                .lastName(admin.getLastName())
                .role(admin.getRole())
                .build();
    }

    @Override
    @Transactional
    public AuthDto login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            throw new BusinessException("Account is temporarily locked. Try again later.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

            if (user.getFailedLoginAttempts() >= 10) {
                user.setLockedUntil(OffsetDateTime.now().plusHours(1));
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
                throw new BusinessException("Account locked for 1 hour due to too many failed attempts.");
            }

            userRepository.save(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return AuthDto.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("No authenticated user found");
        }

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    @Transactional
    public void confirmEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository
                .findByTokenAndType(token, TokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));

        if (verificationToken.isUsed()) {
            throw new BusinessException("Token already used");
        }

        if (verificationToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        if(user.isIdCardVerified()){
            user.setStatus(UserStatus.ACTIVE);
        }
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);

        log.info("Email verified for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendVerificationEmail(EmailRequest email) {
        User user = userRepository.findByEmail(email.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new BusinessException("Email already verified");
        }

        verificationTokenRepository.deleteAllByUserAndType(user, TokenType.EMAIL_VERIFICATION);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(token);
        verificationToken.setType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiresAt(OffsetDateTime.now().plusHours(24));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        emailService.sendVerificationEmail(user, token);
        log.info("Verification email resent to: {}", email);
    }

    @Override
    @Transactional
    public void forgotPassword(EmailRequest email) {

        User user = userRepository.findByEmail(email.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        verificationTokenRepository.deleteAllByUserAndType(user, TokenType.PASSWORD_RESET);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(token);
        verificationToken.setType(TokenType.PASSWORD_RESET);
        verificationToken.setExpiresAt(OffsetDateTime.now().plusHours(1));
        verificationToken.setUsed(false);
        verificationTokenRepository.save(verificationToken);

        emailService.sendPasswordResetEmail(user, token);
        log.info("Password reset email sent to: {}", email);
    }

    @Override
    @Transactional
    public void resetPassword(String token, ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        VerificationToken verificationToken = verificationTokenRepository
                .findByTokenAndType(token, TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessException("Invalid or expired token"));

        if (verificationToken.isUsed()) {
            throw new BusinessException("Token already used");
        }

        if (verificationToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException("Token has expired");
        }

        User user = verificationToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
        emailService.sendPasswordChangedNotification(user);

        log.info("Password reset for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public RefreshTokenDto refreshToken() {
        User user = getCurrentUser();
        
        // Update last activity to track active users
        user.setLastActivity(OffsetDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return RefreshTokenDto.builder()
                .token(token)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .build();
    }

    @Override
    @Transactional
    public void logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
      
            user.setLastActivity(OffsetDateTime.now());
            userRepository.save(user);
            
            SecurityContextHolder.clearContext();
            log.info("User logged out: {}", email);
        }
    }

}