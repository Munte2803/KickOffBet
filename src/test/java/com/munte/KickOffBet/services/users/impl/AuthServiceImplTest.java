package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.dto.api.request.EmailRequest;
import com.munte.KickOffBet.domain.dto.api.request.LoginRequest;
import com.munte.KickOffBet.domain.dto.api.request.RefreshTokenRequest;
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
import com.munte.KickOffBet.services.users.EmailService;
import com.munte.KickOffBet.services.users.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "idCardsBucket", "id-cards");
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("player@kickoffbet.test");
        user.setFirstName("Ion");
        user.setLastName("Popescu");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPassword("{hashed}");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jwtService.generateToken(any())).thenReturn("access-token");
        lenient().when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static RegisterRequest registerRequest(LocalDate birthDate) {
        return new RegisterRequest("Maria", "Ionescu", "maria@kickoffbet.test", "Secret123", birthDate);
    }

    private static MockMultipartFile idCard() {
        return new MockMultipartFile("idCard", "id.png", "image/png", new byte[]{1, 2, 3});
    }

    private VerificationToken token(TokenType type, LocalDateTime expiresAt, boolean used) {
        VerificationToken t = new VerificationToken();
        t.setId(UUID.randomUUID());
        t.setUser(user);
        t.setToken(UUID.randomUUID().toString());
        t.setType(type);
        t.setExpiresAt(expiresAt);
        t.setUsed(used);
        return t;
    }

    private static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    @Nested
    class Register {

        @Test
        void newPlayerIsCreatedPendingWithZeroBalanceAndReceivesVerificationEmail() {
            RegisterRequest request = registerRequest(LocalDate.now().minusYears(25));
            when(userRepository.existsByEmail("maria@kickoffbet.test")).thenReturn(false);
            when(storageService.uploadFile(any(), any())).thenReturn("http://minio/id-cards/abc_id.png");
            when(passwordEncoder.encode("Secret123")).thenReturn("{bcrypt}hash");

            AuthDto dto = authService.register(request, idCard());

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(saved.getRole()).isEqualTo(UserRole.USER);
            assertThat(saved.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.isEmailVerified()).isFalse();
            assertThat(saved.isIdCardVerified()).isFalse();
            assertThat(saved.getPassword()).isEqualTo("{bcrypt}hash");
            assertThat(saved.getIdCardUrl()).isEqualTo("http://minio/id-cards/abc_id.png");
            verify(storageService).uploadFile(any(), any());

            ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(tokenCaptor.capture());
            VerificationToken vt = tokenCaptor.getValue();
            assertThat(vt.getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
            assertThat(vt.isUsed()).isFalse();
            assertThat(vt.getUser()).isSameAs(saved);
            assertThat(vt.getExpiresAt()).isCloseTo(nowUtc().plusHours(24), within(Duration.ofSeconds(10)));
            verify(emailService).sendVerificationEmail(saved, vt.getToken());

            assertThat(dto.getToken()).isEqualTo("access-token");
            assertThat(dto.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(dto.getEmail()).isEqualTo("maria@kickoffbet.test");
            assertThat(dto.getRole()).isEqualTo(UserRole.USER);
        }

        @Test
        void idCardIsUploadedToTheIdCardsBucket() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(storageService.uploadFile(any(), any())).thenReturn("url");

            MockMultipartFile file = idCard();
            authService.register(registerRequest(LocalDate.now().minusYears(30)), file);

            verify(storageService).uploadFile(file, "id-cards");
        }

        @Test
        void duplicateEmailIsRejectedBeforeAnythingIsStored() {
            when(userRepository.existsByEmail("maria@kickoffbet.test")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest(LocalDate.now().minusYears(25)), idCard()))
                    .isInstanceOf(ConflictException.class);
            verifyNoInteractions(storageService, emailService, verificationTokenRepository);
            verify(userRepository, never()).save(any());
        }

        @Test
        void minorsCannotRegister() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            LocalDate oneDayShortOf18 = LocalDate.now().minusYears(18).plusDays(1);

            assertThatThrownBy(() -> authService.register(registerRequest(oneDayShortOf18), idCard()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("18");
            verifyNoInteractions(storageService);
        }

        @Test
        void playerTurning18TodayCanRegister() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(storageService.uploadFile(any(), any())).thenReturn("url");

            AuthDto dto = authService.register(registerRequest(LocalDate.now().minusYears(18)), idCard());

            assertThat(dto.getEmail()).isEqualTo("maria@kickoffbet.test");
        }

        @Test
        void idCardIsMandatory() {
            when(userRepository.existsByEmail(any())).thenReturn(false);

            assertThatThrownBy(() -> authService.register(registerRequest(LocalDate.now().minusYears(25)), null))
                    .isInstanceOf(BusinessException.class);
            MockMultipartFile empty = new MockMultipartFile("idCard", "id.png", "image/png", new byte[0]);
            assertThatThrownBy(() -> authService.register(registerRequest(LocalDate.now().minusYears(25)), empty))
                    .isInstanceOf(BusinessException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void adminIsCreatedActiveAndVerified() {
            when(userRepository.existsByEmail(any())).thenReturn(false);

            authService.registerAdmin(registerRequest(LocalDate.now().minusYears(40)));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(captor.getValue().isEmailVerified()).isTrue();
            assertThat(captor.getValue().isIdCardVerified()).isTrue();
        }
    }

    @Nested
    class EmailConfirmation {

        @Test
        void confirmingEmailWithPendingKycKeepsAccountPending() {
            user.setStatus(UserStatus.PENDING);
            user.setIdCardVerified(false);
            VerificationToken vt = token(TokenType.EMAIL_VERIFICATION, nowUtc().plusHours(1), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.EMAIL_VERIFICATION))
                    .thenReturn(Optional.of(vt));

            authService.confirmEmail(vt.getToken());

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(vt.isUsed()).isTrue();
            verify(verificationTokenRepository).save(vt);
        }

        @Test
        void confirmingEmailAfterKycApprovalActivatesAccount() {
            user.setStatus(UserStatus.PENDING);
            user.setIdCardVerified(true);
            VerificationToken vt = token(TokenType.EMAIL_VERIFICATION, nowUtc().plusHours(1), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.EMAIL_VERIFICATION))
                    .thenReturn(Optional.of(vt));

            authService.confirmEmail(vt.getToken());

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void unknownTokenIsRejected() {
            when(verificationTokenRepository.findByTokenAndType("nope", TokenType.EMAIL_VERIFICATION))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.confirmEmail("nope"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void usedTokenCannotBeReused() {
            user.setEmailVerified(false);
            VerificationToken vt = token(TokenType.EMAIL_VERIFICATION, nowUtc().plusHours(1), true);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.EMAIL_VERIFICATION))
                    .thenReturn(Optional.of(vt));

            assertThatThrownBy(() -> authService.confirmEmail(vt.getToken()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already used");
            assertThat(user.isEmailVerified()).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        void expiredTokenIsRejected() {
            user.setEmailVerified(false);
            VerificationToken vt = token(TokenType.EMAIL_VERIFICATION, nowUtc().minusMinutes(1), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.EMAIL_VERIFICATION))
                    .thenReturn(Optional.of(vt));

            assertThatThrownBy(() -> authService.confirmEmail(vt.getToken()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("expired");
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(vt.isUsed()).isFalse();
        }

        @Test
        void resendingVerificationInvalidatesOldTokensAndIssuesNewOne() {
            user.setEmailVerified(false);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            authService.resendVerificationEmail(new EmailRequest(user.getEmail()));

            verify(verificationTokenRepository).deleteAllByUserAndType(user, TokenType.EMAIL_VERIFICATION);
            ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(captor.capture());
            assertThat(captor.getValue().getType()).isEqualTo(TokenType.EMAIL_VERIFICATION);
            assertThat(captor.getValue().getExpiresAt()).isCloseTo(nowUtc().plusHours(24), within(Duration.ofSeconds(10)));
            verify(emailService).sendVerificationEmail(user, captor.getValue().getToken());
        }

        @Test
        void resendingForAlreadyVerifiedEmailFails() {
            user.setEmailVerified(true);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.resendVerificationEmail(new EmailRequest(user.getEmail())))
                    .isInstanceOf(BusinessException.class);
            verifyNoInteractions(emailService);
        }
    }

    @Nested
    class Login {

        private final LoginRequest request = new LoginRequest("player@kickoffbet.test", "Secret123");

        @Test
        void successfulLoginReturnsTokensAndResetsFailedAttempts() {
            user.setFailedLoginAttempts(4);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            AuthDto dto = authService.login(request);

            assertThat(dto.getToken()).isEqualTo("access-token");
            assertThat(dto.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(dto.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(captor.capture());
            assertThat(captor.getValue().getPrincipal()).isEqualTo("player@kickoffbet.test");
            assertThat(captor.getValue().getCredentials()).isEqualTo("Secret123");
        }

        @Test
        void wrongPasswordCountsAFailedAttempt() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(user.getLockedUntil()).isNull();
            verify(userRepository).save(user);
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        void tenthConsecutiveFailureLocksTheAccountForOneHour() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            for (int i = 1; i <= 9; i++) {
                assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
                assertThat(user.getLockedUntil()).isNull();
            }
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("locked");

            assertThat(user.getLockedUntil()).isCloseTo(nowUtc().plusHours(1), within(Duration.ofSeconds(10)));
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        void lockedAccountIsRejectedEvenWithTheCorrectPassword() {
            user.setLockedUntil(nowUtc().plusMinutes(30));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("locked");
            verifyNoInteractions(authenticationManager);
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        void loginWorksAgainAfterTheLockExpiresAndClearsTheLock() {
            user.setLockedUntil(nowUtc().minusMinutes(1));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            AuthDto dto = authService.login(request);

            assertThat(dto.getToken()).isEqualTo("access-token");
            assertThat(user.getLockedUntil()).isNull();
        }

        @Test
        void successfulLoginAfterSomeFailuresResetsTheCounter() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"))
                    .thenThrow(new BadCredentialsException("bad"))
                    .thenReturn(null);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
            assertThat(user.getFailedLoginAttempts()).isEqualTo(2);

            authService.login(request);

            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        void unknownEmailFails() {
            when(userRepository.findByEmail("ghost@kickoffbet.test")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@kickoffbet.test", "x")))
                    .isInstanceOf(ResourceNotFoundException.class);
            verifyNoInteractions(authenticationManager);
        }
    }

    @Nested
    class Refresh {

        private RefreshTokenRequest request(String token) {
            RefreshTokenRequest r = new RefreshTokenRequest();
            r.setRefreshToken(token);
            return r;
        }

        @Test
        void validRefreshTokenIssuesNewTokenPair() {
            when(jwtService.extractUsername("old-refresh")).thenReturn(user.getEmail());
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(jwtService.isRefreshTokenValid("old-refresh", user)).thenReturn(true);

            RefreshTokenDto dto = authService.refreshToken(request("old-refresh"));

            assertThat(dto.getToken()).isEqualTo("access-token");
            assertThat(dto.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(dto.getEmail()).isEqualTo(user.getEmail());
            assertThat(user.getLastActivity()).isCloseTo(nowUtc(), within(Duration.ofSeconds(10)));
        }

        @Test
        void invalidRefreshTokenIsRejected() {
            when(jwtService.extractUsername("bad-refresh")).thenReturn(user.getEmail());
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(jwtService.isRefreshTokenValid("bad-refresh", user)).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request("bad-refresh")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid refresh token");
            verify(jwtService, never()).generateToken(any());
            verify(jwtService, never()).generateRefreshToken(any());
        }

        @Test
        void refreshTokenOfDeletedUserIsRejected() {
            when(jwtService.extractUsername("orphan")).thenReturn("gone@kickoffbet.test");
            when(userRepository.findByEmail("gone@kickoffbet.test")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken(request("orphan")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class PasswordReset {

        @Test
        void forgotPasswordReplacesOldResetTokensAndSendsOneHourLink() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            authService.forgotPassword(new EmailRequest(user.getEmail()));

            verify(verificationTokenRepository).deleteAllByUserAndType(user, TokenType.PASSWORD_RESET);
            ArgumentCaptor<VerificationToken> captor = ArgumentCaptor.forClass(VerificationToken.class);
            verify(verificationTokenRepository).save(captor.capture());
            VerificationToken vt = captor.getValue();
            assertThat(vt.getType()).isEqualTo(TokenType.PASSWORD_RESET);
            assertThat(vt.isUsed()).isFalse();
            assertThat(vt.getExpiresAt()).isCloseTo(nowUtc().plusHours(1), within(Duration.ofSeconds(10)));
            verify(emailService).sendPasswordResetEmail(user, vt.getToken());
        }

        @Test
        void resetWithValidTokenChangesPasswordConsumesTokenAndNotifies() {
            VerificationToken vt = token(TokenType.PASSWORD_RESET, nowUtc().plusMinutes(30), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.PASSWORD_RESET))
                    .thenReturn(Optional.of(vt));
            when(passwordEncoder.encode("NewSecret1")).thenReturn("{bcrypt}new");

            authService.resetPassword(vt.getToken(), new ResetPasswordRequest(vt.getToken(), "NewSecret1", "NewSecret1"));

            assertThat(user.getPassword()).isEqualTo("{bcrypt}new");
            assertThat(vt.isUsed()).isTrue();
            verify(userRepository).save(user);
            verify(emailService).sendPasswordChangedNotification(user);
        }

        @Test
        void resetTokenCannotBeUsedTwice() {
            VerificationToken vt = token(TokenType.PASSWORD_RESET, nowUtc().plusMinutes(30), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.PASSWORD_RESET))
                    .thenReturn(Optional.of(vt));
            when(passwordEncoder.encode(any())).thenAnswer(inv -> "{bcrypt}" + inv.getArgument(0));

            authService.resetPassword(vt.getToken(), new ResetPasswordRequest(vt.getToken(), "NewSecret1", "NewSecret1"));

            assertThatThrownBy(() -> authService.resetPassword(vt.getToken(),
                    new ResetPasswordRequest(vt.getToken(), "Hijack123", "Hijack123")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already used");
            assertThat(user.getPassword()).isEqualTo("{bcrypt}NewSecret1");
            verify(emailService, times(1)).sendPasswordChangedNotification(user);
        }

        @Test
        void expiredResetTokenIsRejected() {
            VerificationToken vt = token(TokenType.PASSWORD_RESET, nowUtc().minusSeconds(1), false);
            when(verificationTokenRepository.findByTokenAndType(vt.getToken(), TokenType.PASSWORD_RESET))
                    .thenReturn(Optional.of(vt));

            assertThatThrownBy(() -> authService.resetPassword(vt.getToken(),
                    new ResetPasswordRequest(vt.getToken(), "NewSecret1", "NewSecret1")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("expired");
            assertThat(user.getPassword()).isEqualTo("{hashed}");
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        void emailVerificationTokenCannotBeUsedToResetPassword() {
            when(verificationTokenRepository.findByTokenAndType("verify-token", TokenType.PASSWORD_RESET))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("verify-token",
                    new ResetPasswordRequest("verify-token", "NewSecret1", "NewSecret1")))
                    .isInstanceOf(BusinessException.class);
            assertThat(user.getPassword()).isEqualTo("{hashed}");
        }

        @Test
        void mismatchedPasswordConfirmationIsRejectedBeforeTouchingTheToken() {
            assertThatThrownBy(() -> authService.resetPassword("t",
                    new ResetPasswordRequest("t", "NewSecret1", "NewSecret2")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("do not match");
            verifyNoInteractions(verificationTokenRepository, passwordEncoder);
        }
    }

    @Nested
    class CurrentUserAndLogout {

        @Test
        void currentUserIsResolvedFromSecurityContext() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            assertThat(authService.getCurrentUser()).isSameAs(user);
        }

        @Test
        void noAuthenticationMeansNoCurrentUser() {
            assertThatThrownBy(() -> authService.getCurrentUser()).isInstanceOf(BusinessException.class);
        }

        @Test
        void logoutRecordsActivityAndClearsContext() {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            authService.logout();

            assertThat(user.getLastActivity()).isCloseTo(nowUtc(), within(Duration.ofSeconds(10)));
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
