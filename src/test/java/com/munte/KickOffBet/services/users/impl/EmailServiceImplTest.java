package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.entity.User;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailServiceImpl emailService;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@kickoffbet.test");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://kickoffbet.test/");
        user = new User();
        user.setEmail("player@kickoffbet.test");
        user.setFirstName("Ion");
        lenient().when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        lenient().when(templateEngine.process(anyString(), any(IContext.class))).thenReturn("<p>rendered</p>");
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private Context renderedContext(String template) {
        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq(template), captor.capture());
        return captor.getValue();
    }

    private static String raw(MimeMessage message) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void verificationEmailLinksToConfirmPageWithToken() throws Exception {
        emailService.sendVerificationEmail(user, "tok-123");

        Context ctx = renderedContext("email/verification-email");
        assertThat(ctx.getVariable("firstName")).isEqualTo("Ion");
        assertThat(ctx.getVariable("link")).isEqualTo("https://kickoffbet.test/confirm-email?token=tok-123");

        MimeMessage message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("KickOffBet - Email Verification");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("player@kickoffbet.test");
        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@kickoffbet.test");
        assertThat(raw(message)).contains("<p>rendered</p>").containsIgnoringCase("text/html");
    }

    @Test
    void passwordResetEmailLinksToResetPageWithToken() throws Exception {
        emailService.sendPasswordResetEmail(user, "reset-9");

        Context ctx = renderedContext("email/password-reset-email");
        assertThat(ctx.getVariable("link")).isEqualTo("https://kickoffbet.test/reset-password?token=reset-9");
        assertThat(sentMessage().getSubject()).isEqualTo("KickOffBet - Password Reset");
    }

    @Test
    void frontendUrlWithoutTrailingSlashIsUsedAsIs() {
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");

        emailService.sendPasswordResetEmail(user, "abc");

        assertThat(renderedContext("email/password-reset-email").getVariable("link"))
                .isEqualTo("http://localhost:5173/reset-password?token=abc");
    }

    @Test
    void depositConfirmationCarriesTheExactAmount() throws Exception {
        emailService.sendDepositConfirmation(user, new BigDecimal("150.50"));

        Context ctx = renderedContext("email/deposit-confirmation");
        assertThat(ctx.getVariable("amount")).isEqualTo(new BigDecimal("150.50"));
        assertThat(sentMessage().getSubject()).isEqualTo("KickOffBet - Deposit Confirmation");
    }

    @Test
    void withdrawalConfirmationCarriesTheExactAmount() throws Exception {
        emailService.sendWithdrawalConfirmation(user, new BigDecimal("75.25"));

        Context ctx = renderedContext("email/withdrawal-confirmation");
        assertThat(ctx.getVariable("amount")).isEqualTo(new BigDecimal("75.25"));
        assertThat(sentMessage().getSubject()).isEqualTo("KickOffBet - Withdrawal Confirmation");
    }

    @Test
    void passwordChangedNotificationLinksToLogin() throws Exception {
        emailService.sendPasswordChangedNotification(user);

        Context ctx = renderedContext("email/password-changed-notification");
        assertThat(ctx.getVariable("link")).isEqualTo("https://kickoffbet.test/login");
        assertThat(sentMessage().getSubject()).isEqualTo("KickOffBet - Password Changed");
    }

    @Test
    void messagingFailureIsLoggedNotPropagated() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        ReflectionTestUtils.setField(emailService, "fromEmail", "not a valid <address");

        assertThatCode(() -> emailService.sendVerificationEmail(user, "t")).doesNotThrowAnyException();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
