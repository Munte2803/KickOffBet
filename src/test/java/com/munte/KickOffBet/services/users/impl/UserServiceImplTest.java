package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.dto.api.response.StoredFile;
import com.munte.KickOffBet.domain.dto.api.response.TimeSeriesPointDto;
import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserRole;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.UserMapper;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.users.AuthService;
import com.munte.KickOffBet.services.users.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String ID_CARD_URL = "http://localhost:9000/id-cards/3f2a_card.png";

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthService authService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "idCardsBucket", "id-cards");
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("player@kickoffbet.test");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING);
        user.setIdCardUrl(ID_CARD_URL);
    }

    private void userExists() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Nested
    class KycReview {

        @Test
        void approvingKycOfEmailVerifiedUserActivatesAccount() {
            user.setEmailVerified(true);
            userExists();

            userService.approveUser(user.getId());

            assertThat(user.isIdCardVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(userRepository).save(user);
        }

        @Test
        void approvingKycBeforeEmailConfirmationKeepsAccountPending() {
            user.setEmailVerified(false);
            userExists();

            userService.approveUser(user.getId());

            assertThat(user.isIdCardVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        }

        @Test
        void approvingAlreadyActiveUserFails() {
            user.setStatus(UserStatus.ACTIVE);
            userExists();

            assertThatThrownBy(() -> userService.approveUser(user.getId())).isInstanceOf(BusinessException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void approvingUnknownUserFails() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.approveUser(id)).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectingKycDeclinesUserAndDeletesTheDocument() {
            userExists();

            userService.rejectUser(user.getId());

            assertThat(user.getStatus()).isEqualTo(UserStatus.DECLINED);
            assertThat(user.getIdCardUrl()).isNull();
            verify(storageService).deleteFile("id-cards", ID_CARD_URL);
            verify(userRepository).save(user);
        }

        @Test
        void rejectingAlreadyVerifiedUserFails() {
            user.setIdCardVerified(true);
            userExists();

            assertThatThrownBy(() -> userService.rejectUser(user.getId())).isInstanceOf(BusinessException.class);
            verifyNoInteractions(storageService);
            assertThat(user.getIdCardUrl()).isEqualTo(ID_CARD_URL);
        }

        @Test
        void rejectingNonPendingUserFails() {
            user.setStatus(UserStatus.ACTIVE);
            userExists();

            assertThatThrownBy(() -> userService.rejectUser(user.getId())).isInstanceOf(BusinessException.class);
            verifyNoInteractions(storageService);
        }
    }

    @Nested
    class Suspension {

        @Test
        void suspendingActiveUser() {
            user.setStatus(UserStatus.ACTIVE);
            userExists();

            userService.suspendUser(user.getId());

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            verify(userRepository).save(user);
        }

        @Test
        void adminsCannotBeSuspended() {
            user.setRole(UserRole.ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            userExists();

            assertThatThrownBy(() -> userService.suspendUser(user.getId())).isInstanceOf(BusinessException.class);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void suspendingTwiceFails() {
            user.setStatus(UserStatus.SUSPENDED);
            userExists();

            assertThatThrownBy(() -> userService.suspendUser(user.getId())).isInstanceOf(BusinessException.class);
            verify(userRepository, never()).save(any());
        }

        @Test
        void reactivatingSuspendedUser() {
            user.setStatus(UserStatus.SUSPENDED);
            userExists();

            userService.activateUser(user.getId());

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verify(userRepository).save(user);
        }

        @Test
        void reactivatingActiveUserFails() {
            user.setStatus(UserStatus.ACTIVE);
            userExists();

            assertThatThrownBy(() -> userService.activateUser(user.getId())).isInstanceOf(BusinessException.class);
        }

        @Test
        void userCanDeactivateOwnAccount() {
            user.setStatus(UserStatus.ACTIVE);
            when(authService.getCurrentUser()).thenReturn(user);

            userService.deactivateMyAccount();

            assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
            verify(userRepository).save(user);
        }
    }

    @Nested
    class IdCardUpload {

        private final MockMultipartFile file = new MockMultipartFile("idCard", "card.png", "image/png", new byte[]{1});

        @Test
        void pendingUserCanUploadIdCard() {
            user.setIdCardUrl(null);
            when(authService.getCurrentUser()).thenReturn(user);
            when(storageService.uploadFile(file, "id-cards")).thenReturn("http://minio/id-cards/new.png");

            userService.uploadIdCard(file);

            assertThat(user.getIdCardUrl()).isEqualTo("http://minio/id-cards/new.png");
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            verify(userRepository).save(user);
        }

        @Test
        void declinedUserReuploadingGoesBackToPendingReview() {
            user.setStatus(UserStatus.DECLINED);
            user.setIdCardUrl(null);
            when(authService.getCurrentUser()).thenReturn(user);
            when(storageService.uploadFile(file, "id-cards")).thenReturn("http://minio/id-cards/new.png");

            userService.uploadIdCard(file);

            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(user.getIdCardUrl()).isEqualTo("http://minio/id-cards/new.png");
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"ACTIVE", "SUSPENDED", "DEACTIVATED"})
        void uploadIsNotAllowedForOtherStatuses(UserStatus status) {
            user.setStatus(status);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> userService.uploadIdCard(file)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(storageService);
        }

        @Test
        void uploadIsNotAllowedOnceKycIsVerified() {
            user.setIdCardVerified(true);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> userService.uploadIdCard(file)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(storageService);
        }

        @Test
        void emptyFileIsRejected() {
            assertThatThrownBy(() -> userService.uploadIdCard(new MockMultipartFile("idCard", new byte[0])))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> userService.uploadIdCard(null)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(storageService, authService);
        }
    }

    @Nested
    class Profile {

        @Test
        void myProfileIsMappedFromCurrentUser() {
            UserDto dto = mock(UserDto.class);
            when(authService.getCurrentUser()).thenReturn(user);
            when(userMapper.toDto(user)).thenReturn(dto);

            assertThat(userService.getMyProfile()).isSameAs(dto);
        }

        @Test
        void myIdCardIsStreamedFromStorageByObjectKey() {
            StoredFile stored = new StoredFile("card.png", "image/png", new byte[]{1});
            when(authService.getCurrentUser()).thenReturn(user);
            when(storageService.downloadFile("id-cards", "3f2a_card.png")).thenReturn(stored);

            assertThat(userService.getMyIdCard()).isSameAs(stored);
        }

        @Test
        void myIdCardMissingIsNotFound() {
            user.setIdCardUrl(null);
            when(authService.getCurrentUser()).thenReturn(user);

            assertThatThrownBy(() -> userService.getMyIdCard()).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void adminCanStreamAUsersIdCard() {
            StoredFile stored = new StoredFile("card.png", "image/png", new byte[]{1});
            userExists();
            when(storageService.downloadFile("id-cards", "3f2a_card.png")).thenReturn(stored);

            assertThat(userService.getUserCard(user.getId())).isSameAs(stored);
        }

        @Test
        void userWithoutIdCardHasNoCardToStream() {
            user.setIdCardUrl(null);
            userExists();

            assertThatThrownBy(() -> userService.getUserCard(user.getId())).isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void dailyNewUsersAreMappedToTimeSeries() {
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 1, 3, 0, 0);
            List<Object[]> rows = List.<Object[]>of(new Object[]{Date.valueOf("2026-01-01"), 7L});
            when(userRepository.aggregateDailyNewUsers(start, end)).thenReturn(rows);

            List<TimeSeriesPointDto> points = userService.getDailyNewUsersTimeSeries(start, end);

            assertThat(points).containsExactly(new TimeSeriesPointDto(LocalDate.of(2026, 1, 1), 7L));
        }
    }
}
