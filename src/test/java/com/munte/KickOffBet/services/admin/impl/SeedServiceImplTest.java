package com.munte.KickOffBet.services.admin.impl;

import com.munte.KickOffBet.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SeedServiceImplTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private SeedServiceImpl seedService(String password, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        SeedServiceImpl service = new SeedServiceImpl(jdbc, mock(PasswordEncoder.class), environment);
        ReflectionTestUtils.setField(service, "seedUserPassword", password);
        return service;
    }

    @Test
    void seedIsRefusedInProductionAndWithoutAConfiguredPassword() {
        assertThatThrownBy(() -> seedService("Strong-Seed-1", "prod").runSeed())
                .isInstanceOf(BusinessException.class)
                .hasMessage("Seeding is disabled in production");
        assertThatThrownBy(() -> seedService("").runSeed())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SEED_USER_PASSWORD");
        verifyNoInteractions(jdbc);
    }
}
