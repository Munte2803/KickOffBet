package com.munte.KickOffBet.services.transactions;

import com.munte.KickOffBet.domain.entity.User;

import java.math.BigDecimal;

public interface AmlService {

    boolean susDeposit(User user, BigDecimal amount);

    boolean susWithdrawal(User user, BigDecimal amount);
}
