package com.munte.KickOffBet.exceptions;

/** The request has no authenticated user (401). */
public class UnauthorizedException extends KickOffBetException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
