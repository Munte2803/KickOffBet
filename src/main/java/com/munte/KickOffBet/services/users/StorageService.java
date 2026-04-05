package com.munte.KickOffBet.services.users;

import com.munte.KickOffBet.domain.dto.api.response.StoredFile;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String uploadFile(MultipartFile file, String bucket);
    void deleteFile(String bucket, String fileUrl);
    void createBucketIfNotExists(String bucket);
    StoredFile downloadFile(String bucket, String key);

    void setBucketPublic(String bucket);
}
