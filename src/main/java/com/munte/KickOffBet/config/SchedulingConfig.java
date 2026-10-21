package com.munte.KickOffBet.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Database-backed locks (ShedLock, table {@code shedlock} created by schema.sql) so that every job runs on a
 * single instance at a time and never overlaps with itself.
 * <p>
 * A {@code @Scheduled} method that must not run concurrently (across instances or with a slow previous run) is
 * annotated with
 * <pre>{@code
 * @Scheduled(fixedDelay = ...)
 * @SchedulerLock(name = "unique-job-name", lockAtMostFor = "PT10M")
 * }</pre>
 * where {@code lockAtMostFor} is comfortably longer than the job normally takes (it only matters if the instance
 * dies while holding the lock). Code that needs the lock outside a scheduled method (e.g. the manual sync
 * endpoints, see SyncJobLauncher) uses the {@link LockProvider} bean directly.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        // Database time: lock expiry does not depend on the clocks of the instances agreeing
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    // Tests turn this off so the schedulers don't hit the Football Data API on startup
    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class EnabledScheduling {
    }
}
