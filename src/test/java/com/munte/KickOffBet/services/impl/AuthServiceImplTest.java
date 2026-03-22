package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.request.EmailRequest;
import com.munte.KickOffBet.domain.dto.api.request.LoginRequest;
import com.munte.KickOffBet.domain.dto.api.request.RegisterRequest;
import com.munte.KickOffBet.domain.dto.api.request.ResetPasswordRequest;
import com.munte.KickOffBet.domain.dto.api.response.AuthDto;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.entity.VerificationToken;
import com.munte.KickOffBet.domain.enums.TokenType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ConflictException;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.repository.VerificationTokenRepository;
import com.munte.KickOffBet.security.JwtService;
import com.munte.KickOffBet.services.EmailService;
import com.munte.KickOffBet.services.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "idCardsBucket", "id-cards");
    }

    // ==================== register ====================

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Test1234", LocalDate.of(2000, 1, 1)
        );
        MultipartFile idCard = mock(MultipartFile.class);
        when(idCard.isEmpty()).thenReturn(false);

        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(storageService.uploadFile(idCard, "id-cards")).thenReturn("http://minio/id-cards/uuid-file.jpg");
        when(passwordEncoder.encode("Test1234")).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthDto result = authService.register(request, idCard);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(savedUser.isEmailVerified()).isFalse();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
        assertThat(savedUser.getIdCardUrl()).isEqualTo("http://minio/id-cards/uuid-file.jpg");
        assertThat(savedUser.getBalance()).isEqualTo(BigDecimal.ZERO);

        verify(verificationTokenRepository).save(any(VerificationToken.class));
        verify(emailService).sendVerificationEmail(eq(savedUser), anyString());

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getEmail()).isEqualTo("john@test.com");
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Doe");
        assertThat(result.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void register_emailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Test1234", LocalDate.of(2000, 1, 1)
        );
        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, mock(MultipartFile.class)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void register_under18() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Test1234", LocalDate.now().minusYears(17)
        );
        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request, mock(MultipartFile.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 18");
    }

    @Test
    void register_nullIdCard() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Test1234", LocalDate.of(2000, 1, 1)
        );
        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID card is required");
    }

    // ==================== registerAdmin ====================

    @Test
    void registerAdmin_success() {
        RegisterRequest request = new RegisterRequest(
                "Admin", "User", "admin@test.com", "Admin1234", LocalDate.of(1990, 1, 1)
        );

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Admin1234")).thenReturn("encodedPassword");
        when(jwtService.generateToken(any(User.class))).thenReturn("admin-jwt-token");

        AuthDto result = authService.registerAdmin(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedAdmin = userCaptor.getValue();

        assertThat(savedAdmin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedAdmin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedAdmin.isEmailVerified()).isTrue();
        assertThat(savedAdmin.isIdCardVerified()).isTrue();

        assertThat(result.getToken()).isEqualTo("admin-jwt-token");
        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);
    }

    // ==================== login ====================

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("test@test.com", "password");
        User user = createUser();
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthDto result = authService.login(request);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void login_accountLocked() {
        LoginRequest request = new LoginRequest("test@test.com", "password");
        User user = createUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("temporarily locked");
    }

    @Test
    void login_badCredentials_incrementsAttempts() {
        LoginRequest request = new LoginRequest("test@test.com", "wrong");
        User user = createUser();
        user.setFailedLoginAttempts(0);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository).save(user);
    }

    @Test
    void login_badCredentials_locksAfter10Attempts() {
        LoginRequest request = new LoginRequest("test@test.com", "wrong");
        User user = createUser();
        user.setFailedLoginAttempts(9);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked for 1 hour");

        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
        verify(userRepository).save(user);
    }

    // ==================== confirmEmail ====================

    @Test
    void confirmEmail_success() {
        User user = createUser();
        VerificationToken token = createToken(user, TokenType.EMAIL_VERIFICATION, false, false);

        when(verificationTokenRepository.findByTokenAndType("test-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        authService.confirmEmail("test-token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void confirmEmail_tokenAlreadyUsed() {
        User user = createUser();
        VerificationToken token = createToken(user, TokenType.EMAIL_VERIFICATION, true, false);

        when(verificationTokenRepository.findByTokenAndType("test-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.confirmEmail("test-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void confirmEmail_tokenExpired() {
        User user = createUser();
        VerificationToken token = createToken(user, TokenType.EMAIL_VERIFICATION, false, true);

        when(verificationTokenRepository.findByTokenAndType("test-token", TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.confirmEmail("test-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    // ==================== forgotPassword ====================

    @Test
    void forgotPassword_success() {
        User user = createUser();
        EmailRequest emailRequest = new EmailRequest("test@test.com");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(emailRequest);

        verify(verificationTokenRepository).deleteAllByUserAndType(user, TokenType.PASSWORD_RESET);

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(verificationTokenRepository).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getType()).isEqualTo(TokenType.PASSWORD_RESET);
        assertThat(savedToken.isUsed()).isFalse();
        assertThat(savedToken.getUser()).isEqualTo(user);

        verify(emailService).sendPasswordResetEmail(eq(user), anyString());
    }

    // ==================== resetPassword ====================

    @Test
    void resetPassword_success() {
        User user = createUser();
        VerificationToken token = createToken(user, TokenType.PASSWORD_RESET, false, false);
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass1");
        request.setConfirmPassword("NewPass1");

        when(verificationTokenRepository.findByTokenAndType("test-token", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPass1")).thenReturn("encodedNewPass");

        authService.resetPassword("test-token", request);

        assertThat(user.getPassword()).isEqualTo("encodedNewPass");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(verificationTokenRepository).save(token);
    }

    @Test
    void resetPassword_passwordsDontMatch() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("NewPass1");
        request.setConfirmPassword("DifferentPass1");

        assertThatThrownBy(() -> authService.resetPassword("test-token", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("do not match");
    }

    // ==================== helpers ====================

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setBalance(BigDecimal.ZERO);
        return user;
    }

    private VerificationToken createToken(User user, TokenType type, boolean used, boolean expired) {
        VerificationToken t = new VerificationToken();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setToken("test-token");
        t.setType(type);
        t.setUsed(used);
        t.setExpiresAt(expired ? LocalDateTime.now().minusHours(1) : LocalDateTime.now().plusHours(1));
        return t;
    }
}
