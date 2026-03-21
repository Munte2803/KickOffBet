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
import com.munte.KickOffBet.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final StorageService storageService;


    @Value("${minio.bucket.id-cards}")
    private String idCardsBucket;

    @Override
    public Page<User> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<User> getUsersByStatus(UserStatus status, Pageable pageable) {
        return userRepository.findAllByStatus(status, pageable);
    }

    @Override
    public Page<User> searchUsersByEmail(String email, Pageable pageable) {
        return userRepository.findAllByEmailContainingIgnoreCase(email,  pageable);
    }

    @Override
    public Page<User> getPendingVerification(Pageable pageable) {
        return userRepository.findAllByStatusAndIdCardVerifiedFalseOrderByCreatedAtAsc(UserStatus.PENDING,pageable);
    }

    @Override
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    @Transactional
    public void approveUser(UUID id) {
        User user = getUserById(id);

        if (user.getStatus().equals(UserStatus.ACTIVE)) {
            throw new BusinessException("User is already active.");
        }
        if (user.isEmailVerified()) {
            user.setStatus(UserStatus.ACTIVE);
        }

        user.setIdCardVerified(true);
        userRepository.save(user);
        log.info("User approved: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void rejectUser(UUID id) {
        User user = getUserById(id);
        if (!user.getStatus().equals(UserStatus.PENDING)|| user.isIdCardVerified()) {
            throw new BusinessException("User is not pending");
        }
        user.setStatus(UserStatus.DECLINED);
        storageService.deleteFile(idCardsBucket, user.getIdCardUrl());
        user.setIdCardUrl(null);
        userRepository.save(user);
        log.info("User rejected: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void suspendUser(UUID id) {
        User user = getUserById(id);
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("User is already suspended");
        }
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        log.info("User suspended: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void activateUser(UUID id) {
        User user = getUserById(id);
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessException("User is already active");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("User activated: {}", user.getEmail());
    }

    @Override
    public UserDto getMyProfile() {
        User user = authService.getCurrentUser();
        return userMapper.toDto(user);
    }


    @Override
    public byte[] getMyIdCard() {
        User user = authService.getCurrentUser();
        if (user.getIdCardUrl() == null) {
            throw new ResourceNotFoundException("No ID card found");
        }
        String filename = user.getIdCardUrl()
                .substring(user.getIdCardUrl().lastIndexOf("/") + 1);
        return storageService.downloadFile(idCardsBucket, filename);
    }

    @Override
    public byte[] getUserCard(UUID userId){
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getIdCardUrl() == null) {
            throw new ResourceNotFoundException("No ID card found");
        }
        String filename = user.getIdCardUrl().substring(user.getIdCardUrl().lastIndexOf("/") + 1);
        return storageService.downloadFile(idCardsBucket, filename);
    }

    @Override
    @Transactional
    public void uploadIdCard(MultipartFile idCard) {
        if (idCard == null || idCard.isEmpty()) {
            throw new BusinessException("ID card file is required");
        }

        User user = authService.getCurrentUser();

        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.DECLINED || user.isIdCardVerified()) {
            throw new BusinessException("ID card upload is not allowed for your account status");
        }

        if (user.getIdCardUrl() != null) {
            throw new BusinessException("ID card already submitted and pending review");
        }

        String idCardUrl = storageService.uploadFile(idCard, idCardsBucket);
        user.setIdCardUrl(idCardUrl);

        if (user.getStatus() == UserStatus.DECLINED) {
            user.setStatus(UserStatus.PENDING);
        }

        userRepository.save(user);
        log.info("ID card uploaded for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void deactivateMyAccount() {
       User user = authService.getCurrentUser();
       user.setStatus(UserStatus.DEACTIVATED);
       userRepository.save(user);

    }
}