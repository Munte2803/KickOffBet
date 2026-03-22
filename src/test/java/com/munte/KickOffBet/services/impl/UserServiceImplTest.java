package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserStatus;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.ResourceNotFoundException;
import com.munte.KickOffBet.mapper.UserMapper;
import com.munte.KickOffBet.repository.UserRepository;
import com.munte.KickOffBet.services.AuthService;
import com.munte.KickOffBet.services.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "idCardsBucket", "id-cards");
    }

    // ==================== getUserById ====================

    @Test
    void getUserById_found() {
        User user = createUser(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        User result = userService.getUserById(user.getId());

        assertThat(result).isEqualTo(user);
    }

    @Test
    void getUserById_notFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== approveUser ====================

    @Test
    void approveUser_success() {
        User user = createUser(UserStatus.PENDING);
        user.setEmailVerified(true);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.approveUser(user.getId());

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isIdCardVerified()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void approveUser_alreadyActive() {
        User user = createUser(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.approveUser(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already active");
    }

    // ==================== rejectUser ====================

    @Test
    void rejectUser_success() {
        User user = createUser(UserStatus.PENDING);
        user.setIdCardVerified(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.rejectUser(user.getId());

        assertThat(user.getStatus()).isEqualTo(UserStatus.DECLINED);
        assertThat(user.getIdCardUrl()).isNull();
        verify(storageService).deleteFile("id-cards", "http://minio/id-cards/card.jpg");
        verify(userRepository).save(user);
    }

    @Test
    void rejectUser_notPending() {
        User user = createUser(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.rejectUser(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not pending");
    }

    // ==================== suspendUser ====================

    @Test
    void suspendUser_success() {
        User user = createUser(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.suspendUser(user.getId());

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
    }

    @Test
    void suspendUser_alreadySuspended() {
        User user = createUser(UserStatus.SUSPENDED);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.suspendUser(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already suspended");
    }

    // ==================== activateUser ====================

    @Test
    void activateUser_success() {
        User user = createUser(UserStatus.SUSPENDED);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.activateUser(user.getId());

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
    }

    @Test
    void activateUser_alreadyActive() {
        User user = createUser(UserStatus.ACTIVE);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.activateUser(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already active");
    }

    // ==================== getMyProfile ====================

    @Test
    void getMyProfile_success() {
        User user = createUser(UserStatus.ACTIVE);
        UserDto dto = new UserDto();
        dto.setEmail(user.getEmail());

        when(authService.getCurrentUser()).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.getMyProfile();

        assertThat(result).isEqualTo(dto);
        verify(authService).getCurrentUser();
        verify(userMapper).toDto(user);
    }

    // ==================== uploadIdCard ====================

    @Test
    void uploadIdCard_success() {
        User user = createUser(UserStatus.PENDING);
        user.setIdCardUrl(null);
        user.setIdCardVerified(false);

        MultipartFile idCard = mock(MultipartFile.class);
        when(idCard.isEmpty()).thenReturn(false);
        when(authService.getCurrentUser()).thenReturn(user);
        when(storageService.uploadFile(idCard, "id-cards")).thenReturn("http://minio/id-cards/new-card.jpg");

        userService.uploadIdCard(idCard);

        assertThat(user.getIdCardUrl()).isEqualTo("http://minio/id-cards/new-card.jpg");
        verify(userRepository).save(user);
    }

    @Test
    void uploadIdCard_nullFile() {
        assertThatThrownBy(() -> userService.uploadIdCard(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("required");
    }

    @Test
    void uploadIdCard_wrongStatus() {
        User user = createUser(UserStatus.ACTIVE);
        user.setIdCardUrl(null);
        user.setIdCardVerified(false);

        MultipartFile idCard = mock(MultipartFile.class);
        when(idCard.isEmpty()).thenReturn(false);
        when(authService.getCurrentUser()).thenReturn(user);

        assertThatThrownBy(() -> userService.uploadIdCard(idCard))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    // ==================== deactivateMyAccount ====================

    @Test
    void deactivateMyAccount_success() {
        User user = createUser(UserStatus.ACTIVE);
        when(authService.getCurrentUser()).thenReturn(user);

        userService.deactivateMyAccount();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DEACTIVATED);
        verify(userRepository).save(user);
    }

    // ==================== helper ====================

    private User createUser(UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@test.com");
        user.setStatus(status);
        user.setIdCardVerified(false);
        user.setEmailVerified(false);
        user.setIdCardUrl("http://minio/id-cards/card.jpg");
        return user;
    }
}
