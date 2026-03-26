package com.munte.KickOffBet.services.users;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String uploadFile(MultipartFile file, String bucket);
    void deleteFile(String bucket, String fileUrl);
    void createBucketIfNotExists(String bucket);
    byte[] downloadFile(String bucket, String key);

    void setBucketPublic(String bucket);
}