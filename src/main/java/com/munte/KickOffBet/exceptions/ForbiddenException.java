package com.munte.KickOffBet.exceptions;

/** The caller is authenticated but not allowed to do this (403). */
public class ForbiddenException extends KickOffBetException {
    public ForbiddenException(String message) {
        super(message);
    }
}
