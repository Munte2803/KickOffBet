package com.munte.KickOffBet.config;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ValidationConfig {

    // Times in requests are UTC (like everything stored), so @Future / @Past must compare against UTC "now",
    // not the JVM's default zone
    @Bean
    public ValidationConfigurationCustomizer utcValidationClock() {
        return configuration -> configuration.clockProvider(Clock::systemUTC);
    }
}
