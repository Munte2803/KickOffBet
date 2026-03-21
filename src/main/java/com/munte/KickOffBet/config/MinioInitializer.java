package com.munte.KickOffBet.config;

import com.munte.KickOffBet.services.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioInitializer implements ApplicationRunner {

    private final StorageService storageService;

    @Value("${minio.bucket.id-cards}")
    private String idCardsBucket;

    @Value("${minio.bucket.teams}")
    private String teamsBucket;

    @Value("${minio.bucket.leagues}")
    private String leaguesBucket;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        log.info("Initializing MinIO buckets...");
        storageService.createBucketIfNotExists(idCardsBucket);
        storageService.createBucketIfNotExists(teamsBucket);
        storageService.setBucketPublic(teamsBucket);
        storageService.createBucketIfNotExists(leaguesBucket);
        storageService.setBucketPublic(leaguesBucket);
        log.info("MinIO buckets initialized.");
    }
}
