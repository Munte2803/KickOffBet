package com.munte.KickOffBet.controllers;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.entity.VerificationToken;
import com.munte.KickOffBet.domain.enums.TokenType;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.repository.VerificationTokenRepository;
import com.munte.KickOffBet.support.IntegrationTestBase;
import com.munte.KickOffBet.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    private static final String ID_CARD_URL = "http://minio.test/id-cards/card.png";

    @Autowired UserRepository userRepository;
    @Autowired VerificationTokenRepository tokenRepository;

    @BeforeEach
    void storageReturnsUrl() {
        when(storageService.uploadFile(any(), eq("id-cards"))).thenReturn(ID_CARD_URL);
    }

    private Map<String, Object> registration(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Ana-Maria");
        body.put("lastName", "Popescu");
        body.put("email", email);
        body.put("password", "Secret123");
        body.put("birthDate", "1995-05-20");
        return body;
    }

    private ResultActions register(Map<String, Object> body, boolean withIdCard) throws Exception {
        var request = multipart("/api/auth/register").file(jsonPart("data", body));
        if (withIdCard) {
            request.file(filePart("idCard", "id.png"));
        }
        return mvc.perform(request);
    }

    private String tokenFor(String email, TokenType type) {
        return data.inTx(() -> tokenRepository.findAll().stream()
                .filter(t -> t.getType() == type && t.getUser().getEmail().equals(email) && !t.isUsed())
                .map(VerificationToken::getToken)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " token for " + email)));
    }

    // ------------------------------------------------------------------ full flow

    @Test
    void registerConfirmLoginRefreshForgotAndResetPassword() throws Exception {
        String email = "flow@kickoffbet.test";

        // register (multipart: JSON "data" part + "idCard" file, exactly like the UI)
        register(registration(email), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Ana-Maria"))
                .andExpect(jsonPath("$.lastName").value("Popescu"))
                .andExpect(jsonPath("$.role").value("USER"));

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(created.isEmailVerified()).isFalse();
        assertThat(created.isIdCardVerified()).isFalse();
        assertThat(created.getIdCardUrl()).isEqualTo(ID_CARD_URL);
        assertThat(created.getBalance()).isEqualByComparingTo("0");
        assertThat(created.getPassword()).isNotEqualTo("Secret123");

        ArgumentCaptor<String> mailedToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(any(User.class), mailedToken.capture());
        String verificationToken = tokenFor(email, TokenType.EMAIL_VERIFICATION);
        assertThat(mailedToken.getValue()).isEqualTo(verificationToken);

        // confirm email
        mvc.perform(get("/api/auth/confirm-email").param("token", verificationToken))
                .andExpect(status().isNoContent());
        User confirmed = data.reloadUser(created.getId());
        assertThat(confirmed.isEmailVerified()).isTrue();
        assertThat(confirmed.getStatus()).as("still waiting for KYC").isEqualTo(UserStatus.PENDING);

        mvc.perform(get("/api/auth/confirm-email").param("token", verificationToken))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("Token already used"));

        // login (PENDING users may log in)
        ResultActions login = mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("email", email, "password", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value(email));
        String access = read(login, "$.token");
        String refresh = read(login, "$.refreshToken");

        mvc.perform(auth(get("/api/users/me"), access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // refresh
        ResultActions refreshed = mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of("refreshToken", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));
        String rotatedRefresh = read(refreshed, "$.refreshToken");
        assertThat(rotatedRefresh).as("refresh rotates the refresh token").isNotEqualTo(refresh);
        mvc.perform(auth(get("/api/users/me"), read(refreshed, "$.token"))).andExpect(status().isOk());
        assertThat(data.reloadUser(created.getId()).getLastActivity()).isNotNull();

        // forgot + reset password
        mvc.perform(withJson(post("/api/auth/forgot-password"), Map.of("email", email)))
                .andExpect(status().isNoContent());
        String resetToken = tokenFor(email, TokenType.PASSWORD_RESET);
        verify(emailService).sendPasswordResetEmail(any(User.class), eq(resetToken));

        mvc.perform(withJson(post("/api/auth/reset-password"),
                        Map.of("token", resetToken, "password", "NewSecret456", "confirmPassword", "Different456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passwords do not match"));

        mvc.perform(withJson(post("/api/auth/reset-password"),
                        Map.of("token", resetToken, "password", "NewSecret456", "confirmPassword", "NewSecret456")))
                .andExpect(status().isNoContent());
        verify(emailService).sendPasswordChangedNotification(any(User.class));

        // the reset logs out every existing session
        mvc.perform(auth(get("/api/users/me"), access)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of("refreshToken", rotatedRefresh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));

        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", email, "password", "Secret123")))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("INVALID_CREDENTIALS"));
        login(email, "NewSecret456");

        mvc.perform(withJson(post("/api/auth/reset-password"),
                        Map.of("token", resetToken, "password", "Another789", "confirmPassword", "Another789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token already used"));
    }

    @Test
    void confirmingEmailActivatesUserWhoseIdCardIsAlreadyApproved() throws Exception {
        register(registration("kyc-first@kickoffbet.test"), true).andExpect(status().isCreated());
        User user = userRepository.findByEmail("kyc-first@kickoffbet.test").orElseThrow();

        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/{id}/approve", user.getId()), adminToken()))
                .andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.PENDING);

        mvc.perform(get("/api/auth/confirm-email").param("token", tokenFor(user.getEmail(), TokenType.EMAIL_VERIFICATION)))
                .andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void expiredVerificationTokenIsRejected() throws Exception {
        register(registration("late@kickoffbet.test"), true).andExpect(status().isCreated());
        String token = tokenFor("late@kickoffbet.test", TokenType.EMAIL_VERIFICATION);
        VerificationToken vt = tokenRepository.findByTokenAndType(token, TokenType.EMAIL_VERIFICATION).orElseThrow();
        vt.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        tokenRepository.save(vt);

        mvc.perform(get("/api/auth/confirm-email").param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token has expired"));
    }

    @Test
    void resendVerificationReplacesTokenAndIgnoresVerifiedOrUnknownEmails() throws Exception {
        register(registration("resend@kickoffbet.test"), true).andExpect(status().isCreated());
        String first = tokenFor("resend@kickoffbet.test", TokenType.EMAIL_VERIFICATION);

        mvc.perform(withJson(post("/api/auth/resend-verification"), Map.of("email", "resend@kickoffbet.test")))
                .andExpect(status().isNoContent());
        String second = tokenFor("resend@kickoffbet.test", TokenType.EMAIL_VERIFICATION);
        assertThat(second).isNotEqualTo(first);
        assertThat(tokenRepository.findByTokenAndType(first, TokenType.EMAIL_VERIFICATION)).isEmpty();

        mvc.perform(get("/api/auth/confirm-email").param("token", second)).andExpect(status().isNoContent());

        // Verified and unknown emails get the same answer as a real resend, without sending anything
        mvc.perform(withJson(post("/api/auth/resend-verification"), Map.of("email", "resend@kickoffbet.test")))
                .andExpect(status().isNoContent());
        mvc.perform(withJson(post("/api/auth/resend-verification"), Map.of("email", "ghost@kickoffbet.test")))
                .andExpect(status().isNoContent());
        verify(emailService, times(2)).sendVerificationEmail(any(), any());
    }

    // ------------------------------------------------------------------ registration failures

    @Test
    void registrationWithoutIdCardIsRejected() throws Exception {
        register(registration("noid@kickoffbet.test"), false)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("ID card is required for registration"));
        assertThat(userRepository.existsByEmail("noid@kickoffbet.test")).isFalse();
    }

    @Test
    void underageRegistrationIsRejected() throws Exception {
        Map<String, Object> body = registration("kid@kickoffbet.test");
        body.put("birthDate", LocalDate.now().minusYears(17).toString());
        register(body, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You must be at least 18 years old to register"));
        verify(storageService, never()).uploadFile(any(), anyString());
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        data.user("taken@kickoffbet.test");
        register(registration("taken@kickoffbet.test"), true)
                .andExpect(status().isConflict())
                .andExpect(errorCode("CONFLICT"))
                .andExpect(jsonPath("$.error").value("Email already in use"));
    }

    @Test
    void registrationValidationErrorsUseErrorDtoShape() throws Exception {
        Map<String, Object> weakPassword = registration("weak@kickoffbet.test");
        weakPassword.put("password", "password");
        register(weakPassword, true)
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("password: Password must contain uppercase, lowercase and a number"));

        Map<String, Object> badName = registration("name@kickoffbet.test");
        badName.put("firstName", "R2D2");
        register(badName, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("firstName: Name contains invalid characters"));

        Map<String, Object> badEmail = registration("not-an-email");
        register(badEmail, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("email: Invalid email format"));

        Map<String, Object> futureBirth = registration("future@kickoffbet.test");
        futureBirth.put("birthDate", LocalDate.now().plusDays(1).toString());
        register(futureBirth, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("birthDate: Birth date must be in the past"));

        assertThat(userRepository.count()).isZero();
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginValidationErrors() throws Exception {
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", "bad", "password", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").value("email: Invalid email format"));
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", "a@b.ro", "password", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("password: Password is required"));
    }

    @Test
    void wrongPasswordReturnsInvalidCredentialsAndCountsTheAttempt() throws Exception {
        User user = data.user("wrong@kickoffbet.test");
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.getEmail(), "password", "Nope12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("INVALID_CREDENTIALS"));
        assertThat(data.reloadUser(user.getId()).getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", "ghost@kickoffbet.test", "password", "Nope12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("INVALID_CREDENTIALS"));
    }

    @Test
    void forgotPasswordForAnUnknownEmailSucceedsWithoutSendingAnything() throws Exception {
        mvc.perform(withJson(post("/api/auth/forgot-password"), Map.of("email", "ghost@kickoffbet.test")))
                .andExpect(status().isNoContent());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void successfulLoginResetsFailedAttempts() throws Exception {
        User user = data.user("reset-counter@kickoffbet.test");
        for (int i = 0; i < 3; i++) {
            mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.getEmail(), "password", "Nope12345")))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(data.reloadUser(user.getId()).getFailedLoginAttempts()).isEqualTo(3);
        login(user);
        assertThat(data.reloadUser(user.getId()).getFailedLoginAttempts()).isZero();
    }

    @Test
    void accountIsLockedAfterTenFailedLogins() throws Exception {
        User user = data.user("bruteforce@kickoffbet.test");
        String existingSession = login(user);
        Map<String, String> wrong = Map.of("email", user.getEmail(), "password", "Wrong1234");

        // the lock is never announced: it would confirm that the account exists
        for (int i = 1; i <= 10; i++) {
            mvc.perform(withJson(post("/api/auth/login"), wrong))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorCode("INVALID_CREDENTIALS"));
        }

        User locked = data.reloadUser(user.getId());
        assertThat(locked.getLockedUntil()).isAfter(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(55));

        // even the right password is refused while locked, with the same answer
        mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("INVALID_CREDENTIALS"));

        // but the lock only guards password logins: the owner's open session keeps working
        mvc.perform(auth(get("/api/users/me"), existingSession)).andExpect(status().isOk());
    }

    @Test
    void suspendedAndDeactivatedUsersCannotLogIn() throws Exception {
        for (UserStatus status : List.of(UserStatus.SUSPENDED, UserStatus.DEACTIVATED)) {
            User user = data.createUser(status + "@kickoffbet.test", UserRole.USER, status, BigDecimal.ZERO);
            mvc.perform(withJson(post("/api/auth/login"),
                            Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorCode("ACCOUNT_LOCKED"));
        }
    }

    // ------------------------------------------------------------------ refresh / logout

    @Test
    void refreshTokenRequiresBodyAndAValidToken() throws Exception {
        mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("refreshToken: Refresh token is required"));

        mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of("refreshToken", "garbage")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void refreshIsRefusedForSuspendedUser() throws Exception {
        User user = data.user("refresh-suspended@kickoffbet.test");
        ResultActions login = mvc.perform(withJson(post("/api/auth/login"),
                Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)));
        String refresh = read(login, "$.refreshToken");

        User u = data.reloadUser(user.getId());
        u.setStatus(UserStatus.SUSPENDED);
        userRepository.save(u);

        mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of("refreshToken", refresh)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid refresh token"));
    }

    @Test
    void logoutWorksWithAndWithoutToken() throws Exception {
        User user = data.user("logout@kickoffbet.test");
        String token = login(user);
        mvc.perform(auth(post("/api/auth/logout"), token)).andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getLastActivity()).isNotNull();

        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void logoutEndsTheSessionsOnEveryDevice() throws Exception {
        User user = data.user("two-devices@kickoffbet.test");
        String phone = login(user);
        ResultActions laptop = mvc.perform(withJson(post("/api/auth/login"),
                        Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isOk());
        String laptopAccess = read(laptop, "$.token");
        String laptopRefresh = read(laptop, "$.refreshToken");

        mvc.perform(auth(post("/api/auth/logout"), phone)).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/users/me"), laptopAccess)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/auth/refresh-token"), Map.of("refreshToken", laptopRefresh)))
                .andExpect(status().isBadRequest());
        // signing in again works as usual
        mvc.perform(auth(get("/api/users/me"), login(user))).andExpect(status().isOk());
    }

    @Test
    void forgotPasswordValidatesEmail() throws Exception {
        mvc.perform(withJson(post("/api/auth/forgot-password"), Map.of("email", "nope")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"));
    }

    @Test
    void resetPasswordWithUnknownTokenIsRejected() throws Exception {
        mvc.perform(withJson(post("/api/auth/reset-password"),
                        Map.of("token", "unknown", "password", "Valid1234", "confirmPassword", "Valid1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid or expired token"));
        mvc.perform(withJson(post("/api/auth/reset-password"),
                        Map.of("token", "unknown", "password", "weak", "confirmPassword", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(errorCode("VALIDATION_ERROR"));
    }

    @Test
    void registeredUserReceivesTheUploadedIdCardBackFromProfile() throws Exception {
        MockMultipartFile card = filePart("idCard", "id.png");
        mvc.perform(multipart("/api/auth/register").file(jsonPart("data", registration("card@kickoffbet.test"))).file(card))
                .andExpect(status().isCreated());
        verify(storageService).uploadFile(any(), eq("id-cards"));
    }
}
