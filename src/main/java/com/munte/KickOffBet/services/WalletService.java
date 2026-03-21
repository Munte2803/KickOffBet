package com.munte.KickOffBet.services;

import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.entity.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    Transaction deposit(DepositRequest request);

    Transaction withdraw(WithdrawRequest request);

    Transaction stake(BigDecimal amount, UUID ticketId);

    Transaction payout(UUID userId, BigDecimal amount, UUID ticketId);

    Transaction refund(UUID userId, BigDecimal amount);

    Transaction approveTransaction(UUID transactionId);

    Transaction rejectTransaction(UUID transactionId);

}
