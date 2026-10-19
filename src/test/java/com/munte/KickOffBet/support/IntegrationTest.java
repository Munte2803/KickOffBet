package com.munte.KickOffBet.support;

import com.munte.KickOffBet.client.FootballDataClient;
import com.munte.KickOffBet.services.users.EmailService;
import com.munte.KickOffBet.services.users.StorageService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Full Spring context on H2 with every external system (MinIO, SMTP, Football Data API) mocked.
 * Keep this the only variation used by test classes so they all share one cached context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDataFactory.class)
@MockitoBean(types = {StorageService.class, EmailService.class, FootballDataClient.class})
public @interface IntegrationTest {
}
