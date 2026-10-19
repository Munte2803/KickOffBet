package com.munte.KickOffBet.controllers.admin;

import com.munte.KickOffBet.domain.dto.api.response.StoredFile;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin user management (/api/admin/users) and the user's own profile (/api/users/me), including KYC.
 */
class AdminUserIntegrationTest extends IntegrationTestBase {

    @Autowired UserRepository userRepository;
    @Autowired VerificationTokenRepository tokenRepository;
    @Autowired JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
    }

    private User pending(String email, boolean emailVerified) {
        User u = data.createUser(email, UserRole.USER, UserStatus.PENDING, BigDecimal.ZERO);
        u.setEmailVerified(emailVerified);
        return userRepository.save(u);
    }

    // ------------------------------------------------------------------ create admin

    @Test
    void createsAnotherAdmin() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("firstName", "Maria", "lastName", "Ionescu",
                "email", "boss@kickoffbet.test", "password", "Admin1234", "birthDate", "1985-03-03"));

        mvc.perform(auth(withJson(post("/api/admin/users/create-admin"), body), admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("boss@kickoffbet.test"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        User created = userRepository.findByEmail("boss@kickoffbet.test").orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.isIdCardVerified()).isTrue();
        mvc.perform(auth(get("/api/admin/users"), login("boss@kickoffbet.test", "Admin1234"))).andExpect(status().isOk());

        mvc.perform(auth(withJson(post("/api/admin/users/create-admin"), body), admin))
                .andExpect(status().isConflict());
        body.put("email", "x");
        mvc.perform(auth(withJson(post("/api/admin/users/create-admin"), body), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("email: Invalid email format"));
    }

    // ------------------------------------------------------------------ listing

    @Test
    void listsFiltersAndSearchesUsers() throws Exception {
        data.user("zed@kickoffbet.test");
        User first = pending("first-pending@kickoffbet.test", true);
        User second = pending("second-pending@kickoffbet.test", false);
        User verifiedPending = pending("kyc-done@kickoffbet.test", true);
        verifiedPending.setIdCardVerified(true);
        userRepository.save(verifiedPending);
        data.createUser("Suspended@Example.com", UserRole.USER, UserStatus.SUSPENDED, BigDecimal.ZERO);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now().minusDays(2)), first.getId());

        mvc.perform(auth(get("/api/admin/users").param("sort", "email,asc").param("size", "3"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].email").value("Suspended@Example.com"))
                .andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.content[0].firstName").value("Test"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());

        mvc.perform(auth(get("/api/admin/users/status/{status}", "PENDING"), admin))
                .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(auth(get("/api/admin/users/pending-verification"), admin))
                .andExpect(jsonPath("$.content[*].id").value(org.hamcrest.Matchers.contains(
                        first.getId().toString(), second.getId().toString())));

        mvc.perform(auth(get("/api/admin/users/search").param("email", "example.COM"), admin))
                .andExpect(jsonPath("$.content[*].email").value(org.hamcrest.Matchers.contains("Suspended@Example.com")));
        mvc.perform(auth(get("/api/admin/users/search").param("email", "pending"), admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(get("/api/admin/users/search"), admin)).andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/admin/users").param("sort", "password"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Sorting by 'password' is not allowed"));
        mvc.perform(auth(get("/api/admin/users/status/{status}", "NOPE"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userDetail() throws Exception {
        User user = data.userWithBalance("detail@kickoffbet.test", "42.50");
        mvc.perform(auth(get("/api/admin/users/{id}", user.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("detail@kickoffbet.test"))
                .andExpect(jsonPath("$.balance").value(42.5))
                .andExpect(jsonPath("$.birthDate").value("1990-01-01"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.idCardVerified").value(true))
                .andExpect(jsonPath("$.idCardUrl").value(user.getIdCardUrl()))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(auth(get("/api/admin/users/{id}", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    @Test
    void downloadsUserIdCardAsAttachment() throws Exception {
        User user = data.user("card@kickoffbet.test");
        String key = user.getIdCardUrl().substring(user.getIdCardUrl().lastIndexOf('/') + 1);
        when(storageService.downloadFile("id-cards", key))
                .thenReturn(new StoredFile("card.png", "image/png", new byte[]{9, 8, 7}));

        mvc.perform(auth(get("/api/admin/users/{id}/id-card", user.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("card.png")))
                .andExpect(content().bytes(new byte[]{9, 8, 7}));

        User noCard = data.user("nocard@kickoffbet.test");
        noCard.setIdCardUrl(null);
        userRepository.save(noCard);
        mvc.perform(auth(get("/api/admin/users/{id}/id-card", noCard.getId()), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No ID card found"));
    }

    // ------------------------------------------------------------------ KYC / status changes

    @Test
    void approvingKycActivatesOnlyEmailVerifiedUsers() throws Exception {
        User verified = pending("verified@kickoffbet.test", true);
        User unverified = pending("unverified@kickoffbet.test", false);

        mvc.perform(auth(patch("/api/admin/users/{id}/approve", verified.getId()), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(patch("/api/admin/users/{id}/approve", unverified.getId()), admin)).andExpect(status().isNoContent());

        assertThat(data.reloadUser(verified.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(data.reloadUser(verified.getId()).isIdCardVerified()).isTrue();
        assertThat(data.reloadUser(unverified.getId()).getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(data.reloadUser(unverified.getId()).isIdCardVerified()).isTrue();

        mvc.perform(auth(patch("/api/admin/users/{id}/approve", verified.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User is already active."));
        mvc.perform(auth(patch("/api/admin/users/{id}/approve", UUID.randomUUID()), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void userWithoutAnUploadedIdCardCannotBeApproved() throws Exception {
        User declined = pending("declined@kickoffbet.test", true);
        declined.setStatus(UserStatus.DECLINED);
        declined.setIdCardUrl(null);
        userRepository.save(declined);

        mvc.perform(auth(patch("/api/admin/users/{id}/approve", declined.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User has no ID card uploaded"));
        assertThat(data.reloadUser(declined.getId()).getStatus()).isEqualTo(UserStatus.DECLINED);
    }

    @Test
    void rejectingKycDeclinesUserAndDeletesCard() throws Exception {
        User user = pending("reject@kickoffbet.test", true);
        String cardUrl = user.getIdCardUrl();

        mvc.perform(auth(patch("/api/admin/users/{id}/reject", user.getId()), admin)).andExpect(status().isNoContent());

        User declined = data.reloadUser(user.getId());
        assertThat(declined.getStatus()).isEqualTo(UserStatus.DECLINED);
        assertThat(declined.getIdCardUrl()).isNull();
        verify(storageService).deleteFile("id-cards", cardUrl);

        mvc.perform(auth(patch("/api/admin/users/{id}/reject", user.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User is not pending"));
        User active = data.user("active-reject@kickoffbet.test");
        mvc.perform(auth(patch("/api/admin/users/{id}/reject", active.getId()), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendAndReactivate() throws Exception {
        User user = data.user("naughty@kickoffbet.test");
        String token = login(user);

        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", user.getId()), admin)).andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.SUSPENDED);
        mvc.perform(auth(get("/api/users/me"), token)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("ACCOUNT_LOCKED"));

        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", user.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User is already suspended"));

        mvc.perform(auth(patch("/api/admin/users/{id}/activate", user.getId()), admin)).andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
        mvc.perform(auth(get("/api/users/me"), login(user))).andExpect(status().isOk());

        mvc.perform(auth(patch("/api/admin/users/{id}/activate", user.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User is already active"));
    }

    @Test
    void adminsCannotBeSuspended() throws Exception {
        User otherAdmin = data.admin("other-admin@kickoffbet.test");
        mvc.perform(auth(patch("/api/admin/users/{id}/suspend", otherAdmin.getId()), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot suspend an admin user"));
    }

    @Test
    void newUsersTimeSeries() throws Exception {
        User old = data.user("old@kickoffbet.test");
        data.user("new@kickoffbet.test");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        jdbc.update("UPDATE users SET created_at = ? WHERE id = ?", Timestamp.valueOf(now.minusDays(5)), old.getId());
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        mvc.perform(auth(get("/api/admin/users/timeseries")
                        .param("start", now.minusDays(10).format(f))
                        .param("end", now.plusDays(1).format(f)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].date").value(now.minusDays(5).toLocalDate().toString()))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].totalAmount").doesNotExist())
                .andExpect(jsonPath("$[1].date").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[1].count").value(2)); // "new" user + the admin from setUp
    }

    // ------------------------------------------------------------------ profile (/api/users/me)

    @Test
    void myProfile() throws Exception {
        User user = data.userWithBalance("profile@kickoffbet.test", "12.34");
        mvc.perform(auth(get("/api/users/me"), login(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("profile@kickoffbet.test"))
                .andExpect(jsonPath("$.balance").value(12.34))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void myIdCardIsServedInline() throws Exception {
        User user = data.user("mycard@kickoffbet.test");
        when(storageService.downloadFile(eq("id-cards"), any()))
                .thenReturn(new StoredFile("id.pdf", null, new byte[]{1}));

        mvc.perform(auth(get("/api/users/me/id-card"), login(user)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void idCardReuploadAfterRejectionGoesBackToPendingReview() throws Exception {
        when(storageService.uploadFile(any(), eq("id-cards"))).thenReturn("http://minio.test/id-cards/new.png");
        User user = pending("reupload@kickoffbet.test", true);
        String token = login(user);
        mvc.perform(auth(patch("/api/admin/users/{id}/reject", user.getId()), admin)).andExpect(status().isNoContent());

        mvc.perform(auth(multipart("/api/users/me/id-card").file(filePart("file", "new.png")), token))
                .andExpect(status().isNoContent());
        User reloaded = data.reloadUser(user.getId());
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(reloaded.getIdCardUrl()).isEqualTo("http://minio.test/id-cards/new.png");

        mvc.perform(auth(patch("/api/admin/users/{id}/approve", user.getId()), admin)).andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
        // now verified: wallet operations open up and further uploads are refused
        mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of("amount", 25)), token))
                .andExpect(status().isCreated());
        mvc.perform(auth(multipart("/api/users/me/id-card").file(filePart("file", "again.png")), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ID card upload is not allowed for your account status"));
    }

    @Test
    void idCardUploadRequiresAFile() throws Exception {
        String token = login(pending("empty@kickoffbet.test", false));
        mvc.perform(auth(multipart("/api/users/me/id-card")
                        .file(new org.springframework.mock.web.MockMultipartFile("file", "e.png", "image/png", new byte[0])), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ID card file is required"));
    }

    @Test
    void deactivatingOwnAccountRevokesAccess() throws Exception {
        User user = data.user("leaving@kickoffbet.test");
        String token = login(user);

        mvc.perform(auth(delete("/api/users/me"), token)).andExpect(status().isNoContent());

        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        mvc.perform(auth(get("/api/users/me"), token)).andExpect(status().isForbidden());
        mvc.perform(withJson(post("/api/auth/login"), Map.of("email", user.getEmail(), "password", TestDataFactory.DEFAULT_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(errorCode("ACCOUNT_LOCKED"));
    }

    @Test
    void endToEndKycFromRegistration() throws Exception {
        when(storageService.uploadFile(any(), eq("id-cards"))).thenReturn("http://minio.test/id-cards/reg.png");
        Map<String, Object> reg = Map.of("firstName", "Ion", "lastName", "Popa", "email", "kyc@kickoffbet.test",
                "password", "Secret123", "birthDate", "1990-02-02");
        mvc.perform(multipart("/api/auth/register").file(jsonPart("data", reg)).file(filePart("idCard", "id.png")))
                .andExpect(status().isCreated());
        User user = userRepository.findByEmail("kyc@kickoffbet.test").orElseThrow();
        String token = login("kyc@kickoffbet.test", "Secret123");

        mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of("amount", 25)), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Verify your account!"));

        mvc.perform(auth(get("/api/admin/users/pending-verification"), admin))
                .andExpect(jsonPath("$.content[*].email").value(org.hamcrest.Matchers.contains("kyc@kickoffbet.test")));
        mvc.perform(auth(patch("/api/admin/users/{id}/approve", user.getId()), admin)).andExpect(status().isNoContent());
        assertThat(data.reloadUser(user.getId()).getStatus()).as("email not confirmed yet").isEqualTo(UserStatus.PENDING);

        String verification = data.inTx(() -> tokenRepository.findAll().stream()
                .filter(t -> t.getType() == TokenType.EMAIL_VERIFICATION)
                .map(VerificationToken::getToken).findFirst().orElseThrow());
        mvc.perform(get("/api/auth/confirm-email").param("token", verification)).andExpect(status().isNoContent());

        assertThat(data.reloadUser(user.getId()).getStatus()).isEqualTo(UserStatus.ACTIVE);
        mvc.perform(auth(withJson(post("/api/wallet/deposit"), Map.of("amount", 25)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
