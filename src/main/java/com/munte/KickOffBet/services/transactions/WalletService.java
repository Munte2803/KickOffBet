package com.munte.KickOffBet.services.transactions;

import com.munte.KickOffBet.domain.dto.api.request.DepositRequest;
import com.munte.KickOffBet.domain.dto.api.request.WithdrawRequest;
import com.munte.KickOffBet.domain.entity.Transaction;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    Transaction deposit(DepositRequest request);

    Transaction withdraw(WithdrawRequest request);

    void stake(BigDecimal amount, UUID ticketId);

    void payout(UUID userId, BigDecimal amount, UUID ticketId);

    void refund(UUID userId, BigDecimal amount);

    Transaction approveTransaction(UUID transactionId);

    Transaction rejectTransaction(UUID transactionId);

}
