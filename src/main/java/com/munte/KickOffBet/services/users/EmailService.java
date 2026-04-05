package com.munte.KickOffBet.services.users;


import com.munte.KickOffBet.domain.entity.User;

import java.math.BigDecimal;

public interface EmailService {
    void sendVerificationEmail(User user, String token);
    void sendPasswordResetEmail(User user, String token);
    void sendDepositConfirmation(User user, BigDecimal amount);
    void sendWithdrawalConfirmation(User user, BigDecimal amount);

    void sendPasswordChangedNotification(User user);
}

