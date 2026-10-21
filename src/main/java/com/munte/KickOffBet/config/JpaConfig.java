package com.munte.KickOffBet.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
public class JpaConfig {

    // createdAt/updatedAt are compared with LocalDateTime.now(ZoneOffset.UTC) everywhere (AML windows, reports),
    // so they must be UTC too instead of the JVM default zone
    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(LocalDateTime.now(ZoneOffset.UTC));
    }
}
