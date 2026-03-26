package com.munte.KickOffBet.services.users;

import com.munte.KickOffBet.domain.dto.api.response.UserDto;
import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserService {

    Page<User> getAllUsers(Pageable pageable);

    Page<User> getUsersByStatus(UserStatus status, Pageable pageable);

    Page<User> searchUsersByEmail(String email, Pageable pageable);

    Page<User> getPendingVerification(Pageable pageable);

    User getUserById(UUID id);

    void approveUser(UUID id);

    void rejectUser(UUID id);

    void suspendUser(UUID id);

    void activateUser(UUID id);

    UserDto getMyProfile();

    byte[] getMyIdCard();

    byte[] getUserCard(UUID userId);

    void uploadIdCard(MultipartFile idCard);

    void deactivateMyAccount();
}
