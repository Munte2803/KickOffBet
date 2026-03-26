package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.StorageException;
import com.munte.KickOffBet.services.users.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${minio.public-url}")
    private String publicUrl;

    private final Tika tika;

    @Override
    public String uploadFile(MultipartFile file, String bucket) {
        try {

            String detectedType = tika.detect(file.getBytes());
            if (!detectedType.startsWith("image/")) {
                throw new BusinessException("Only images are allowed");
            }

            if (file.getSize() > 5 * 1024 * 1024) { // 5MB
                throw new BusinessException("File size must be under 5MB");
            }

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(filename)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );
            return publicUrl + "/" + bucket + "/" + filename;
        } catch (IOException e) {
            throw new StorageException("Failed to upload file to bucket: " + bucket);
        }
    }

    @Override
    public void deleteFile(String bucket, String fileUrl) {
        try {
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(filename)
                            .build()
            );
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete file from bucket: " + bucket);
        }
    }

    @Override
    public void createBucketIfNotExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.debug("Bucket already exists: {}", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: {}", bucket);
        } catch (S3Exception e) {
            throw new StorageException("Failed to create bucket: " + bucket);
        }
    }

    @Override
    public byte[] downloadFile(String bucket, String filename) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(filename)
                            .build()
            );
            return object.asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("Failed to download file: " + filename);
        }
    }

    @Override
    public void setBucketPublic(String bucket) {
        String policy = """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                    }
                ]
            }
            """.formatted(bucket);

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy(policy)
                .build());
        log.info("Set public policy for bucket: {}", bucket);
    }
}