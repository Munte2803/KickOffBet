package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.entity.User;
import com.munte.KickOffBet.services.users.EmailService;
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

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Async("threadPoolTaskExecutor")
    public void sendVerificationEmail(User user, String token) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("link", frontendBaseUrl() + "/confirm-email?token=" + token);
        sendEmail(user.getEmail(), "KickOffBet - Email Verification", "email/verification-email", context);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void sendPasswordResetEmail(User user, String token) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("link", frontendBaseUrl() + "/reset-password?token=" + token);
        sendEmail(user.getEmail(), "KickOffBet - Password Reset", "email/password-reset-email", context);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void sendDepositConfirmation(User user, BigDecimal amount) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("amount", amount);
        sendEmail(user.getEmail(), "KickOffBet - Deposit Confirmation", "email/deposit-confirmation", context);
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void sendWithdrawalConfirmation(User user, BigDecimal amount) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("amount", amount);
        sendEmail(user.getEmail(), "KickOffBet - Withdrawal Confirmation", "email/withdrawal-confirmation", context);
    }

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
            log.info("Email sent async to: {}", to);
        } catch (MessagingException e) {
            log.warn("Failed to send email async to {}: {}", to, e.getMessage());
        }
    }

    @Override
    @Async("threadPoolTaskExecutor")
    public void sendPasswordChangedNotification(User user) {
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("link", frontendBaseUrl() + "/login");
        sendEmail(user.getEmail(), "KickOffBet - Password Changed",
                "email/password-changed-notification", context);
    }

    private String frontendBaseUrl() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
