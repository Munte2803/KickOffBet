package com.munte.KickOffBet.exceptions;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException() {
        super("Insufficient funds!");
    }
}
