package com.munte.KickOffBet.services.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public void sendVerificationEmail(User user, String token) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("link", baseUrl + "/api/auth/confirm-email?token=" + token);
        sendEmail(user.getEmail(), "KickOffBet - Email Verification", "email/verification-email", context);
    }

    @Override
    public void sendPasswordResetEmail(User user, String token) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("link", baseUrl + "/api/auth/reset-password?token=" + token);
        sendEmail(user.getEmail(), "KickOffBet - Password Reset", "email/password-reset-email", context);
    }

    @Override
    public void sendDepositConfirmation(User user, BigDecimal amount) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("amount", amount);
        sendEmail(user.getEmail(), "KickOffBet - Deposit Confirmation", "email/deposit-confirmation", context);
    }

    @Override
    public void sendWithdrawalConfirmation(User user, BigDecimal amount) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("amount", amount);
        sendEmail(user.getEmail(), "KickOffBet - Withdrawal Confirmation", "email/withdrawal-confirmation", context);
    }

    @Async("threadPoolTaskExecutor")
    private void sendEmail(String to, String subject, String template, Context context) {
        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Override
    public void sendPasswordChangedNotification(User user) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        sendEmail(user.getEmail(), "KickOffBet - Password Changed",
                "email/password-changed-notification", context);
    }
}