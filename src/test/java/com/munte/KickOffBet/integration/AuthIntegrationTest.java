package com.munte.KickOffBet.integration;

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
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.StorageService;
import jakarta.persistence.EntityManager;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class AuthIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private Tika tika;

    @MockitoBean(name = "footballDataRestClient")
    private RestClient footballDataRestClient;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private StorageService storageService;

    // ---------------------------------------------------------------
    // 1. register - creates user with PENDING status
    // ---------------------------------------------------------------
    @Test
    void register_createsUserWithPendingStatus() {
        when(storageService.uploadFile(any(), any()))
                .thenReturn("http://minio/id-cards/test.jpg");

        MockMultipartFile idCard = new MockMultipartFile(
                "idCard", "card.jpg", "image/jpeg", "test-content".getBytes());

        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@test.com", "Test1234", LocalDate.of(2000, 1, 1));

        AuthDto result = authService.register(request, idCard);

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getEmail()).isEqualTo("john@test.com");

        entityManager.flush();
        entityManager.clear();

        User saved = userRepository.findByEmail("john@test.com").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    // ---------------------------------------------------------------
    // 2. register - throws when email already exists
    // ---------------------------------------------------------------
    @Test
    void register_throwsWhenEmailExists() {
        createAndSaveUser("existing@test.com", "Test1234", UserStatus.ACTIVE);

        when(storageService.uploadFile(any(), any()))
                .thenReturn("http://minio/id-cards/test.jpg");

        MockMultipartFile idCard = new MockMultipartFile(
                "idCard", "card.jpg", "image/jpeg", "test-content".getBytes());

        RegisterRequest request = new RegisterRequest(
                "Jane", "Doe", "existing@test.com", "Test1234", LocalDate.of(2000, 1, 1));

        assertThatThrownBy(() -> authService.register(request, idCard))
                .isInstanceOf(ConflictException.class);
    }

    // ---------------------------------------------------------------
    // 3. register - throws when user is under 18
    // ---------------------------------------------------------------
    @Test
    void register_throwsWhenUnder18() {
        when(storageService.uploadFile(any(), any()))
                .thenReturn("http://minio/id-cards/test.jpg");

        MockMultipartFile idCard = new MockMultipartFile(
                "idCard", "card.jpg", "image/jpeg", "test-content".getBytes());

        RegisterRequest request = new RegisterRequest(
                "Young", "User", "young@test.com", "Test1234",
                LocalDate.now().minusYears(17));

        assertThatThrownBy(() -> authService.register(request, idCard))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least 18");
    }

    // ---------------------------------------------------------------
    // 4. registerAdmin - creates active admin
    // ---------------------------------------------------------------
    @Test
    void registerAdmin_createsActiveAdmin() {
        RegisterRequest request = new RegisterRequest(
                "Admin", "User", "admin@test.com", "Test1234", LocalDate.of(1990, 5, 15));

        AuthDto result = authService.registerAdmin(request);

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getEmail()).isEqualTo("admin@test.com");
        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN);

        entityManager.flush();
        entityManager.clear();

        User saved = userRepository.findByEmail("admin@test.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.isIdCardVerified()).isTrue();
    }

    // ---------------------------------------------------------------
    // 5. login - success
    // ---------------------------------------------------------------
    @Test
    void login_success() {
        createAndSaveUser("login@test.com", "Test1234", UserStatus.ACTIVE);

        AuthDto result = authService.login(new LoginRequest("login@test.com", "Test1234"));

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getEmail()).isEqualTo("login@test.com");
    }

    // ---------------------------------------------------------------
    // 6. login - locks account after 10 failed attempts
    // ---------------------------------------------------------------
    @Test
    void login_locksAccountAfterFailedAttempts() {
        createAndSaveUser("lockme@test.com", "Test1234", UserStatus.ACTIVE);

        for (int i = 0; i < 9; i++) {
            try {
                authService.login(new LoginRequest("lockme@test.com", "WrongPass1"));
            } catch (BadCredentialsException | BusinessException ignored) {
                // expected
            }
        }

        // The 10th attempt should trigger the lock
        assertThatThrownBy(() ->
                authService.login(new LoginRequest("lockme@test.com", "WrongPass1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked for 1 hour");

        entityManager.flush();
        entityManager.clear();

        User locked = userRepository.findByEmail("lockme@test.com").orElseThrow();
        assertThat(locked.getLockedUntil()).isNotNull();
    }

    // ---------------------------------------------------------------
    // 7. confirmEmail - success
    // ---------------------------------------------------------------
    @Test
    void confirmEmail_success() {
        User user = createAndSaveUser("verify@test.com", "Test1234", UserStatus.PENDING);
        user.setEmailVerified(false);
        userRepository.saveAndFlush(user);

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setToken("test-token-123");
        token.setType(TokenType.EMAIL_VERIFICATION);
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        verificationTokenRepository.saveAndFlush(token);

        authService.confirmEmail("test-token-123");

        entityManager.flush();
        entityManager.clear();

        User updated = userRepository.findByEmail("verify@test.com").orElseThrow();
        assertThat(updated.isEmailVerified()).isTrue();

        VerificationToken usedToken = verificationTokenRepository
                .findByTokenAndType("test-token-123", TokenType.EMAIL_VERIFICATION)
                .orElseThrow();
        assertThat(usedToken.isUsed()).isTrue();
    }

    // ---------------------------------------------------------------
    // 8. resetPassword - changes password
    // ---------------------------------------------------------------
    @Test
    void resetPassword_changesPassword() {
        User user = createAndSaveUser("reset@test.com", "OldPass1", UserStatus.ACTIVE);

        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setToken("reset-token");
        token.setType(TokenType.PASSWORD_RESET);
        token.setUsed(false);
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        verificationTokenRepository.saveAndFlush(token);

        ResetPasswordRequest request = new ResetPasswordRequest(
                "reset-token", "OldPass1", "NewPass1", "NewPass1");

        authService.resetPassword("reset-token", request);

        entityManager.flush();
        entityManager.clear();

        User updated = userRepository.findByEmail("reset@test.com").orElseThrow();
        assertThat(passwordEncoder.matches("NewPass1", updated.getPassword())).isTrue();
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------
    private User createAndSaveUser(String email, String rawPassword, UserStatus status) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setBalance(BigDecimal.ZERO);
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setRole(UserRole.USER);
        user.setStatus(status);
        user.setEmailVerified(status == UserStatus.ACTIVE);
        user.setIdCardVerified(status == UserStatus.ACTIVE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }
}
